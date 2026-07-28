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

//! Shared v2 e2e helpers.

use std::net::SocketAddr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

use eventmesh::{
    config::{
        ConsumerOptions, Credentials, Endpoint, EndpointSet, GrpcConfig, GrpcConsumerOptions,
        HttpConfig, Identity, ProducerOptions, TcpConfig,
    },
    grpc::{GrpcClient, GrpcConsumer, GrpcProducer},
    http::{HttpClient, HttpConsumer},
    message::{EventMeshMessage, Message},
    subscription::Subscription,
    tcp::{TcpClient, TcpConsumer, TcpProducer},
    webhook::{WebhookOptions, WebhookServer},
    MessageHandler, Result,
};
use tokio::net::TcpStream;
use tokio::sync::{mpsc, oneshot};
use tokio::task::JoinHandle;
use tracing::{debug, warn};

use crate::runtime::{
    ensure_runtime, webhook_host, ADMIN_PORT, GRPC_PORT, HOST, HTTP_PORT, TCP_PORT,
};

static SEQ: AtomicU64 = AtomicU64::new(0);
static IDENTITY_SEQ: AtomicU64 = AtomicU64::new(1);
static TCP_E2E_LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

/// Serializes TCP e2e cases against the shared EventMesh/RocketMQ runtime.
///
/// Unique topics isolate message data, but concurrent TCP subscriptions still
/// compete in the runtime's asynchronous route refresh and RocketMQ rebalance
/// cycles. Keep the guard alive for the complete test case.
pub(crate) async fn serialize_tcp_e2e() -> tokio::sync::MutexGuard<'static, ()> {
    TCP_E2E_LOCK.lock().await
}

pub(crate) fn unique_topic(scope: &str) -> String {
    let n = SEQ.fetch_add(1, Ordering::Relaxed);
    let ts = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    format!("e2e-{scope}-{ts}-{n}")
}

fn identity() -> Identity {
    let identity_id = IDENTITY_SEQ.fetch_add(1, Ordering::Relaxed);
    let third_octet = (identity_id / 254) % 256;
    let fourth_octet = identity_id % 254 + 1;
    Identity::default()
        .with_env("env")
        .with_idc("idc")
        .with_system("sys")
        // 198.18.0.0/15 is reserved for benchmarking. The address is only a
        // logical gRPC subscriber identity and is never used as a route.
        .with_ip(format!("198.18.{third_octet}.{fourth_octet}"))
}

fn credentials() -> Credentials {
    Credentials::new().with_basic("eventmesh", "eventmesh")
}

pub(crate) fn producer_options() -> ProducerOptions {
    ProducerOptions::new(unique_topic("producer-group"))
}

pub(crate) fn consumer_options() -> ConsumerOptions {
    ConsumerOptions::new(unique_topic("consumer-group"))
}

pub(crate) fn grpc_consumer_options() -> GrpcConsumerOptions {
    GrpcConsumerOptions::new(unique_topic("consumer-group"))
}

pub(crate) fn grpc_client() -> GrpcClient {
    let endpoint = Endpoint::new(HOST, GRPC_PORT).expect("valid gRPC endpoint");
    GrpcClient::new(
        GrpcConfig::new(endpoint)
            .with_identity(identity())
            .with_credentials(credentials()),
    )
    .expect("build gRPC client")
}

pub(crate) fn grpc_producer() -> GrpcProducer {
    grpc_client()
        .producer(producer_options())
        .expect("build gRPC producer")
}

pub(crate) fn http_client() -> HttpClient {
    let endpoint = Endpoint::new(HOST, HTTP_PORT).expect("valid HTTP endpoint");
    HttpClient::new(
        HttpConfig::new(EndpointSet::new([endpoint]).expect("non-empty endpoints"))
            .with_identity(identity())
            .with_credentials(credentials()),
    )
    .expect("build HTTP client")
}

pub(crate) fn http_producer() -> eventmesh::http::HttpProducer {
    http_client()
        .producer(producer_options())
        .expect("build HTTP producer")
}

pub(crate) fn tcp_client() -> TcpClient {
    tcp_client_with_system("sys")
}

