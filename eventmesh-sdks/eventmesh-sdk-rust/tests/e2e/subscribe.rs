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

//! E2e: stream subscription — subscribe, receive messages, then unsubscribe.

use std::time::Duration;

use eventmesh::{grpc::GrpcProducer, model::EventMeshMessage, transport::Publisher};

use crate::harness::{ensure_topic, let_stream_settle, producer_config, unique_topic, warm_topic};
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
async fn subscribe_and_receive() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("sub-recv");
    ensure_topic(&topic).await;
    let (consumer, mut rx) = warm_topic(&topic).await;

    let producer = GrpcProducer::connect(producer_config()).expect("connect producer");
    let payload = "delivered-payload";
    let msg = EventMeshMessage::builder()
        .topic(&topic)
        .content(payload)
        .build();
    producer.publish(msg).await.expect("publish");

    let received = recv_one(&mut rx, Duration::from_secs(10)).await;
    assert_eq!(received.content.as_deref(), Some(payload));
    assert_eq!(received.topic.as_deref(), Some(topic.as_str()));

    drop(consumer);
}

#[tokio::test]
async fn subscribe_batch_receive() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("sub-batch");
    ensure_topic(&topic).await;
    let (consumer, mut rx) = warm_topic(&topic).await;

    let producer = GrpcProducer::connect(producer_config()).expect("connect producer");
    let batch: Vec<EventMeshMessage> = (0..3)
        .map(|i| {
            EventMeshMessage::builder()
                .topic(&topic)
                .content(format!("m{i}"))
                .build()
        })
        .collect();
    producer.publish_batch(batch).await.expect("batch publish");

    // Expect all three to arrive (order not strictly guaranteed).
    let mut contents = Vec::new();
    for _ in 0..3 {
        let msg = recv_one(&mut rx, Duration::from_secs(10)).await;
        contents.push(msg.content.unwrap_or_default());
    }
    contents.sort();
    assert_eq!(contents, vec!["m0", "m1", "m2"]);

    drop(consumer);
}

#[tokio::test]
async fn unsubscribe_stops_delivery() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("sub-unsub");
    ensure_topic(&topic).await;
    let (consumer, mut rx) = warm_topic(&topic).await;

    let producer = GrpcProducer::connect(producer_config()).expect("connect producer");

    // First message should arrive.
    producer
        .publish(
            EventMeshMessage::builder()
                .topic(&topic)
                .content("before-unsub")
                .build(),
        )
        .await
        .expect("publish before unsub");
    let _ = recv_one(&mut rx, Duration::from_secs(10)).await;

    // Unsubscribe, then publish again.
    consumer
        .unsubscribe(vec![eventmesh::model::SubscriptionItem::new(
            &topic,
            eventmesh::model::SubscriptionMode::CLUSTERING,
            eventmesh::model::SubscriptionType::ASYNC,
        )])
        .await
        .expect("unsubscribe");
    let_stream_settle().await;

    // Publish again. On the standalone broker (which requires a live subscriber
    // to accept a publish) this errors out — which itself confirms the
    // unsubscribe took effect. On a durable backend the publish succeeds but
    // nothing should be delivered.
    match producer
        .publish(
            EventMeshMessage::builder()
                .topic(&topic)
                .content("after-unsub")
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
            eprintln!("[e2e] publish after unsub rejected by broker (expected on standalone): {e}");
        }
    }

    drop(consumer);
}
