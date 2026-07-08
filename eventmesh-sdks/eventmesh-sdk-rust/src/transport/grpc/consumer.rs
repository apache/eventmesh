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

//! gRPC consumer — stream and webhook modes.
//!
//! Two consumer types are provided:
//!
//! - [`GrpcStreamConsumer<L>`] — opens a bidirectional gRPC stream and
//!   dispatches delivered messages to a user-supplied [`MessageListener`].
//!   The stream, receive loop, and heartbeat all run as background tasks.
//! - [`GrpcWebhookConsumer`] — a lightweight RPC-only client that registers
//!   webhook URLs with the runtime (the runtime POSTs delivered messages to
//!   the URL over HTTP).  No listener, no receive loop.
//!
//! Both types support [`subscribe_webhook`], [`unsubscribe`], and
//! [`wait_for_shutdown`].
//!
//! [`subscribe_webhook`]: GrpcStreamConsumer::subscribe_webhook
//! [`unsubscribe`]: GrpcStreamConsumer::unsubscribe

use std::collections::HashMap;
use std::future::Future;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tonic::codegen::tokio_stream::StreamExt;
use tracing::{debug, error, warn};

use crate::common::constants::SDK_STREAM_URL;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, EventMeshProtocolType, PublishResponse, SubscriptionItem};
use crate::transport::grpc::client::GrpcClient;
use crate::transport::grpc::codec;
use crate::transport::grpc::heartbeat::{self, StreamTx};
use crate::MessageListener;

// ---------------------------------------------------------------------------
// Shared types
// ---------------------------------------------------------------------------

/// A locally-recorded subscription entry, used by the heartbeat loop.
#[derive(Debug, Clone)]
pub(crate) struct SubscriptionEntry {
    #[allow(dead_code)]
    pub(crate) item: SubscriptionItem,
    pub(crate) url: String,
}

// ---------------------------------------------------------------------------
// Shutdown-signal helper
// ---------------------------------------------------------------------------

/// Spawn a watcher that cancels `token` when `signal` resolves.
///
/// If `signal` is `None`, nothing is spawned — the token can only be
/// cancelled by `shutdown()` / drop.
fn spawn_signal_watcher(
    signal: Option<impl Future<Output = ()> + Send + 'static>,
    token: CancellationToken,
) {
    if let Some(signal) = signal {
        tokio::spawn(async move {
            tokio::select! {
                _ = signal => token.cancel(),
                _ = token.cancelled() => {}
            }
        });
    }
}

/// Wait for a background task to finish, returning its result.
async fn await_task<T: Send + 'static>(handle: &Mutex<Option<JoinHandle<T>>>) -> Option<T> {
    match handle.lock().await.take() {
        Some(h) => match h.await {
            Ok(result) => Some(result),
            Err(e) => {
                warn!("background task panicked: {e}");
                None
            }
        },
        None => None,
    }
}

// ---------------------------------------------------------------------------
// GrpcStreamConsumer
// ---------------------------------------------------------------------------

/// gRPC stream consumer.
///
/// Opens a bidirectional gRPC stream, dispatches delivered messages to the
/// listener, and maintains a background heartbeat.  The stream, receive loop,
/// and heartbeat all run as background tokio tasks that are stopped when the
/// consumer is dropped or explicitly via [`shutdown`](Self::shutdown) /
/// [`wait_for_shutdown`](Self::wait_for_shutdown).
///
/// Subscribe and unsubscribe RPCs can be called at any time after construction
/// — they are sent over the already-open stream (subscribe) or as independent
/// unary RPCs (unsubscribe).
///
/// # Example
///
/// ```no_run
/// # use eventmesh::{
/// #     config::GrpcClientConfig, grpc::GrpcStreamConsumer,
/// #     model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
/// #     MessageListener,
/// # };
/// # struct MyListener;
/// # impl MessageListener for MyListener {
/// #     type Message = EventMeshMessage;
/// #     async fn handle(&self, _: Self::Message) -> Option<Self::Message> { None }
/// # }
/// # #[tokio::main]
/// # async fn main() -> eventmesh::Result<()> {
/// let consumer = GrpcStreamConsumer::subscribe_stream(
///     GrpcClientConfig::builder().build(),
///     MyListener,
///     vec![SubscriptionItem::new("t", SubscriptionMode::CLUSTERING, SubscriptionType::ASYNC)],
///     Some(async { tokio::signal::ctrl_c().await.ok(); }),
/// ).await?;
/// consumer.wait_for_shutdown().await;
/// # Ok(())
/// # }
/// ```
pub struct GrpcStreamConsumer<L: MessageListener<Message = EventMeshMessage>> {
    client: GrpcClient,
    config: crate::config::GrpcClientConfig,
    subscriptions: Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    _listener: std::marker::PhantomData<Arc<L>>,
    shutdown: CancellationToken,
    heartbeat_handle: Mutex<Option<JoinHandle<()>>>,
    stream_tx: StreamTx,
    driver_handle: Mutex<Option<JoinHandle<Result<()>>>>,
}

