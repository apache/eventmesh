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

//! E2e: HTTP webhook subscription — subscribe via webhook, receive pushed
//! messages, then unsubscribe and confirm delivery stops.

use std::time::Duration;

use eventmesh::{
    http::HttpProducer,
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    transport::Publisher,
};

use crate::harness::{
    ensure_topic, http_producer_config, http_warm_topic, let_stream_settle, unique_topic,
};
use crate::runtime::ensure_runtime;

/// The HTTP transport delivers messages via an HTTP POST callback from the
/// runtime to the consumer's webhook URL, which adds a network hop compared to
/// the gRPC bidirectional stream. Allow a generous timeout for delivery.
const DELIVERY_TIMEOUT: Duration = Duration::from_secs(15);

/// Helper: receive one message from `rx` or panic after `DELIVERY_TIMEOUT`.
async fn recv_one(
    rx: &mut tokio::sync::mpsc::UnboundedReceiver<EventMeshMessage>,
) -> EventMeshMessage {
    tokio::time::timeout(DELIVERY_TIMEOUT, rx.recv())
        .await
        .expect("timed out waiting for a pushed message")
        .expect("listener channel closed unexpectedly")
}

#[tokio::test]
async fn http_subscribe_and_receive() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("http-sub-recv");
    ensure_topic(&topic).await;
    let (handle, mut rx) = http_warm_topic(&topic).await;

    let producer = HttpProducer::new(http_producer_config()).expect("build http producer");
    let payload = "delivered-via-http";
    let msg = EventMeshMessage::builder()
        .topic(&topic)
        .content(payload)
        .build();
    producer.publish(msg).await.expect("http publish");

    let received = recv_one(&mut rx).await;
    assert_eq!(
        received.content.as_deref(),
        Some(payload),
        "delivered content mismatch: {received}"
    );

    drop(handle);
}

#[tokio::test]
async fn http_subscribe_batch_receive() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("http-sub-batch");
    ensure_topic(&topic).await;
    let (handle, mut rx) = http_warm_topic(&topic).await;

    let producer = HttpProducer::new(http_producer_config()).expect("build http producer");
    // HTTP batch publish is unsupported, so publish individually.
    for i in 0..3 {
        let msg = EventMeshMessage::builder()
            .topic(&topic)
            .content(format!("m{i}"))
            .build();
        producer.publish(msg).await.expect("http publish");
    }

    // Expect all three to arrive (order not guaranteed).
    let mut contents = Vec::new();
    for _ in 0..3 {
        let msg = recv_one(&mut rx).await;
        contents.push(msg.content.unwrap_or_default());
    }
    contents.sort();
    assert_eq!(contents, vec!["m0", "m1", "m2"]);

    drop(handle);
}

#[tokio::test]
async fn http_unsubscribe_stops_delivery() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("http-sub-unsub");
    ensure_topic(&topic).await;
    let (handle, mut rx) = http_warm_topic(&topic).await;

    let producer = HttpProducer::new(http_producer_config()).expect("build http producer");

    // First message should arrive.
    producer
        .publish(
            EventMeshMessage::builder()
                .topic(&topic)
                .content("before-http-unsub")
                .build(),
        )
        .await
        .expect("http publish before unsub");
    let _ = recv_one(&mut rx).await;

    // Unsubscribe, then publish again.
    handle
        .consumer()
        .unsubscribe(vec![SubscriptionItem::new(
            &topic,
            SubscriptionMode::CLUSTERING,
            SubscriptionType::ASYNC,
        )])
        .await
        .expect("http unsubscribe");
    let_stream_settle().await;

    match producer
        .publish(
            EventMeshMessage::builder()
                .topic(&topic)
                .content("after-http-unsub")
                .build(),
        )
        .await
    {
        Ok(_) => {
            let leaked = tokio::time::timeout(Duration::from_secs(3), rx.recv()).await;
            assert!(
                leaked.is_err(),
                "no message expected after unsubscribe, but got: {:?}",
                leaked.ok().flatten()
            );
        }
        Err(e) => {
            // Standalone rejects publish to a topic with no subscribers — the
            // unsubscribe worked.
            eprintln!(
                "[e2e] publish after http unsub rejected by broker (expected on standalone): {e}"
            );
        }
    }

    drop(handle);
}
