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

//! E2e: gRPC stream subscriptions through the v2 facade.

use std::time::Duration;

use eventmesh::{
    message::{EventMeshMessage, Message},
    subscription::Subscription,
};

use crate::harness::{
    ensure_topic, grpc_producer, unique_topic, wait_for_client_group, warm_topic, warm_topic_as,
};
use crate::require_runtime;

async fn receive(
    receiver: &mut tokio::sync::mpsc::UnboundedReceiver<EventMeshMessage>,
) -> EventMeshMessage {
    tokio::time::timeout(Duration::from_secs(10), receiver.recv())
        .await
        .expect("timed out waiting for delivery")
        .expect("handler channel closed")
}

#[tokio::test(flavor = "multi_thread")]
async fn subscribe_and_receive() {
    require_runtime!();
    let topic = unique_topic("sub-recv");
    ensure_topic(&topic).await;
    let (_consumer, mut receiver) = warm_topic(&topic).await;

    grpc_producer()
        .publish(Message::from(EventMeshMessage::new(
            &topic,
            "delivered-payload",
        )))
        .await
        .expect("publish");
    let received = receive(&mut receiver).await;
    assert_eq!(received.content.as_deref(), Some("delivered-payload"));
    assert_eq!(received.topic.as_deref(), Some(topic.as_str()));
}

#[tokio::test(flavor = "multi_thread")]
async fn subscribe_batch_receive() {
    require_runtime!();
    let topic = unique_topic("sub-batch");
    ensure_topic(&topic).await;
    let (_consumer, mut receiver) = warm_topic(&topic).await;

    let messages = (0..3)
        .map(|index| Message::from(EventMeshMessage::new(&topic, format!("m{index}"))))
        .collect();
    grpc_producer()
        .publish_batch(messages)
        .await
        .expect("batch publish");

    let mut contents = Vec::new();
    for _ in 0..3 {
        contents.push(receive(&mut receiver).await.content.unwrap_or_default());
    }
    contents.sort();
    assert_eq!(contents, ["m0", "m1", "m2"]);
}

#[tokio::test(flavor = "multi_thread")]
async fn unsubscribe_stops_delivery() {
    require_runtime!();
    let topic = unique_topic("sub-unsub");
    let consumer_group = unique_topic("consumer-group");
    ensure_topic(&topic).await;
    let (consumer, mut receiver) = warm_topic_as(&topic, consumer_group.clone()).await;
    let producer = grpc_producer();
    wait_for_client_group("grpc", &consumer_group, true).await;

    producer
        .publish(Message::from(EventMeshMessage::new(&topic, "before-unsub")))
        .await
        .expect("publish before unsubscribe");
    let _ = receive(&mut receiver).await;

    consumer
        .unsubscribe(Subscription::new(&topic))
        .await
        .expect("unsubscribe");
    wait_for_client_group("grpc", &consumer_group, false).await;

    producer
        .publish(Message::from(EventMeshMessage::new(&topic, "after-unsub")))
        .await
        .expect("publish after unsubscribe");
    assert!(
        matches!(
            tokio::time::timeout(Duration::from_secs(3), receiver.recv()).await,
            Err(_) | Ok(None)
        ),
        "delivery leaked after unsubscribe"
    );
}