pub(crate) fn tcp_client_with_system(system: &str) -> TcpClient {
    let endpoint = Endpoint::new(HOST, TCP_PORT).expect("valid TCP endpoint");
    TcpClient::new(
        TcpConfig::new(endpoint)
            .with_identity(identity().with_system(system))
            .with_credentials(credentials()),
    )
    .expect("build TCP client")
}

pub(crate) async fn tcp_producer() -> TcpProducer {
    tcp_client()
        .producer(producer_options())
        .await
        .expect("build TCP producer")
}

pub(crate) async fn ensure_topic(topic: &str) {
    assert!(ensure_runtime(), "ensure_runtime() must be called first");
    let url = format!("http://{HOST}:{ADMIN_PORT}/topic");
    let client = reqwest::Client::builder()
        .no_proxy()
        .timeout(Duration::from_secs(5))
        .build()
        .expect("reqwest client");

    for attempt in 0..5u8 {
        let result = client.post(&url).form(&[("name", topic)]).send().await;
        match result {
            Ok(response) if response.status().is_success() || response.status().as_u16() == 409 => {
                break;
            }
            Ok(response) => debug!(%topic, status = %response.status(), "ensure_topic response"),
            Err(error) => warn!(%topic, attempt, "ensure_topic error: {error}"),
        }
        tokio::time::sleep(Duration::from_millis(300)).await;
    }
    // RocketMQ's admin call updates brokers before the new route is visible
    // through NameServer. A consumer started in that gap logs "topic not
    // exist" and may not rebalance again until after a short E2E timeout.
    let deadline = Instant::now() + Duration::from_secs(15);
    loop {
        if let Ok(response) = client.get(&url).send().await {
            if let Ok(topics) = response.json::<Vec<serde_json::Value>>().await {
                if topics
                    .iter()
                    .any(|entry| entry.get("name").and_then(|name| name.as_str()) == Some(topic))
                {
                    return;
                }
            }
        }
        assert!(
            Instant::now() < deadline,
            "topic {topic:?} was not visible through EventMesh admin within 15s"
        );
        tokio::time::sleep(Duration::from_millis(200)).await;
    }
}

/// Ask the Runtime admin API to gracefully disconnect only TCP sessions whose
/// HELLO `subsystem` matches `subsystem`. This exercises the same server-side
/// disconnect path used by operational client management without restarting
/// the shared Runtime container.
pub(crate) async fn reject_tcp_subsystem(subsystem: &str) {
    let url = format!("http://{HOST}:{ADMIN_PORT}/clientManage/rejectClientBySubSystem");
    let response = reqwest::Client::builder()
        .no_proxy()
        .timeout(Duration::from_secs(5))
        .build()
        .expect("reqwest client")
        .get(url)
        .query(&[("subsystem", subsystem)])
        .send()
        .await
        .expect("reject TCP subsystem through Runtime admin API");
    assert!(
        response.status().is_success(),
        "reject TCP subsystem status"
    );
    let body = response.text().await.expect("reject TCP subsystem body");
    assert!(
        body.contains("success!") && !body.contains("no session had been closed"),
        "Runtime did not find the test TCP sessions: {body}"
    );
}

pub(crate) async fn let_stream_settle() {
    tokio::time::sleep(Duration::from_millis(800)).await;
}

/// The TCP Runtime acknowledges `SUBSCRIBE_REQUEST` before its RocketMQ push
/// consumer has refreshed routes and sent the new subscription heartbeat.
/// Route refresh and rebalance run on separate scheduled intervals, so allow
/// enough time for both before publishing a message under test.
pub(crate) async fn let_tcp_subscription_settle() {
    tokio::time::sleep(Duration::from_secs(45)).await;
}

