// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

//! E2e: bounded concurrent gRPC handler dispatch through the v2 facade.

use std::sync::{
    atomic::{AtomicUsize, Ordering},
    Arc,
};
use std::time::Duration;

use eventmesh::{
    config::GrpcConsumerOptions,
    message::{EventMeshMessage, Message},
    subscription::Subscription,
    MessageHandler, Result,
};
use tokio::sync::mpsc;

use crate::harness::{ensure_topic, grpc_client, grpc_producer, let_stream_settle, unique_topic};
use crate::require_runtime;

const HANDLER_DELAY: Duration = Duration::from_millis(500);
const COUNT: usize = 5;
const MAX_CONCURRENT: usize = 2;

struct SlowHandler {
    active: Arc<AtomicUsize>,
    max_active: Arc<AtomicUsize>,
    completed: mpsc::UnboundedSender<()>,
}

impl MessageHandler for SlowHandler {
    async fn handle(&self, _message: Message) -> Result<Option<Message>> {
        let active = self.active.fetch_add(1, Ordering::SeqCst) + 1;
        self.max_active.fetch_max(active, Ordering::SeqCst);
        tokio::time::sleep(HANDLER_DELAY).await;
        self.active.fetch_sub(1, Ordering::SeqCst);
        let _ = self.completed.send(());
        Ok(None)
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn concurrent_dispatch_overlaps_handlers() {
    require_runtime!();
    let topic = unique_topic("concurrent");
    ensure_topic(&topic).await;
    let active = Arc::new(AtomicUsize::new(0));
    let max_active = Arc::new(AtomicUsize::new(0));
    let (completed, mut completions) = mpsc::unbounded_channel();
    let consumer = grpc_client()
        .stream_consumer(
            GrpcConsumerOptions::new(unique_topic("concurrent-group"))
                .with_max_concurrent_handlers(MAX_CONCURRENT),
            [Subscription::new(&topic)],
            SlowHandler {
                active: Arc::clone(&active),
                max_active: Arc::clone(&max_active),
                completed,
            },
        )
        .await
        .expect("open gRPC consumer");
    let_stream_settle().await;

    let producer = grpc_producer();
    for index in 0..COUNT {
        producer
            .publish(Message::from(
                EventMeshMessage::new(&topic, format!("m{index}")).unwrap(),
            ))
            .await
            .expect("publish");
    }

    for _ in 0..COUNT {
        tokio::time::timeout(Duration::from_secs(15), completions.recv())
            .await
            .expect("timed out waiting for handler completion")
            .expect("handler completion channel closed");
    }
    let observed = max_active.load(Ordering::SeqCst);
    assert!(
        observed > 1,
        "expected overlapping handlers, observed maximum was {observed}"
    );
    assert!(
        observed <= MAX_CONCURRENT,
        "handler limit was {MAX_CONCURRENT}, observed {observed}"
    );
    consumer.shutdown();
    consumer.join().await.expect("join gRPC consumer");
}
