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
//! Both types support [`subscribe_webhook`], [`unsubscribe_stream`] /
//! [`unsubscribe_webhook`], and [`wait_for_shutdown`].
//!
//! [`subscribe_webhook`]: GrpcStreamConsumer::subscribe_webhook
//! [`unsubscribe_stream`]: GrpcStreamConsumer::unsubscribe_stream
//! [`unsubscribe_webhook`]: GrpcStreamConsumer::unsubscribe_webhook

use std::collections::HashMap;
use std::future::Future;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;
use tokio::sync::Semaphore;
use tokio::task::{JoinHandle, JoinSet};
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
/// Delivered messages are dispatched to the listener **concurrently** — up to
/// [`GrpcClientConfig::max_concurrent_handlers`] (default 64) may be in flight
/// at once.  This means replies can arrive at the broker out of message-arrival
/// order (each reply is self-correlating via request attributes, so protocol
/// correctness is unaffected).  Set `max_concurrent_handlers = 1` to restore
/// strict serial / in-order-reply semantics matching the Java SDK.
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
    subscriptions: Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
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
    ///
    /// # Runtime requirement
    ///
    /// This method **requires a multi-threaded tokio runtime**. On a
    /// current-thread runtime (the default for `#[tokio::test]`),
    /// tonic's background connection tasks cannot progress and the call
    /// will time out after 15 seconds with a diagnostic error. Use:
    ///
    /// ```text
    /// #[tokio::test(flavor = "multi_thread")]
    /// ```
    ///
    /// (`#[tokio::main]` is already multi-threaded by default.)
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
                    (item.topic.clone(), SDK_STREAM_URL.to_string()),
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
    /// channel. Returns an error if the stream is no longer active, is shutting
    /// down, or remains backpressured beyond the configured timeout.
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
        match heartbeat::stream_sender(&self.stream_tx).await {
            Some(tx) => {
                // The state lock is released before awaiting bounded-channel
                // capacity. This keeps stream teardown and heartbeat replay
                // from being blocked by a backpressured caller subscription.
                match heartbeat::await_with_timeout_or_shutdown(
                    &self.shutdown,
                    self.config.timeout,
                    tx.reserve(),
                )
                .await
                {
                    heartbeat::OperationOutcome::Completed(Ok(permit)) => {
                        permit.send(event);
                        let mut sub_guard = self.subscriptions.lock().await;
                        for item in &items {
                            sub_guard.insert(
                                (item.topic.clone(), SDK_STREAM_URL.to_string()),
                                SubscriptionEntry {
                                    item: item.clone(),
                                    url: SDK_STREAM_URL.to_string(),
                                },
                            );
                        }
                        Ok(())
                    }
                    heartbeat::OperationOutcome::Completed(Err(e)) => {
                        Err(EventMeshError::ChannelClosed(format!("subscribe: {e}")))
                    }
                    heartbeat::OperationOutcome::TimedOut => {
                        Err(EventMeshError::Timeout(self.config.timeout))
                    }
                    heartbeat::OperationOutcome::Cancelled => Err(EventMeshError::ChannelClosed(
                        "stream is shutting down".into(),
                    )),
                }
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

    /// Unsubscribe stream-mode topics (registered via `subscribe_stream` or
    /// `subscribe`).
    ///
    /// This is an independent unary RPC — it is **not** sent over the open
    /// stream. The server matches stream clients by IP + PID, so no URL is
    /// needed.
    pub async fn unsubscribe_stream(
        &self,
        items: Vec<SubscriptionItem>,
    ) -> Result<PublishResponse> {
        unsubscribe_stream_rpc(&self.client, &self.config, &self.subscriptions, items).await
    }

    /// Unsubscribe webhook-mode topics (registered via `subscribe_webhook`).
    ///
    /// `url` must be the same webhook URL passed to `subscribe_webhook`.
    /// The server matches webhook clients by URL — omitting or mismatching
    /// it leaves a ghost subscription that continues to receive pushes.
    pub async fn unsubscribe_webhook(
        &self,
        items: Vec<SubscriptionItem>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        unsubscribe_webhook_rpc(&self.client, &self.config, &self.subscriptions, items, url).await
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
    subscriptions: Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
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

    /// Unsubscribe webhook topics.
    ///
    /// `url` must be the same webhook URL passed to `subscribe_webhook`.
    /// The server matches webhook clients by URL — omitting or mismatching
    /// it leaves a ghost subscription that continues to receive pushes.
    pub async fn unsubscribe_webhook(
        &self,
        items: Vec<SubscriptionItem>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        unsubscribe_webhook_rpc(&self.client, &self.config, &self.subscriptions, items, url).await
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
    subscriptions: &Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
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
                (item.topic.clone(), url.clone()),
                SubscriptionEntry {
                    item,
                    url: url.clone(),
                },
            );
        }
        Ok(response)
    } else {
        Err(EventMeshError::Server {
            code: response.code.unwrap_or(-1) as i32,
            message: response
                .message
                .unwrap_or_else(|| "subscribe failed".into()),
        })
    }
}

