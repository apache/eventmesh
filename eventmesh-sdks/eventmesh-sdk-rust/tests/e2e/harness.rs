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

use std::net::SocketAddr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use tokio::net::TcpStream;
use tokio::sync::mpsc;
use tokio::sync::oneshot;
use tokio::task::JoinHandle;
use tracing::{debug, warn};

use eventmesh::{
    config::GrpcClientConfig,
    grpc::GrpcConsumer,
    http::{HttpConsumer, WebhookServer},
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    transport::Subscriber,
    MessageListener,
};

use eventmesh::config::HttpClientConfig;

use crate::runtime::{ensure_runtime, webhook_host, ADMIN_PORT, GRPC_PORT, HOST, HTTP_PORT};

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
// HTTP transport helpers
// ---------------------------------------------------------------------------

/// An HTTP producer config pointing at the local runtime, with a unique group.
pub(crate) fn http_producer_config() -> HttpClientConfig {
    let group = unique_topic("http-producer-group");
    HttpClientConfig::builder()
        .servers(format!("{HOST}:{HTTP_PORT}"))
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .producer_group(group)
        .build()
        .expect("http producer config")
}

/// An HTTP consumer config pointing at the local runtime, with a unique group.
pub(crate) fn http_consumer_config() -> HttpClientConfig {
    let group = unique_topic("http-consumer-group");
    HttpClientConfig::builder()
        .servers(format!("{HOST}:{HTTP_PORT}"))
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .consumer_group(group)
        .build()
        .expect("http consumer config")
}

/// Find a free TCP port on the host by briefly binding to port 0.
///
/// There is an inherent TOCTOU race between this function returning and the
/// caller re-binding, but in practice the window is microseconds and perfectly
/// acceptable for parallel test wiring.
fn free_port() -> u16 {
    let listener = std::net::TcpListener::bind("0.0.0.0:0").expect("bind for port probe");
    let port = listener.local_addr().expect("local_addr").port();
    drop(listener);
    port
}

/// Poll a TCP address until it accepts a connection or `timeout` elapses.
async fn wait_for_listen(addr: SocketAddr, timeout: Duration) {
    let deadline = Instant::now() + timeout;
    loop {
        if Instant::now() >= deadline {
            panic!("webhook server at {addr} did not start within {timeout:?}");
        }
        if TcpStream::connect(addr).await.is_ok() {
            return;
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
}

/// Owns an `HttpConsumer` and its webhook server task, cleaning both up on
/// drop. Tests hold this alive for the duration of the scenario.
pub(crate) struct HttpConsumerHandle {
    consumer: Option<HttpConsumer>,
    server_task: Option<JoinHandle<()>>,
    shutdown_tx: Option<oneshot::Sender<()>>,
}

impl HttpConsumerHandle {
    /// Borrow the underlying consumer (for unsubscribe, etc.).
    pub(crate) fn consumer(&self) -> &HttpConsumer {
        self.consumer.as_ref().expect("consumer present")
    }
}

impl Drop for HttpConsumerHandle {
    fn drop(&mut self) {
        // Signal the webhook server's graceful-shutdown future.
        if let Some(tx) = self.shutdown_tx.take() {
            let _ = tx.send(());
        }
        // Abort the server task if it hasn't exited yet.
        if let Some(handle) = self.server_task.take() {
            handle.abort();
        }
        // Dropping the consumer cancels its heartbeat task (see HttpConsumer::drop).
        self.consumer.take();
    }
}

/// Create a topic, start a webhook server, subscribe an HTTP consumer to the
/// topic via webhook, and return the handle plus the message receiver.
///
/// This mirrors the gRPC [`warm_topic`] but for the HTTP transport: the
/// standalone in-memory broker rejects publishes to topics with no live
/// subscriber, so every publish-oriented HTTP test warms the topic this way
/// first. Returns `(handle, receiver)` so the caller can also assert delivery.
pub(crate) async fn http_warm_topic(
    topic: &str,
) -> (
    HttpConsumerHandle,
    mpsc::UnboundedReceiver<EventMeshMessage>,
) {
    assert!(ensure_runtime(), "ensure_runtime() must be called first");

    let (listener, rx) = CollectingListener::new();
    let listener = Arc::new(listener);

    // Allocate a webhook port and build the advertise URL the runtime will use.
    let port = free_port();
    let bind_addr: SocketAddr = format!("0.0.0.0:{port}")
        .parse()
        .expect("valid webhook bind addr");
    let whost = webhook_host();
    let webhook_url = format!("http://{whost}:{port}/eventmesh/callback");

    // Start the built-in webhook server.
    let (shutdown_tx, shutdown_rx) = oneshot::channel::<()>();
    let server = WebhookServer::new(bind_addr, listener.clone())
        .with_advertise_url(webhook_url.clone())
        .with_graceful_shutdown(async move {
            let _ = shutdown_rx.await;
        });
    let server_task = tokio::spawn(async move {
        if let Err(e) = server.await {
            warn!("webhook server exited with error: {e}");
        }
    });
    // Wait for the server to actually bind before subscribing.
    wait_for_listen(bind_addr, Duration::from_secs(5)).await;
    debug!(%topic, port, url = %webhook_url, "HTTP webhook server ready");

    // Create the HTTP consumer (spawns heartbeat task) and subscribe.
    let consumer = HttpConsumer::new(http_consumer_config()).expect("build http consumer");
    consumer
        .subscribe_webhook(
            vec![SubscriptionItem::new(
                topic,
                SubscriptionMode::CLUSTERING,
                SubscriptionType::ASYNC,
            )],
            webhook_url,
        )
        .await
        .expect("subscribe_webhook");

    let_stream_settle().await;

    let handle = HttpConsumerHandle {
        consumer: Some(consumer),
        server_task: Some(server_task),
        shutdown_tx: Some(shutdown_tx),
    };

    (handle, rx)
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
