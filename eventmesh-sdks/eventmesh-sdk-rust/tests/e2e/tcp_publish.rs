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

//! E2e: TCP producer operations through the v2 facade.

use eventmesh::{
    message::{EventMeshMessage, Message},
    subscription::{DeliveryMode, Subscription},
};

use crate::harness::{
    consumer_options, ensure_topic, let_tcp_subscription_settle, tcp_client, tcp_producer,
    tcp_warm_topic, unique_topic, CollectingListener,
};
use crate::require_runtime;
use std::time::Duration;

async fn receive(
    receiver: &mut tokio::sync::mpsc::UnboundedReceiver<EventMeshMessage>,
) -> EventMeshMessage {
    tokio::time::timeout(Duration::from_secs(10), receiver.recv())
        .await
        .expect("timed out waiting for TCP delivery")
        .expect("handler channel closed")
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_publish_single() {
    require_runtime!();
    let topic = unique_topic("tcp-pub-single");
    ensure_topic(&topic).await;
    let (_consumer, mut receiver) = tcp_warm_topic(&topic).await;

    let producer = tcp_producer().await;
    let receipt = producer
        .publish(Message::from(EventMeshMessage::new(
            &topic,
            "hello from rust TCP e2e",
        )))
        .await
        .expect("TCP publish");
    assert_eq!(receipt.code, 0, "TCP publish should succeed: {receipt:?}");
    assert_eq!(
        receive(&mut receiver).await.content.as_deref(),
        Some("hello from rust TCP e2e")
    );
    producer.shutdown().await;
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_broadcast() {
    require_runtime!();
    let topic = unique_topic("tcp-broadcast");
    ensure_topic(&topic).await;
    let producer = tcp_producer().await;
    let receipt = producer
        .publish(Message::from(EventMeshMessage::new(
            &topic,
            "warm TCP broadcast topic",
        )))
        .await
        .expect("warm TCP broadcast topic");
    assert_eq!(receipt.code, 0, "warm publish should succeed: {receipt:?}");

    let (listener, mut receiver) = CollectingListener::new();
    let consumer = tcp_client()
        .consumer(consumer_options(), listener)
        .await
        .expect("open TCP broadcast consumer");
    consumer
        .subscribe(Subscription::new(&topic).with_delivery_mode(DeliveryMode::Broadcast))
        .await
        .expect("subscribe TCP broadcast consumer");
    let_tcp_subscription_settle().await;

    producer
        .broadcast(Message::from(EventMeshMessage::new(
            &topic,
            "broadcast from rust TCP e2e",
        )))
        .await
        .expect("TCP broadcast");
    assert_eq!(
        tokio::time::timeout(Duration::from_secs(35), receiver.recv())
            .await
            .expect("timed out waiting for TCP broadcast delivery")
            .expect("broadcast handler channel closed")
            .content
            .as_deref(),
        Some("broadcast from rust TCP e2e")
    );
    producer.shutdown().await;
    consumer.shutdown().await;
}