async fn unsubscribe_stream_rpc(
    client: &GrpcClient,
    config: &crate::config::GrpcClientConfig,
    subscriptions: &Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    items: Vec<SubscriptionItem>,
) -> Result<PublishResponse> {
    if items.is_empty() {
        return Err(EventMeshError::InvalidArgument(
            "unsubscribe items must not be empty".into(),
        ));
    }

    // Stream subscriptions: the server matches stream clients by ip+pid,
    // not by URL, so url=None is correct here.
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
            guard.remove(&(item.topic.clone(), SDK_STREAM_URL.to_string()));
        }
        Ok(response)
    } else {
        Err(EventMeshError::Server {
            code: response.code.unwrap_or(-1) as i32,
            message: response
                .message
                .unwrap_or_else(|| "unsubscribe failed".into()),
        })
    }
}

async fn unsubscribe_webhook_rpc(
    client: &GrpcClient,
    config: &crate::config::GrpcClientConfig,
    subscriptions: &Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    items: Vec<SubscriptionItem>,
    url: impl Into<String>,
) -> Result<PublishResponse> {
    let url = url.into();
    if items.is_empty() {
        return Err(EventMeshError::InvalidArgument(
            "unsubscribe items must not be empty".into(),
        ));
    }

    // Webhook subscriptions: the server matches webhook clients by URL.
    // The URL must match the one used at subscribe time, otherwise the
    // WebhookTopicConfig entry is not removed and pushes continue.
    let url_ref = if url.is_empty() {
        None
    } else {
        Some(url.as_str())
    };
    let event = codec::build_subscription_event(
        config,
        EventMeshProtocolType::EventMeshMessage,
        url_ref,
        &items,
    )?;
    let resp = timed(config.timeout, client.unsubscribe(event)).await?;
    let response = codec::to_response(&resp);
    if response.is_success() {
        let mut guard = subscriptions.lock().await;
        for item in &items {
            guard.remove(&(item.topic.clone(), url.clone()));
        }
        Ok(response)
    } else {
        Err(EventMeshError::Server {
            code: response.code.unwrap_or(-1) as i32,
            message: response
                .message
                .unwrap_or_else(|| "unsubscribe failed".into()),
        })
    }
}

// ---------------------------------------------------------------------------
// Stream receive-loop driver (spawned, not public)
// ---------------------------------------------------------------------------

