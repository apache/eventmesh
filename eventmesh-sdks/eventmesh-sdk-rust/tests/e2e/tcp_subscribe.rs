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

//! E2e: TCP subscriptions through the v2 facade.

use std::time::Duration;

use eventmesh::message::{EventMeshMessage, Message};

use crate::harness::{
    ensure_topic, serialize_tcp_e2e, tcp_producer, tcp_warm_topic, unique_topic,
    wait_for_tcp_topic_listener,
};
use crate::require_runtime;

async fn receive(
    receiver: &mut tokio::sync::mpsc::UnboundedReceiver<EventMeshMessage>,
) -> EventMeshMessage {
    tokio::time::timeout(Duration::from_secs(10), receiver.recv())
        .await
        .expect("timed out waiting for TCP delivery")
        .expect("handler channel closed")
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_subscribe_and_receive() {
    let _tcp_e2e_guard = serialize_tcp_e2e().await;
    require_runtime!();
    let topic = unique_topic("tcp-sub-recv");
    ensure_topic(&topic).await;
    let (consumer, mut receiver) = tcp_warm_topic(&topic).await;
    let producer = tcp_producer().await;
    wait_for_tcp_topic_listener(&topic, true).await;

    producer
        .publish(Message::from(
            EventMeshMessage::new(&topic, "delivered-via-tcp").unwrap(),
        ))
        .await
        .expect("TCP publish");
    let received = receive(&mut receiver).await;
    assert_eq!(received.content(), "delivered-via-tcp");
    assert_eq!(received.topic(), topic.as_str());

    producer.shutdown().await;
    consumer.shutdown();
    consumer.join().await.expect("join TCP consumer");
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_unsubscribe_stops_delivery() {
    let _tcp_e2e_guard = serialize_tcp_e2e().await;
    require_runtime!();
    let topic = unique_topic("tcp-sub-unsub");
    ensure_topic(&topic).await;
    let (consumer, mut receiver) = tcp_warm_topic(&topic).await;
    let producer = tcp_producer().await;
    wait_for_tcp_topic_listener(&topic, true).await;

    producer
        .publish(Message::from(
            EventMeshMessage::new(&topic, "before-unsub").unwrap(),
        ))
        .await
        .expect("publish before unsubscribe");
    let _ = receive(&mut receiver).await;
    consumer.unsubscribe_all().await.expect("TCP unsubscribe");
    wait_for_tcp_topic_listener(&topic, false).await;

    producer
        .publish(Message::from(
            EventMeshMessage::new(&topic, "after-unsub").unwrap(),
        ))
        .await
        .expect("TCP publish after unsubscribe");
    assert!(
        matches!(
            tokio::time::timeout(Duration::from_secs(3), receiver.recv()).await,
            Err(_) | Ok(None)
        ),
        "TCP delivery leaked after unsubscribe"
    );

    producer.shutdown().await;
    consumer.shutdown();
    consumer.join().await.expect("join TCP consumer");
}
