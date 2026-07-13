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

//! E2e: TCP subscription — subscribe, receive messages, then unsubscribe.

use std::time::Duration;

use eventmesh::{
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    tcp::TcpProducer,
    transport::Publisher,
};

use crate::harness::{
    ensure_topic, let_stream_settle, tcp_consumer_config, tcp_producer_config, unique_topic,
    CollectingListener,
};
use crate::require_runtime;

use eventmesh::tcp::TcpConsumer;

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

#[tokio::test(flavor = "multi_thread")]
async fn tcp_subscribe_and_receive() {
    require_runtime!();
    let topic = unique_topic("tcp-sub-recv");
    ensure_topic(&topic).await;

    // Create a TCP consumer with a collecting listener.
    let (listener, mut rx) = CollectingListener::new();
    let consumer = TcpConsumer::connect(
        tcp_consumer_config(),
        listener,
        None::<std::future::Ready<()>>,
    )
    .await
    .expect("connect consumer");
    consumer
        .subscribe(&[SubscriptionItem::new(
            &topic,
            SubscriptionMode::CLUSTERING,
            SubscriptionType::ASYNC,
        )])
        .await
        .expect("subscribe");
    let_stream_settle().await;

    // Publish via TCP producer.
    let producer = TcpProducer::connect(tcp_producer_config())
        .await
        .expect("connect producer");
    let payload = "delivered-via-tcp";
    let msg = EventMeshMessage::builder()
        .topic(&topic)
        .content(payload)
        .build();
    producer.publish(msg).await.expect("publish");

    let received = recv_one(&mut rx, Duration::from_secs(10)).await;
    assert_eq!(received.content.as_deref(), Some(payload));
    assert_eq!(received.topic.as_deref(), Some(topic.as_str()));

    producer.shutdown().await;
    consumer.shutdown().await;
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_unsubscribe_stops_delivery() {
    require_runtime!();
    let topic = unique_topic("tcp-sub-unsub");
    ensure_topic(&topic).await;

    let (listener, mut rx) = CollectingListener::new();
    let consumer = TcpConsumer::connect(
        tcp_consumer_config(),
        listener,
        None::<std::future::Ready<()>>,
    )
    .await
    .expect("connect consumer");
    consumer
        .subscribe(&[SubscriptionItem::new(
            &topic,
            SubscriptionMode::CLUSTERING,
            SubscriptionType::ASYNC,
        )])
        .await
        .expect("subscribe");
    let_stream_settle().await;

    let producer = TcpProducer::connect(tcp_producer_config())
        .await
        .expect("connect producer");

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
        .unsubscribe(vec![SubscriptionItem::new(
            &topic,
            SubscriptionMode::CLUSTERING,
            SubscriptionType::ASYNC,
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
            // A timeout (`Err`) is the "nothing leaked" outcome. A closed
            // channel (`Ok(None)`) also means nothing was delivered — the
            // server tore down the subscription on unsubscribe — so treat that
            // as a pass too. Only an actual delivered message (`Ok(Some(..))`)
            // is a real leak.
            assert!(
                matches!(leaked, Err(_) | Ok(None)),
                "no message expected after unsubscribe, but got: {:?}",
                leaked.ok().flatten()
            );
        }
        Err(e) => {
            eprintln!("[e2e] publish after unsub rejected by broker (expected on standalone): {e}");
        }
    }

    producer.shutdown().await;
    consumer.shutdown().await;
}
