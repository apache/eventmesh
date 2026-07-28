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

//! E2e: HTTP webhook subscriptions through the v2 facade.

use std::time::Duration;

use eventmesh::{
    message::{EventMeshMessage, Message},
    subscription::Subscription,
};

use crate::harness::{
    ensure_topic, http_producer, http_warm_topic, http_warm_topic_as, unique_topic,
    wait_for_client_group,
};
use crate::require_runtime;

async fn receive(
    receiver: &mut tokio::sync::mpsc::UnboundedReceiver<EventMeshMessage>,
) -> EventMeshMessage {
    tokio::time::timeout(Duration::from_secs(15), receiver.recv())
        .await
        .expect("timed out waiting for webhook delivery")
        .expect("handler channel closed")
}

#[tokio::test(flavor = "multi_thread")]
async fn http_subscribe_and_receive() {
    require_runtime!();
    let topic = unique_topic("http-sub-recv");
    ensure_topic(&topic).await;
    let (_handle, mut receiver) = http_warm_topic(&topic).await;

    http_producer()
        .publish(Message::from(
            EventMeshMessage::new(&topic, "delivered-via-http").unwrap(),
        ))
        .await
        .expect("HTTP publish");
    assert_eq!(receive(&mut receiver).await.content(), "delivered-via-http");
}

#[tokio::test(flavor = "multi_thread")]
async fn http_unsubscribe_stops_delivery() {
    require_runtime!();
    let topic = unique_topic("http-sub-unsub");
    let consumer_group = unique_topic("consumer-group");
    ensure_topic(&topic).await;
    let (consumer, mut receiver) = http_warm_topic_as(&topic, consumer_group.clone()).await;
    let producer = http_producer();
    wait_for_client_group("http", &consumer_group, true).await;

    producer
        .publish(Message::from(
            EventMeshMessage::new(&topic, "before-http-unsub").unwrap(),
        ))
        .await
        .expect("HTTP publish before unsubscribe");
    let _ = receive(&mut receiver).await;

    consumer
        .unsubscribe(Subscription::new(&topic))
        .await
        .expect("HTTP unsubscribe");
    wait_for_client_group("http", &consumer_group, false).await;

    producer
        .publish(Message::from(
            EventMeshMessage::new(&topic, "after-http-unsub").unwrap(),
        ))
        .await
        .expect("HTTP publish after unsubscribe");
    assert!(
        matches!(
            tokio::time::timeout(Duration::from_secs(3), receiver.recv()).await,
            Err(_) | Ok(None)
        ),
        "webhook delivery leaked after unsubscribe"
    );
}
