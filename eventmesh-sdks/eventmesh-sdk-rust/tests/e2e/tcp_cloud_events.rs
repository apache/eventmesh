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

//! E2e: TCP CloudEvents — publish a CloudEvent and verify it is received by a
//! TCP consumer (converted to EventMeshMessage at the boundary).

use std::time::Duration;

use cloudevents::{EventBuilder, EventBuilderV10};
use eventmesh::{
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    tcp::{TcpConsumer, TcpProducer},
    transport::Subscriber,
};

use crate::harness::{
    ensure_topic, let_stream_settle, tcp_consumer_config, tcp_producer_config, unique_topic,
    CollectingListener,
};
use crate::runtime::ensure_runtime;

/// Helper: receive one message from `rx` or panic after `timeout`.
async fn recv_one(
    rx: &mut tokio::sync::mpsc::UnboundedReceiver<EventMeshMessage>,
    timeout: Duration,
) -> EventMeshMessage {
    tokio::time::timeout(timeout, rx.recv())
        .await
        .expect("timed out waiting for a delivered message")
        .expect("listener channel closed unexpectedly")
}

#[tokio::test]
async fn tcp_publish_cloud_event() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("tcp-ce-pub");
    ensure_topic(&topic).await;

    // Subscribe a collecting TCP consumer.
    let (listener, mut rx) = CollectingListener::new();
    let consumer = TcpConsumer::connect(tcp_consumer_config(), listener)
        .await
        .expect("connect consumer");
    consumer
        .subscribe(vec![SubscriptionItem::new(
            &topic,
            SubscriptionMode::CLUSTERING,
            SubscriptionType::ASYNC,
        )])
        .await
        .expect("subscribe");
    let_stream_settle().await;

    // Publish a CloudEvent over TCP.
    let producer = TcpProducer::connect(tcp_producer_config())
        .await
        .expect("connect producer");

    let payload = r#"{"msg":"hello from rust tcp cloudevents e2e"}"#;
    let event = EventBuilderV10::new()
        .id("tcp-ce-e2e-1")
        .source("https://eventmesh.apache.org/rust-sdk")
        .ty("com.example.someevent")
        .subject(&topic)
        .data("application/json", payload)
        .build()
        .expect("valid CloudEvent");

    let resp = producer.publish_cloud_event(event).await;
    producer.shutdown().await;
    let resp = resp.expect("publish_cloud_event should succeed");
    assert!(resp.is_success(), "publish should succeed: {resp}");

    // The consumer receives the CloudEvent converted to EventMeshMessage.
    let received = recv_one(&mut rx, Duration::from_secs(10)).await;
    assert_eq!(received.topic.as_deref(), Some(topic.as_str()));
    // Content should contain the JSON payload.
    assert!(
        received
            .content
            .as_deref()
            .map(|c| c.contains("hello from rust tcp cloudevents e2e"))
            .unwrap_or(false),
        "content should contain the CloudEvent data, got: {:?}",
        received.content
    );

    consumer.shutdown().await;
}

#[tokio::test]
async fn tcp_broadcast_cloud_event() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("tcp-ce-broadcast");
    ensure_topic(&topic).await;

    let (listener, mut rx) = CollectingListener::new();
    let consumer = TcpConsumer::connect(tcp_consumer_config(), listener)
        .await
        .expect("connect consumer");
    consumer
        .subscribe(vec![SubscriptionItem::new(
            &topic,
            SubscriptionMode::BROADCASTING,
            SubscriptionType::ASYNC,
        )])
        .await
        .expect("subscribe");
    let_stream_settle().await;

    let producer = TcpProducer::connect(tcp_producer_config())
        .await
        .expect("connect producer");

    let event = EventBuilderV10::new()
        .id("tcp-ce-broadcast-1")
        .source("https://eventmesh.apache.org/rust-sdk")
        .ty("com.example.someevent")
        .subject(&topic)
        .data("text/plain", "broadcast cloudevents payload")
        .build()
        .expect("valid CloudEvent");

    producer
        .broadcast_cloud_event(event)
        .await
        .expect("broadcast");

    let received = recv_one(&mut rx, Duration::from_secs(10)).await;
    assert_eq!(received.topic.as_deref(), Some(topic.as_str()));
    assert!(
        received
            .content
            .as_deref()
            .map(|c| c.contains("broadcast cloudevents payload"))
            .unwrap_or(false),
        "content should contain the CloudEvent data, got: {:?}",
        received.content
    );

    producer.shutdown().await;
    consumer.shutdown().await;
}