/// Poll the Runtime's protocol-specific client inventory until `group` is
/// present or absent. Unique E2E consumer groups make this an unambiguous
/// server-side subscription assertion even though the current admin response
/// does not include the topic.
pub(crate) async fn wait_for_client_group(protocol: &str, group: &str, expected: bool) {
    let path = match protocol {
        "http" => "/client/http",
        "grpc" => "/client/grpc",
        other => panic!("unsupported admin client protocol {other:?}"),
    };
    let url = format!("http://{HOST}:{ADMIN_PORT}{path}");
    let client = reqwest::Client::builder()
        .no_proxy()
        .timeout(Duration::from_secs(5))
        .build()
        .expect("reqwest client");
    let deadline = Instant::now() + Duration::from_secs(15);
    let mut last_groups = Vec::new();

    loop {
        if let Ok(response) = client.get(&url).send().await {
            if response.status().is_success() {
                if let Ok(entries) = response.json::<Vec<serde_json::Value>>().await {
                    last_groups = entries
                        .iter()
                        .filter_map(|entry| entry.get("group").and_then(serde_json::Value::as_str))
                        .map(str::to_owned)
                        .collect();
                    let present = last_groups.iter().any(|candidate| candidate == group);
                    if present == expected {
                        return;
                    }
                }
            }
        }
        assert!(
            Instant::now() < deadline,
            "Runtime admin {path} did not report consumer group {group:?} as {} within 15s; \
             last groups: {last_groups:?}",
            if expected { "present" } else { "absent" }
        );
        tokio::time::sleep(Duration::from_millis(200)).await;
    }
}

/// Poll the Runtime's TCP topic listener inventory until the unique E2E topic
/// has (or has no) active listening sessions.
pub(crate) async fn wait_for_tcp_topic_listener(topic: &str, expected: bool) {
    let url = format!("http://{HOST}:{ADMIN_PORT}/clientManage/showListenClientByTopic");
    let client = reqwest::Client::builder()
        .no_proxy()
        .timeout(Duration::from_secs(5))
        .build()
        .expect("reqwest client");
    let deadline = Instant::now() + Duration::from_secs(15);
    let mut last_body = String::new();

    loop {
        if let Ok(response) = client.get(&url).query(&[("topic", topic)]).send().await {
            if response.status().is_success() {
                if let Ok(body) = response.text().await {
                    let present = !body.trim().is_empty();
                    last_body = body;
                    if present == expected {
                        return;
                    }
                }
            }
        }
        assert!(
            Instant::now() < deadline,
            "Runtime admin TCP listener query did not report topic {topic:?} as {} within 15s; \
             last response: {last_body:?}",
            if expected { "present" } else { "absent" }
        );
        tokio::time::sleep(Duration::from_millis(200)).await;
    }
}

pub(crate) async fn warm_topic(
    topic: &str,
) -> (
    GrpcConsumer<CollectingListener>,
    mpsc::UnboundedReceiver<EventMeshMessage>,
) {
    warm_topic_as(topic, unique_topic("consumer-group")).await
}

pub(crate) async fn warm_topic_as(
    topic: &str,
    consumer_group: String,
) -> (
    GrpcConsumer<CollectingListener>,
    mpsc::UnboundedReceiver<EventMeshMessage>,
) {
    let (listener, receiver) = CollectingListener::new();
    let consumer = grpc_client()
        .stream_consumer(
            GrpcConsumerOptions::new(consumer_group),
            [Subscription::new(topic)],
            listener,
        )
        .await
        .expect("open gRPC stream consumer");
    let_stream_settle().await;
    (consumer, receiver)
}

pub(crate) fn free_port() -> u16 {
    let listener = std::net::TcpListener::bind("0.0.0.0:0").expect("bind port probe");
    let port = listener.local_addr().expect("probe local address").port();
    drop(listener);
    port
}

