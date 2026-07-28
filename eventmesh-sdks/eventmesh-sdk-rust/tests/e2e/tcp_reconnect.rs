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

//! E2e: disconnect isolated TCP sessions through the Runtime admin API and
//! verify reconnect, subscription replay, and publish.

use std::time::{Duration, Instant};

use eventmesh::message::{EventMeshMessage, Message};

use crate::harness::{
    consumer_options, ensure_topic, let_tcp_subscription_settle, producer_options,
    reject_tcp_subsystem, serialize_tcp_e2e, tcp_client_with_system, unique_topic,
    CollectingListener,
};
use crate::require_runtime;

#[tokio::test(flavor = "multi_thread")]
async fn tcp_reconnect_replays_subscription_after_server_disconnect() {
    let _tcp_e2e_guard = serialize_tcp_e2e().await;
    require_runtime!();

    let topic = unique_topic("tcp-real-reconnect");
    let subsystem = unique_topic("tcp-reconnect-subsystem");
    ensure_topic(&topic).await;
    let client = tcp_client_with_system(&subsystem);
    let (listener, mut receiver) = CollectingListener::new();
    let consumer = client
        .consumer(consumer_options(), listener)
        .await
        .expect("open reconnect test consumer");
    consumer
        .subscribe(eventmesh::Subscription::new(&topic))
        .await
        .expect("subscribe reconnect test consumer");
    let producer = client
        .producer(producer_options())
        .await
        .expect("open reconnect test producer");
    let_tcp_subscription_settle().await;

    reject_tcp_subsystem(&subsystem).await;
    // Runtime sends SERVER_GOODBYE_REQUEST first and closes the session with a
    // 30-second safety timer after the client ACK. Wait past that existing
    // server behavior, then allow reconnect + subscription replay + broker
    // rebalance to settle.
    tokio::time::sleep(Duration::from_secs(32)).await;
    let_tcp_subscription_settle().await;

    let deadline = Instant::now() + Duration::from_secs(60);
    loop {
        match producer
            .publish(Message::from(
                EventMeshMessage::new(&topic, "after-real-reconnect").unwrap(),
            ))
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
    assert_eq!(delivered.content(), "after-real-reconnect");
    producer.shutdown().await;
    consumer.shutdown();
    consumer.join().await.expect("join TCP consumer");
}