impl<L: MessageListener<Message = EventMeshMessage>> GrpcStreamConsumer<L> {
    /// Open a bidirectional stream subscription and spawn the receive loop +
    /// heartbeat as background tasks.
    ///
    /// `items` are sent as the first message on the stream (the subscription
    /// request).  `shutdown_signal` is an optional future whose resolution
    /// triggers graceful shutdown of the stream and heartbeat.  When omitted,
    /// shutdown can only be initiated by [`shutdown`](Self::shutdown) or drop.
    pub async fn subscribe_stream(
        config: crate::config::GrpcClientConfig,
        listener: L,
        items: Vec<SubscriptionItem>,
        shutdown_signal: Option<impl Future<Output = ()> + Send + 'static>,
    ) -> Result<Self> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }

        let client = GrpcClient::new(&config)?;
        let subscriptions = Arc::new(Mutex::new(HashMap::new()));
        let shutdown = CancellationToken::new();
        let stream_tx: StreamTx = Arc::new(Mutex::new(None));
        let listener = Arc::new(listener);

        // Signal watcher.
        spawn_signal_watcher(shutdown_signal, shutdown.clone());

        // Build the subscription event (first stream message).
        let event = codec::build_subscription_event(
            &config,
            EventMeshProtocolType::EventMeshMessage,
            None,
            &items,
        )?;

        // Eagerly open the stream.
        let (reply_tx, stream) = client.subscribe_stream(event).await?;
        let reply_tx = Arc::new(reply_tx);

        // Register the stream sender so heartbeat resubscribe can re-use it.
        {
            *stream_tx.lock().await = Some((*reply_tx).clone());
        }

        // Record the initial subscription.
        {
            let mut guard = subscriptions.lock().await;
            for item in &items {
                guard.insert(
                    item.topic.clone(),
                    SubscriptionEntry {
                        item: item.clone(),
                        url: SDK_STREAM_URL.to_string(),
                    },
                );
            }
        }

        // Spawn heartbeat.
        let heartbeat_handle = heartbeat::spawn(
            client.clone(),
            config.clone(),
            Arc::clone(&subscriptions),
            Arc::clone(&stream_tx),
            shutdown.clone(),
        );

        // Spawn the receive-loop driver.
        let driver_handle = spawn_stream_driver(
            stream,
            reply_tx,
            Arc::clone(&listener),
            config.clone(),
            stream_tx.clone(),
            shutdown.clone(),
        );

        Ok(Self {
            client,
            config,
            subscriptions,
            _listener: std::marker::PhantomData,
            shutdown,
            heartbeat_handle: Mutex::new(Some(heartbeat_handle)),
            stream_tx,
            driver_handle: Mutex::new(Some(driver_handle)),
        })
    }

    /// Subscribe to additional topics over the already-open stream.
    ///
    /// The subscription CloudEvent is sent through the stream's request
    /// channel.  Returns an error if the stream is no longer active.
    pub async fn subscribe(&self, items: Vec<SubscriptionItem>) -> Result<()> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }
        let event = codec::build_subscription_event(
            &self.config,
            EventMeshProtocolType::EventMeshMessage,
            None,
            &items,
        )?;
        let guard = self.stream_tx.lock().await;
        match guard.as_ref() {
            Some(tx) => {
                tx.send(event)
                    .await
                    .map_err(|e| EventMeshError::ChannelClosed(format!("subscribe: {e}")))?;
                let mut sub_guard = self.subscriptions.lock().await;
                for item in &items {
                    sub_guard.insert(
                        item.topic.clone(),
                        SubscriptionEntry {
                            item: item.clone(),
                            url: SDK_STREAM_URL.to_string(),
                        },
                    );
                }
                Ok(())
            }
            None => Err(EventMeshError::ChannelClosed("stream is not active".into())),
        }
    }

    /// Subscribe via webhook: the server POSTs delivered events to `url`.
    ///
    /// This is a unary gRPC RPC — it does not use the stream.  It can be
    /// called on a stream consumer to mix stream and webhook subscriptions.
    pub async fn subscribe_webhook(
        &self,
        items: Vec<SubscriptionItem>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        subscribe_webhook_rpc(&self.client, &self.config, &self.subscriptions, items, url).await
    }

    /// Unsubscribe from topics (independent unary RPC, not sent over the
    /// stream).
    pub async fn unsubscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        unsubscribe_rpc(&self.client, &self.config, &self.subscriptions, items).await
    }

    /// Current consumer group.
    pub fn consumer_group(&self) -> &str {
        &self.config.identity.consumer_group
    }

    /// Explicitly shut down: cancel the shared token and await the driver and
    /// heartbeat tasks' exit.
    pub async fn shutdown(&self) {
        self.shutdown.cancel();
        await_task(&self.driver_handle).await;
        await_task(&self.heartbeat_handle).await;
    }

    /// Block until the shutdown signal fires or the stream / heartbeat tasks
    /// exit on their own, then await their clean exit.
    ///
    /// If no shutdown signal was provided at construction time, this blocks
    /// until the tasks exit naturally (e.g. the server closes the stream).
    pub async fn wait_for_shutdown(&self) {
        self.shutdown.cancelled().await;
        await_task(&self.driver_handle).await;
        await_task(&self.heartbeat_handle).await;
    }
}

