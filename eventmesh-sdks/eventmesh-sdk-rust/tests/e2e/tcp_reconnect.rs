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

//! E2E: recover TCP subscriptions after server disconnects and handler
//! failures, including real unacknowledged-message redelivery and TTL expiry.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use eventmesh::message::{EventMeshMessage, Message};
use tokio::sync::{mpsc, Notify};

use crate::harness::{
    consumer_options, ensure_topic, let_tcp_subscription_settle, producer_options,
    reject_tcp_subsystem, serialize_tcp_e2e, tcp_client_with_system, unique_topic,
    CollectingListener,
};
use crate::require_runtime;
use crate::runtime::{ADMIN_PORT, HOST};

#[derive(Clone, Copy)]
enum HandlerFailure {
    Panic,
    Error,
}

async fn wait_for_session_count(topic: &str, expected: usize) {
    let client = reqwest::Client::builder()
        .no_proxy()
        .timeout(Duration::from_secs(5))
        .build()
        .expect("admin HTTP client");
    let url = format!("http://{HOST}:{ADMIN_PORT}/clientManage/showListenClientByTopic");
    let deadline = Instant::now() + Duration::from_secs(15);
    loop {
        let body = client
            .get(&url)
            .query(&[("topic", topic)])
            .send()
            .await
            .expect("query TCP sessions")
            .error_for_status()
            .expect("TCP session query status")
            .text()
            .await
            .expect("TCP session query body");
        if body.lines().filter(|line| line.starts_with("pid=")).count() == expected {
            return;
        }
        assert!(
            Instant::now() < deadline,
            "expected {expected} listening sessions for {topic}, got: {body}"
        );
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
}

async fn handler_failure_redelivers_and_recovers(failure: HandlerFailure, hold_for: Duration) {
    let _tcp_e2e_guard = serialize_tcp_e2e().await;
    require_runtime!();

    let topic = unique_topic("tcp-handler-failure");
    let other_topic = unique_topic("tcp-handler-replay");
    ensure_topic(&topic).await;
    ensure_topic(&other_topic).await;
    let client = tcp_client_with_system(&unique_topic("tcp-handler-subsystem"));
    let options = consumer_options();
    let release = Arc::new(Notify::new());
    let first = Arc::new(AtomicBool::new(true));
    let (failed_tx, mut failed_rx) = mpsc::unbounded_channel();
    let (recovered_tx, mut recovered_rx) = mpsc::unbounded_channel();
    let consumer = client
        .consumer(options.clone(), {
            let release = Arc::clone(&release);
            move |message: Message| {
                let release = Arc::clone(&release);
                let first = Arc::clone(&first);
                let failed_tx = failed_tx.clone();
                let recovered_tx = recovered_tx.clone();
                async move {
                    let message = message.into_event_mesh()?;
                    if first.swap(false, Ordering::SeqCst) {
                        failed_tx.send(message).expect("report first delivery");
                        release.notified().await;
                        return match failure {
                            HandlerFailure::Panic => panic!("intentional TCP E2E handler panic"),
                            HandlerFailure::Error => Err(eventmesh::Error::Tcp(
                                "intentional TCP E2E handler error".into(),
                            )),
                        };
                    }
                    recovered_tx
                        .send(message)
                        .expect("report recovered delivery");
                    Ok(None)
                }
            }
        })
        .await
        .expect("open failing TCP consumer");
    for subscribed_topic in [&topic, &other_topic] {
        consumer
            .subscribe(eventmesh::Subscription::new(subscribed_topic))
            .await
            .expect("subscribe failing TCP consumer");
    }
    let producer = client
        .producer(producer_options())
        .await
        .expect("open handler failure test producer");
    let_tcp_subscription_settle().await;

    let original = EventMeshMessage::builder()
        .topic(&topic)
        .content("failed-delivery")
        .unique_id(unique_topic("failed-message"))
        .ttl_millis(60_000)
        .build()
        .unwrap();
    let receipt = producer
        .publish(original.clone().into())
        .await
        .expect("publish failed delivery");
    assert_eq!(receipt.code, 0);
    let failed = tokio::time::timeout(Duration::from_secs(35), failed_rx.recv())
        .await
        .expect("first consumer must receive the original delivery")
        .expect("failed delivery channel closed");
    assert_eq!(failed.unique_id(), original.unique_id());

    // Add the other session after the first handler has received the message,
    // so the failure is deterministic while Runtime still has a retry target.
    let (listener, mut redelivered_rx) = CollectingListener::new();
    let peer = client
        .consumer(options, listener)
        .await
        .expect("open redelivery target");
    peer.subscribe(eventmesh::Subscription::new(&topic))
        .await
        .expect("subscribe redelivery target");
    wait_for_session_count(&topic, 2).await;
    tokio::time::sleep(hold_for).await;
    release.notify_one();

    let redelivered = tokio::time::timeout(Duration::from_secs(15), redelivered_rx.recv())
        .await
        .expect("Runtime must redeliver the unacknowledged message to the other session")
        .expect("redelivery channel closed");
    assert_eq!(redelivered.content(), original.content());
    assert_eq!(redelivered.unique_id(), original.unique_id());
    assert_eq!(redelivered.ttl_millis(), Some(60_000));

    // The failed session has been removed before redelivery. Two sessions now
    // means it has reconnected and replayed its subscription; the second topic
    // also verifies that every confirmed subscription is restored.
    wait_for_session_count(&topic, 2).await;
    wait_for_session_count(&other_topic, 1).await;
    peer.shutdown();
    peer.join().await.expect("join redelivery target");
    wait_for_session_count(&topic, 1).await;

    for subscribed_topic in [&topic, &other_topic] {
        let receipt = producer
            .publish(
                EventMeshMessage::new(subscribed_topic, "after-handler-failure")
                    .unwrap()
                    .into(),
            )
            .await
            .expect("publish after subscription replay");
        assert_eq!(receipt.code, 0);
    }
    let mut topics = std::collections::HashSet::new();
    for _ in 0..2 {
        let delivered = tokio::time::timeout(Duration::from_secs(35), recovered_rx.recv())
            .await
            .expect("recovered consumer must process both subscriptions")
            .expect("recovered delivery channel closed");
        assert_eq!(delivered.content(), "after-handler-failure");
        topics.insert(delivered.topic().to_owned());
    }
    assert_eq!(topics, [topic, other_topic].into_iter().collect());
    producer.shutdown().await;
    consumer.shutdown();
    consumer.join().await.expect("join recovered TCP consumer");
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_handler_panic_redelivers_and_recovers_subscriptions() {
    handler_failure_redelivers_and_recovers(HandlerFailure::Panic, Duration::ZERO).await;
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_handler_error_redelivers_and_recovers_subscriptions() {
    handler_failure_redelivers_and_recovers(HandlerFailure::Error, Duration::ZERO).await;
}

#[tokio::test(flavor = "multi_thread")]
#[ignore = "requires a Runtime built with the lowercase TTL fix"]
async fn tcp_explicit_ttl_keeps_delivery_unacked_past_default_timeout() {
    // Without the Runtime TTL fix, the 3-second fallback expires this delivery
    // on the next 5-second cleaner tick. Nine seconds covers that entire window.
    handler_failure_redelivers_and_recovers(HandlerFailure::Panic, Duration::from_secs(9)).await;
}

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