async fn wait_for_listen(address: SocketAddr, timeout: Duration) {
    let deadline = Instant::now() + timeout;
    loop {
        if Instant::now() >= deadline {
            panic!("webhook server at {address} did not start within {timeout:?}");
        }
        if TcpStream::connect(address).await.is_ok() {
            return;
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
}

/// A standalone SDK webhook server for transports that own their registration
/// lifecycle (notably gRPC webhook consumers).
pub(crate) struct WebhookServerHandle {
    webhook_url: String,
    server_task: JoinHandle<()>,
    shutdown_tx: Option<oneshot::Sender<()>>,
}

impl WebhookServerHandle {
    pub(crate) fn webhook_url(&self) -> &str {
        &self.webhook_url
    }
}

impl Drop for WebhookServerHandle {
    fn drop(&mut self) {
        if let Some(tx) = self.shutdown_tx.take() {
            let _ = tx.send(());
        }
        self.server_task.abort();
    }
}

pub(crate) async fn start_webhook_server() -> (
    WebhookServerHandle,
    mpsc::UnboundedReceiver<EventMeshMessage>,
) {
    let (listener, receiver) = CollectingListener::new();
    let port = free_port();
    let bind_address: SocketAddr = format!("0.0.0.0:{port}").parse().expect("webhook address");
    let url = format!("http://{}:{port}/eventmesh/callback", webhook_host());
    let (shutdown_tx, shutdown_rx) = oneshot::channel();
    let server = WebhookServer::bind(bind_address, listener)
        .await
        .expect("bind webhook server")
        .with_advertise_url(url.clone())
        .with_graceful_shutdown(async move {
            let _ = shutdown_rx.await;
        });
    let server_task = tokio::spawn(async move {
        if let Err(error) = server.await {
            warn!(%error, "webhook server exited with error");
        }
    });
    wait_for_listen(bind_address, Duration::from_secs(5)).await;
    (
        WebhookServerHandle {
            webhook_url: url,
            server_task,
            shutdown_tx: Some(shutdown_tx),
        },
        receiver,
    )
}

pub(crate) async fn http_warm_topic(
    topic: &str,
) -> (HttpConsumer, mpsc::UnboundedReceiver<EventMeshMessage>) {
    http_warm_topic_as(topic, unique_topic("consumer-group")).await
}

pub(crate) async fn http_warm_topic_as(
    topic: &str,
    consumer_group: String,
) -> (HttpConsumer, mpsc::UnboundedReceiver<EventMeshMessage>) {
    assert!(ensure_runtime(), "ensure_runtime() must be called first");
    let (listener, receiver) = CollectingListener::new();
    let port = free_port();
    let bind_address: SocketAddr = format!("0.0.0.0:{port}").parse().expect("webhook address");
    let url = format!("http://{}:{port}/eventmesh/callback", webhook_host());
    let consumer = http_client()
        .consumer(
            ConsumerOptions::new(consumer_group),
            WebhookOptions::new(bind_address).with_advertise_url(url),
            [Subscription::new(topic)],
            listener,
        )
        .await
        .expect("open HTTP consumer");
    let_stream_settle().await;

    (consumer, receiver)
}

pub(crate) async fn tcp_warm_topic(
    topic: &str,
) -> (
    TcpConsumer<CollectingListener>,
    mpsc::UnboundedReceiver<EventMeshMessage>,
) {
    assert!(ensure_runtime(), "ensure_runtime() must be called first");
    let (listener, receiver) = CollectingListener::new();
    let consumer = tcp_client()
        .consumer(consumer_options(), listener)
        .await
        .expect("open TCP consumer");
    consumer
        .subscribe(Subscription::new(topic))
        .await
        .expect("subscribe TCP consumer");
    let_tcp_subscription_settle().await;
    (consumer, receiver)
}

pub(crate) struct CollectingListener {
    tx: mpsc::UnboundedSender<EventMeshMessage>,
}

impl CollectingListener {
    pub(crate) fn new() -> (Self, mpsc::UnboundedReceiver<EventMeshMessage>) {
        let (tx, receiver) = mpsc::unbounded_channel();
        (Self { tx }, receiver)
    }
}

impl MessageHandler for CollectingListener {
    async fn handle(&self, message: Message) -> Result<Option<Message>> {
        let message = message.into_event_mesh()?;
        let _ = self.tx.send(message);
        Ok(None)
    }
}

pub(crate) struct ReplyingListener {
    pub(crate) reply_content: String,
}

impl MessageHandler for ReplyingListener {
    async fn handle(&self, message: Message) -> Result<Option<Message>> {
        let request = message.into_event_mesh()?;
        Ok(Some(
            EventMeshMessage::new(request.topic(), self.reply_content.clone())?.into(),
        ))
    }
}