impl<L: MessageListener<Message = EventMeshMessage>> Drop for GrpcStreamConsumer<L> {
    fn drop(&mut self) {
        self.shutdown.cancel();
        if let Ok(mut guard) = self.heartbeat_handle.try_lock() {
            if let Some(handle) = guard.take() {
                handle.abort();
            }
        }
        if let Ok(mut guard) = self.driver_handle.try_lock() {
            if let Some(handle) = guard.take() {
                handle.abort();
            }
        }
    }
}

// ---------------------------------------------------------------------------
// GrpcWebhookConsumer
// ---------------------------------------------------------------------------

/// gRPC webhook consumer — a lightweight RPC-only client.
///
/// Registers webhook URLs with the runtime via unary gRPC RPCs.  The runtime
/// POSTs delivered messages to the registered URL over HTTP; the SDK does
/// not receive messages over gRPC for this consumer.  Use a
/// [`WebhookServer`](crate::transport::http::WebhookServer) or your own HTTP
/// endpoint to receive the pushes.
///
/// A background heartbeat task keeps subscriptions alive.
///
/// # Example
///
/// ```no_run
/// # use eventmesh::{config::GrpcClientConfig, grpc::GrpcWebhookConsumer};
/// # use eventmesh::model::{SubscriptionItem, SubscriptionMode, SubscriptionType};
/// # #[tokio::main]
/// # async fn main() -> eventmesh::Result<()> {
/// let consumer = GrpcWebhookConsumer::new(
///     GrpcClientConfig::builder().build(),
///     None::<std::future::Ready<()>>,
/// ).await?;
/// consumer.subscribe_webhook(
///     vec![SubscriptionItem::new("t", SubscriptionMode::CLUSTERING, SubscriptionType::ASYNC)],
///     "http://127.0.0.1:8080/cb",
/// ).await?;
/// consumer.wait_for_shutdown().await;
/// # Ok(())
/// # }
/// ```
pub struct GrpcWebhookConsumer {
    client: GrpcClient,
    config: crate::config::GrpcClientConfig,
    subscriptions: Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    shutdown: CancellationToken,
    heartbeat_handle: Mutex<Option<JoinHandle<()>>>,
}

