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
    webhook::WebhookServer,
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

pub(crate) fn unique_topic(scope: &str) -> String {
    let n = SEQ.fetch_add(1, Ordering::Relaxed);
    let ts = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    format!("e2e-{scope}-{ts}-{n}")
}

fn identity() -> Identity {
    Identity::default()
        .with_env("env")
        .with_idc("idc")
        .with_system("sys")
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
    let endpoint = Endpoint::new(HOST, TCP_PORT).expect("valid TCP endpoint");
    TcpClient::new(
        TcpConfig::new(endpoint)
            .with_identity(identity())
            .with_credentials(credentials()),
    )
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

pub(crate) async fn warm_topic(
    topic: &str,
) -> (
    GrpcConsumer<CollectingListener>,
    mpsc::UnboundedReceiver<EventMeshMessage>,
) {
    let (listener, receiver) = CollectingListener::new();
    let consumer = grpc_client()
        .stream_consumer(
            grpc_consumer_options(),
            [Subscription::new(topic)],
            listener,
        )
        .await
        .expect("open gRPC stream consumer");
    let_stream_settle().await;
    (consumer, receiver)
}

fn free_port() -> u16 {
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

pub(crate) struct HttpConsumerHandle {
    consumer: HttpConsumer,
    webhook_url: String,
    server_task: JoinHandle<()>,
    shutdown_tx: Option<oneshot::Sender<()>>,
}

impl HttpConsumerHandle {
    pub(crate) fn consumer(&self) -> &HttpConsumer {
        &self.consumer
    }

    pub(crate) fn webhook_url(&self) -> &str {
        &self.webhook_url
    }
}

impl Drop for HttpConsumerHandle {
    fn drop(&mut self) {
        if let Some(tx) = self.shutdown_tx.take() {
            let _ = tx.send(());
        }
        self.server_task.abort();
    }
}

pub(crate) async fn http_warm_topic(
    topic: &str,
) -> (
    HttpConsumerHandle,
    mpsc::UnboundedReceiver<EventMeshMessage>,
) {
    assert!(ensure_runtime(), "ensure_runtime() must be called first");
    let (listener, receiver) = CollectingListener::new();
    let port = free_port();
    let bind_address: SocketAddr = format!("0.0.0.0:{port}").parse().expect("webhook address");
    let url = format!("http://{}:{port}/eventmesh/callback", webhook_host());
    let (shutdown_tx, shutdown_rx) = oneshot::channel();
    let server = WebhookServer::new(bind_address, listener)
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

    let consumer = http_client()
        .webhook_consumer(consumer_options())
        .expect("build HTTP consumer");
    consumer
        .subscribe(Subscription::new(topic), url.clone())
        .await
        .expect("subscribe HTTP webhook");
    let_stream_settle().await;

    (
        HttpConsumerHandle {
            consumer,
            webhook_url: url,
            server_task,
            shutdown_tx: Some(shutdown_tx),
        },
        receiver,
    )
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
            EventMeshMessage::new(
                request.topic.unwrap_or_default(),
                self.reply_content.clone(),
            )
            .into(),
        ))
    }
}
