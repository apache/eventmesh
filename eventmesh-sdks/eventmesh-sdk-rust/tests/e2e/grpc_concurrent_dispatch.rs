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

use std::time::{Duration, Instant};

use eventmesh::{
    config::ConsumerOptions,
    message::{EventMeshMessage, Message},
    subscription::Subscription,
    MessageHandler, Result,
};
use tokio::sync::mpsc;

use crate::harness::{ensure_topic, grpc_client, grpc_producer, let_stream_settle, unique_topic};
use crate::require_runtime;

const HANDLER_DELAY: Duration = Duration::from_millis(500);
const COUNT: usize = 5;

struct SlowHandler {
    tx: mpsc::UnboundedSender<Instant>,
}

impl MessageHandler for SlowHandler {
    async fn handle(&self, _message: Message) -> Result<Option<Message>> {
        let _ = self.tx.send(Instant::now());
        tokio::time::sleep(HANDLER_DELAY).await;
        Ok(None)
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn concurrent_dispatch_overlaps_handlers() {
    require_runtime!();
    let topic = unique_topic("concurrent");
    ensure_topic(&topic).await;
    let (tx, mut receiver) = mpsc::unbounded_channel();
    let consumer = grpc_client()
        .stream_consumer(
            ConsumerOptions::new(unique_topic("concurrent-group")).with_concurrency(COUNT),
            [Subscription::new(&topic)],
            SlowHandler { tx },
        )
        .await
        .expect("open gRPC consumer");
    let_stream_settle().await;

    let started = Instant::now();
    let producer = grpc_producer();
    for index in 0..COUNT {
        producer
            .publish(Message::from(EventMeshMessage::new(
                &topic,
                format!("m{index}"),
            )))
            .await
            .expect("publish");
    }

    let mut starts = Vec::with_capacity(COUNT);
    for _ in 0..COUNT {
        starts.push(
            tokio::time::timeout(Duration::from_secs(15), receiver.recv())
                .await
                .expect("timed out waiting for handler")
                .expect("handler channel closed"),
        );
    }
    assert!(
        started.elapsed() < HANDLER_DELAY * COUNT as u32,
        "handlers should overlap"
    );
    starts.sort();
    let minimum_gap = starts
        .windows(2)
        .map(|window| window[1] - window[0])
        .min()
        .unwrap();
    assert!(minimum_gap < HANDLER_DELAY, "handler starts should overlap");
    consumer.shutdown().await;
}