impl GrpcWebhookConsumer {
    /// Create a webhook consumer.  Spawns a background heartbeat task.
    ///
    /// `shutdown_signal` is an optional future whose resolution triggers
    /// graceful shutdown of the heartbeat.  When omitted, shutdown can only be
    /// initiated by [`shutdown`](Self::shutdown) or drop.
    pub async fn new(
        config: crate::config::GrpcClientConfig,
        shutdown_signal: Option<impl Future<Output = ()> + Send + 'static>,
    ) -> Result<Self> {
        let client = GrpcClient::new(&config)?;
        let subscriptions = Arc::new(Mutex::new(HashMap::new()));
        let shutdown = CancellationToken::new();

        spawn_signal_watcher(shutdown_signal, shutdown.clone());

        let heartbeat_handle = heartbeat::spawn(
            client.clone(),
            config.clone(),
            Arc::clone(&subscriptions),
            // Webhook mode has no stream — stream_tx is always None.
            Arc::new(Mutex::new(None)),
            shutdown.clone(),
        );

        Ok(Self {
            client,
            config,
            subscriptions,
            shutdown,
            heartbeat_handle: Mutex::new(Some(heartbeat_handle)),
        })
    }

    /// Subscribe via webhook: the server POSTs delivered events to `url`.
    pub async fn subscribe_webhook(
        &self,
        items: Vec<SubscriptionItem>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        subscribe_webhook_rpc(&self.client, &self.config, &self.subscriptions, items, url).await
    }

    /// Unsubscribe from topics.
    pub async fn unsubscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        unsubscribe_rpc(&self.client, &self.config, &self.subscriptions, items).await
    }

    /// Current consumer group.
    pub fn consumer_group(&self) -> &str {
        &self.config.identity.consumer_group
    }

    /// Explicitly shut down: cancel the heartbeat and await its exit.
    pub async fn shutdown(&self) {
        self.shutdown.cancel();
        await_task(&self.heartbeat_handle).await;
    }

    /// Block until the shutdown signal fires or the heartbeat task exits.
    pub async fn wait_for_shutdown(&self) {
        self.shutdown.cancelled().await;
        await_task(&self.heartbeat_handle).await;
    }
}

