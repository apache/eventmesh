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

//! Destructive e2e: restart the real runtime and verify TCP replay + publish.

use std::time::{Duration, Instant};

use eventmesh::message::{EventMeshMessage, Message};

use crate::harness::{
    ensure_topic, let_tcp_subscription_settle, serialize_tcp_e2e, tcp_producer, tcp_warm_topic,
    unique_topic,
};
use crate::require_runtime;

/// This test restarts the compose-managed runtime, so it is intentionally
/// ignored in the normal parallel suite. Run it explicitly with:
/// `cargo test --features e2e --test e2e tcp_reconnect_replays_subscription_after_runtime_restart -- --ignored`
#[tokio::test(flavor = "multi_thread")]
#[ignore = "restarts the compose-managed EventMesh runtime"]
async fn tcp_reconnect_replays_subscription_after_runtime_restart() {
    let _tcp_e2e_guard = serialize_tcp_e2e().await;
    require_runtime!();
    assert!(
        crate::runtime::compose_runtime_started(),
        "the reconnect e2e requires the runtime started by this test harness"
    );

    let topic = unique_topic("tcp-real-reconnect");
    ensure_topic(&topic).await;
    let (consumer, mut receiver) = tcp_warm_topic(&topic).await;
    let producer = tcp_producer().await;

    assert!(
        crate::runtime::restart_compose_runtime(),
        "restart EventMesh runtime"
    );
    // The consumer replays subscriptions only once its TCP connection has
    // re-established; the broker also needs its normal route/rebalance period.
    let_tcp_subscription_settle().await;

    let deadline = Instant::now() + Duration::from_secs(60);
    loop {
        match producer
            .publish(Message::from(EventMeshMessage::new(
                &topic,
                "after-real-reconnect",
            )))
            .await
        {
            Ok(receipt) => {
                assert_eq!(receipt.code, 0);
                break;
            }
            Err(error) if Instant::now() < deadline => {
                tracing::debug!(%error, "TCP producer still reconnecting");
                tokio::time::sleep(Duration::from_secs(1)).await;
            }
            Err(error) => panic!("TCP producer did not recover after runtime restart: {error}"),
        }
    }
    let delivered = tokio::time::timeout(Duration::from_secs(35), receiver.recv())
        .await
        .expect("timed out waiting for replayed TCP subscription")
        .expect("TCP handler channel closed");
    assert_eq!(delivered.content.as_deref(), Some("after-real-reconnect"));
    producer.shutdown().await;
    consumer.shutdown().await;
}
