//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  The ASF
// licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//

//! Shared e2e helpers: config builders, unique resource names, topic creation,
//! and a collecting message listener.

use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

use tokio::sync::mpsc;
use tracing::{debug, warn};

use eventmesh::{
    config::GrpcClientConfig,
    grpc::GrpcConsumer,
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    transport::Subscriber,
    MessageListener,
};

use crate::runtime::{ensure_runtime, ADMIN_PORT, GRPC_PORT, HOST};

/// Monotonic counter to make every resource name globally unique, so parallel
/// tests never collide on a topic or consumer group.
static SEQ: AtomicU64 = AtomicU64::new(0);

/// A topic name unique to this process + scope.
pub(crate) fn unique_topic(scope: &str) -> String {
    let n = SEQ.fetch_add(1, Ordering::Relaxed);
    let ts = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    format!("e2e-{scope}-{ts}-{n}")
}

/// A producer config pointing at the local runtime, with a unique group.
pub(crate) fn producer_config() -> GrpcClientConfig {
    let group = unique_topic("producer-group");
    GrpcClientConfig::builder()
        .server_addr(HOST)
        .server_port(GRPC_PORT)
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .producer_group(group)
        .build()
}

/// A consumer config pointing at the local runtime, with a unique group.
pub(crate) fn consumer_config() -> GrpcClientConfig {
    let group = unique_topic("consumer-group");
    GrpcClientConfig::builder()
        .server_addr(HOST)
        .server_port(GRPC_PORT)
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .consumer_group(group)
        .build()
}

/// Create a topic via the admin HTTP API (idempotent). The broker requires a
/// topic to exist before a consumer can rebalance onto it (and the standalone
/// in-memory broker before a producer may publish), so this runs ahead of
/// every publish/subscribe test.
pub(crate) async fn ensure_topic(topic: &str) {
    assert!(ensure_runtime(), "ensure_runtime() must be called first");

    let url = format!("http://{HOST}:{ADMIN_PORT}/topic");

    // The admin `/topic` endpoint parses the POST body as
    // application/x-www-form-urlencoded (Netty HttpPostRequestDecoder), not
    // JSON — sending a JSON body yields a blank name and "Topic name can not
    // be blank". Send the name as a form field instead.
    //
    // The endpoint occasionally rejects concurrent creates with a 500; retry
    // briefly since "already exists" is a perfectly good outcome here.
    for attempt in 0..5u8 {
        let res = reqwest::Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
            .expect("reqwest client")
            .post(&url)
            .form(&[("name", topic)])
            .send()
            .await;
        match res {
            Ok(resp) => {
                let status = resp.status();
                debug!(%topic, %status, "ensure_topic response");
                if status.is_success() || status.as_u16() == 409 {
                    return;
                }
                // Fall through to retry for other statuses.
            }
            Err(e) => warn!(%topic, attempt, "ensure_topic error: {e}"),
        }
        tokio::time::sleep(Duration::from_millis(300)).await;
    }
    // Not fatal: a few standalone builds accept the publish anyway. The actual
    // publish will surface a real failure if the topic truly isn't there.
    warn!(%topic, "ensure_topic gave up after retries; continuing optimistically");
}

/// Wait briefly so a freshly-opened subscription stream has registered with the
/// broker before the test starts publishing (matters for the standalone store).
pub(crate) async fn let_stream_settle() {
    tokio::time::sleep(Duration::from_millis(800)).await;
}

/// Create a topic and subscribe a collecting consumer to it, returning the
/// consumer handle (keep it alive for the test) and the message receiver.
///
/// The standalone in-memory broker rejects publishes to a topic that has no
/// live subscriber, so every publish-oriented test warms the topic this way
/// first. Returns `(consumer, receiver)` so the caller can also assert delivery.
pub(crate) async fn warm_topic(
    topic: &str,
) -> (
    GrpcConsumer<CollectingListener>,
    mpsc::UnboundedReceiver<EventMeshMessage>,
) {
    let (listener, rx) = CollectingListener::new();
    let consumer = GrpcConsumer::new(consumer_config(), listener).expect("build consumer");
    consumer
        .subscribe(vec![SubscriptionItem::new(
            topic,
            SubscriptionMode::CLUSTERING,
            SubscriptionType::ASYNC,
        )])
        .await
        .expect("subscribe");
    let_stream_settle().await;
    (consumer, rx)
}

// ---------------------------------------------------------------------------
// Listeners
// ---------------------------------------------------------------------------

/// A listener that forwards every delivered message into an mpsc channel, so a
/// test can assert on what was received. Returns `None` (async ack, no reply).
pub(crate) struct CollectingListener {
    tx: mpsc::UnboundedSender<EventMeshMessage>,
}

impl CollectingListener {
    pub(crate) fn new() -> (Self, mpsc::UnboundedReceiver<EventMeshMessage>) {
        let (tx, rx) = mpsc::unbounded_channel();
        (Self { tx }, rx)
    }
}

impl MessageListener for CollectingListener {
    type Message = EventMeshMessage;

    async fn handle(&self, msg: EventMeshMessage) -> Option<EventMeshMessage> {
        let _ = self.tx.send(msg);
        None
    }
}

/// A listener used for request/reply: it echoes back a fixed reply content so
/// the producer's `request_reply` call receives a deterministic answer.
pub(crate) struct ReplyingListener {
    pub(crate) reply_content: String,
}

impl MessageListener for ReplyingListener {
    type Message = EventMeshMessage;

    async fn handle(&self, msg: EventMeshMessage) -> Option<EventMeshMessage> {
        debug!(
            topic = ?msg.topic,
            "replying listener received request, echoing reply"
        );
        Some(
            EventMeshMessage::builder()
                .topic(msg.topic.unwrap_or_default())
                .content(self.reply_content.clone())
                .build(),
        )
    }
}