impl Drop for GrpcWebhookConsumer {
    fn drop(&mut self) {
        self.shutdown.cancel();
        if let Ok(mut guard) = self.heartbeat_handle.try_lock() {
            if let Some(handle) = guard.take() {
                handle.abort();
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared RPC helpers
// ---------------------------------------------------------------------------

/// Apply the config's default request timeout to a short unary RPC.
async fn timed<T>(timeout: Duration, f: impl Future<Output = Result<T>>) -> Result<T> {
    tokio::time::timeout(timeout, f)
        .await
        .map_err(|_| EventMeshError::Timeout(timeout))?
}

async fn subscribe_webhook_rpc(
    client: &GrpcClient,
    config: &crate::config::GrpcClientConfig,
    subscriptions: &Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    items: Vec<SubscriptionItem>,
    url: impl Into<String>,
) -> Result<PublishResponse> {
    let url = url.into();
    if items.is_empty() {
        return Err(EventMeshError::InvalidArgument(
            "subscription items must not be empty".into(),
        ));
    }
    let event = codec::build_subscription_event(
        config,
        EventMeshProtocolType::EventMeshMessage,
        Some(&url),
        &items,
    )?;
    let resp = timed(config.timeout, client.subscribe_webhook(event)).await?;
    let response = codec::to_response(&resp);
    if response.is_success() {
        let mut guard = subscriptions.lock().await;
        for item in items {
            guard.insert(
                item.topic.clone(),
                SubscriptionEntry {
                    item,
                    url: url.clone(),
                },
            );
        }
    }
    Ok(response)
}

async fn unsubscribe_rpc(
    client: &GrpcClient,
    config: &crate::config::GrpcClientConfig,
    subscriptions: &Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    items: Vec<SubscriptionItem>,
) -> Result<PublishResponse> {
    if items.is_empty() {
        return Err(EventMeshError::InvalidArgument(
            "unsubscribe items must not be empty".into(),
        ));
    }
    let event = codec::build_subscription_event(
        config,
        EventMeshProtocolType::EventMeshMessage,
        None,
        &items,
    )?;
    let resp = timed(config.timeout, client.unsubscribe(event)).await?;
    let response = codec::to_response(&resp);
    if response.is_success() {
        let mut guard = subscriptions.lock().await;
        for item in &items {
            guard.remove(&item.topic);
        }
    }
    Ok(response)
}

// ---------------------------------------------------------------------------
// Stream receive-loop driver (spawned, not public)
// ---------------------------------------------------------------------------

/// Spawn the stream receive loop as a background task.
///
/// Dispatches delivered messages to the listener and sends back replies until
/// the server closes the stream or the shutdown token fires.  On exit, clears
/// the shared `stream_tx` so the heartbeat loop knows the stream is gone.
fn spawn_stream_driver(
    mut stream: tonic::Streaming<crate::proto_gen::PbCloudEvent>,
    reply_tx: Arc<tokio::sync::mpsc::Sender<crate::proto_gen::PbCloudEvent>>,
    listener: Arc<impl MessageListener<Message = EventMeshMessage>>,
    config: crate::config::GrpcClientConfig,
    stream_tx: StreamTx,
    shutdown: CancellationToken,
) -> JoinHandle<Result<()>> {
    tokio::spawn(async move {
        loop {
            tokio::select! {
                msg = stream.next() => match msg {
                    None => {
                        debug!("subscribe stream ended");
                        break;
                    }
                    Some(Err(status)) => {
                        warn!("stream receive error: {status}");
                        continue;
                    }
                    Some(Ok(cloud_event)) => {
                        let eventmesh_msg = codec::to_event_mesh_message(&cloud_event);
                        if eventmesh_msg.biz_seq_no.is_none() {
                            debug!("skipping control frame (no seqnum)");
                            continue;
                        }
                        debug!("delivered topic={:?}", eventmesh_msg.topic);
                        match listener.handle(eventmesh_msg).await {
                            Some(reply) => match build_reply(reply, &cloud_event, &config) {
                                Ok(reply_event) => {
                                    if reply_tx.send(reply_event).await.is_err() {
                                        warn!("reply channel closed; stopping receive loop");
                                        break;
                                    }
                                }
                                Err(e) => error!("failed to encode reply: {e}"),
                            },
                            None => { /* async ack: nothing to send back */ }
                        }
                    }
                },
                _ = shutdown.cancelled() => {
                    debug!("subscribe stream shutting down");
                    break;
                }
            }
        }

        *stream_tx.lock().await = None;
        Ok(())
    })
}

/// Build a reply CloudEvent (used by the stream receive loop when the listener
/// returns `Some(message)`).
///
/// Mirrors the Java SDK's `SubStreamHandler.buildReplyMessage`: the incoming
/// request's attributes are carried over into the reply so the broker can
/// correlate the reply with the original request.  The reply's own attributes
/// take precedence.
pub(crate) fn build_reply(
    reply: EventMeshMessage,
    request: &crate::proto_gen::PbCloudEvent,
    config: &crate::config::GrpcClientConfig,
) -> Result<crate::proto_gen::PbCloudEvent> {
    let mut event = codec::from_event_mesh_message(&reply, config)?;
    for (key, value) in &request.attributes {
        event
            .attributes
            .entry(key.clone())
            .or_insert_with(|| value.clone());
    }
    codec::mark_as_reply(&mut event);
    Ok(event)
}