/// Spawn the stream receive loop as a background task.
///
/// Dispatches delivered messages to the listener **concurrently** (up to
/// [`GrpcClientConfig::max_concurrent_handlers`] in flight at once) and sends
/// back replies as each handler completes.  Concurrency is bounded by a
/// `Semaphore`: when all permits are held by in-flight handlers, the loop
/// stops pulling from the gRPC stream, which engages gRPC flow control and
/// pauses the server — this is the backpressure path.
///
/// Replies are sent in handler-completion order, **not** message-arrival
/// order.  This is a deliberate divergence from the Java SDK (which processes
/// serially and replies in order) chosen for throughput.  Each reply carries
/// the original request's attributes (see [`build_reply`]) so the broker can
/// correlate it independently of ordering.  Set
/// `max_concurrent_handlers = 1` to restore strict serial / in-order-reply
/// semantics.
///
/// On shutdown (`shutdown` token cancelled or the stream ends) the loop stops
/// accepting new messages and then **drains** all in-flight handlers to
/// completion (mirroring axum's graceful-shutdown behaviour) before clearing
/// `stream_tx` and returning.  `Drop` of the consumer aborts the driver task,
/// which drops the `JoinSet` and aborts any remaining in-flight handlers.
fn spawn_stream_driver(
    mut stream: tonic::Streaming<crate::proto_gen::PbCloudEvent>,
    reply_tx: Arc<tokio::sync::mpsc::Sender<crate::proto_gen::PbCloudEvent>>,
    listener: Arc<impl MessageListener<Message = EventMeshMessage>>,
    config: crate::config::GrpcClientConfig,
    stream_tx: StreamTx,
    shutdown: CancellationToken,
) -> JoinHandle<Result<()>> {
    tokio::spawn(async move {
        let semaphore = Arc::new(Semaphore::new(config.max_concurrent_handlers.max(1)));
        let mut join_set: JoinSet<()> = JoinSet::new();

        loop {
            tokio::select! {
                msg = stream.next() => match msg {
                    None => {
                        debug!("subscribe stream ended");
                        // Cancel the token so wait_for_shutdown() unblocks
                        // instead of waiting forever for an external signal.
                        shutdown.cancel();
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
                        // Acquire a permit (bounded concurrency) but allow
                        // shutdown to interrupt the wait.  The permit lives
                        // for the duration of the spawned handler task.
                        let permit = tokio::select! {
                            p = semaphore.clone().acquire_owned() => match p {
                                Ok(p) => p,
                                Err(_) => break, // semaphore closed
                            },
                            _ = shutdown.cancelled() => break,
                        };
                        join_set.spawn(handle_one(
                            cloud_event,
                            eventmesh_msg,
                            Arc::clone(&listener),
                            Arc::clone(&reply_tx),
                            config.clone(),
                            permit,
                        ));
                    }
                },
                // Reap completed tasks so the JoinSet does not grow unbounded.
                // Guard against busy-spinning: join_next() on an empty JoinSet
                // returns Ready(None) immediately, which would make the select!
                // fire on this branch every iteration.  The async block keeps the
                // future Pending when the set is empty, so the loop only wakes
                // when the stream delivers, a task completes, or shutdown fires.
                _ = async {
                    if !join_set.is_empty() {
                        join_set.join_next().await;
                    } else {
                        std::future::pending::<()>().await;
                    }
                } => {}
                _ = shutdown.cancelled() => {
                    debug!("subscribe stream shutting down");
                    break;
                }
            }
        }

        // Drain: wait for all in-flight handlers to finish (mirrors axum's
        // graceful-shutdown semantics).  `Drop` of the consumer aborts the
        // driver task instead, cancelling these immediately.
        while join_set.join_next().await.is_some() {}

        *stream_tx.lock().await = None;
        Ok(())
    })
}

/// Run a single message through the listener and send any reply.
///
/// The `_permit` is held for the lifetime of this future; dropping it (when
/// the future completes or is cancelled) releases the concurrency slot back
/// to the semaphore, allowing the receive loop to pull the next message.
async fn handle_one<L: MessageListener<Message = EventMeshMessage>>(
    cloud_event: crate::proto_gen::PbCloudEvent,
    eventmesh_msg: EventMeshMessage,
    listener: Arc<L>,
    reply_tx: Arc<tokio::sync::mpsc::Sender<crate::proto_gen::PbCloudEvent>>,
    config: crate::config::GrpcClientConfig,
    _permit: tokio::sync::OwnedSemaphorePermit,
) {
    match listener.handle(eventmesh_msg).await {
        Some(reply) => match build_reply(reply, &cloud_event, &config) {
            Ok(reply_event) => {
                if reply_tx.send(reply_event).await.is_err() {
                    warn!("reply channel closed; reply dropped");
                }
            }
            Err(e) => error!("failed to encode reply: {e}"),
        },
        None => { /* async ack: nothing to send back */ }
    }
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
