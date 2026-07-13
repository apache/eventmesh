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

//! E2e: verify that the gRPC stream consumer dispatches messages to the
//! listener **concurrently** rather than serially.
//!
//! A listener that sleeps `HANDLER_DELAY` per message is fed `N` messages.
//! Under serial processing the total wall time would be `N * HANDLER_DELAY`;
//! under bounded concurrency (default 64) it should be roughly
//! `HANDLER_DELAY + overhead`. We assert the total is well below the serial
//! lower bound to prove overlap.

use std::time::{Duration, Instant};

use tokio::sync::mpsc;
use tracing::debug;

use eventmesh::{
    grpc::GrpcStreamConsumer,
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    MessageListener,
};

use eventmesh::grpc::GrpcProducer;
use eventmesh::transport::Publisher;

use crate::harness::{
    consumer_config, ensure_topic, let_stream_settle, producer_config, unique_topic,
};
use crate::require_runtime;

/// Per-message artificial delay inside the listener.
const HANDLER_DELAY: Duration = Duration::from_millis(500);
/// Number of messages to publish.
const N: usize = 5;

/// A listener that records when each `handle` call starts, sleeps for
/// `HANDLER_DELAY`, then forwards the message into a channel.  The recorded
/// start instants let the test assert that calls overlapped in time.
struct SlowListener {
    tx: mpsc::UnboundedSender<Instant>,
}

impl MessageListener for SlowListener {
    type Message = EventMeshMessage;

    async fn handle(&self, _msg: Self::Message) -> Option<Self::Message> {
        let _ = self.tx.send(Instant::now());
        tokio::time::sleep(HANDLER_DELAY).await;
        None
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn concurrent_dispatch_overlaps_handlers() {
    require_runtime!();
    let topic = unique_topic("concurrent");
    ensure_topic(&topic).await;

    let (listener, mut rx) = {
        let (tx, rx) = mpsc::unbounded_channel();
        (SlowListener { tx }, rx)
    };

    let mut cfg = consumer_config();
    // Explicitly enable concurrency well above N.
    cfg.max_concurrent_handlers = 8;

    let consumer = GrpcStreamConsumer::subscribe_stream(
        cfg,
        listener,
        vec![SubscriptionItem::new(
            &topic,
            SubscriptionMode::CLUSTERING,
            SubscriptionType::ASYNC,
        )],
        None::<std::future::Ready<()>>,
    )
    .await
    .expect("subscribe_stream");
    let_stream_settle().await;

    let producer = GrpcProducer::connect(producer_config()).expect("connect producer");

    let wall_start = Instant::now();
    for i in 0..N {
        producer
            .publish(
                EventMeshMessage::builder()
                    .topic(&topic)
                    .content(format!("m{i}"))
                    .build(),
            )
            .await
            .expect("publish");
    }

    // Collect the start-instant of every handler invocation.
    let mut starts = Vec::with_capacity(N);
    for _ in 0..N {
        let start = tokio::time::timeout(Duration::from_secs(15), rx.recv())
            .await
            .expect("timed out waiting for handler to start")
            .expect("listener channel closed");
        starts.push(start);
    }
    let elapsed = wall_start.elapsed();

    debug!(?starts, ?elapsed, "concurrent dispatch results");

    // Serial processing would take at least N * HANDLER_DELAY.  Concurrent
    // processing should finish in roughly HANDLER_DELAY + publish overhead.
    let serial_lower_bound = HANDLER_DELAY * N as u32;
    assert!(
        elapsed < serial_lower_bound,
        "expected concurrent dispatch to finish in < {serial_lower_bound:?}, \
         but took {elapsed:?}; handlers may have run serially"
    );

    // Stronger: assert that at least two handler calls overlapped.  If all
    // calls were serial, the gap between consecutive start instants would be
    // >= HANDLER_DELAY.  Find the minimum gap and assert it is well below
    // HANDLER_DELAY, proving overlap.
    starts.sort();
    let min_gap = starts
        .windows(2)
        .map(|w| w[1] - w[0])
        .min()
        .expect("at least two start instants");
    assert!(
        min_gap < HANDLER_DELAY,
        "expected overlapping handler starts (gap < {HANDLER_DELAY:?}), \
         but min gap was {min_gap:?}; handlers ran serially"
    );

    drop(consumer);
}
