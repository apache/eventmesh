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
//!   dispatches delivered messages to a user-supplied [`MessageHandler`].
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
use tracing::{debug, warn};

use crate::common::constants::SDK_STREAM_URL;
use crate::common::protocol_key::ProtocolKey;
use crate::config::{ConsumerOptions, GrpcConfig, GrpcConsumerOptions};
use crate::error::{EventMeshError, Result};
use crate::message::Message;
#[cfg(test)]
use crate::model::EventMeshMessage;
use crate::model::{EventMeshProtocolType, PublishResponse};
use crate::subscription::Subscription;
use crate::transport::grpc::client::ChannelClient;
use crate::transport::grpc::codec;
use crate::transport::grpc::heartbeat::{self, StreamTx};
use crate::transport::task::BackgroundTask;
use crate::MessageHandler;

const DEFAULT_REPLY_PRODUCER_GROUP: &str = "DefaultProducerGroup";

// ---------------------------------------------------------------------------
// Shared types
// ---------------------------------------------------------------------------

/// A locally-recorded subscription entry, used by the heartbeat loop.
#[derive(Debug, Clone)]
pub(crate) struct SubscriptionEntry {
    #[allow(dead_code)]
    pub(crate) item: Subscription,
    pub(crate) url: String,
}

fn decode_message(event: &crate::proto_gen::PbCloudEvent) -> Result<Message> {
    let protocol_type = event
        .attributes
        .get(ProtocolKey::PROTOCOL_TYPE)
        .map(crate::proto_gen::attr_as_str)
        .unwrap_or_default();

    match protocol_type.as_str() {
        protocol if protocol == EventMeshProtocolType::CloudEvents.as_str() => {
            #[cfg(feature = "cloud_events")]
            return codec::to_cloudevent(event.clone()).map(Message::CloudEvent);

            #[cfg(not(feature = "cloud_events"))]
            return Err(EventMeshError::Unsupported(
                "received a CloudEvent without the 'cloud_events' feature enabled".into(),
            ));
        }
        "" => {}
        protocol if protocol == EventMeshProtocolType::EventMeshMessage.as_str() => {}
        protocol => {
            return Err(EventMeshError::Protocol {
                transport: "grpc",
                message: format!("unsupported protocoltype {protocol:?}"),
            });
        }
    }

    Ok(Message::EventMesh(codec::to_event_mesh_message(event)?))
}

fn encode_message(
    message: &Message,
    config: &GrpcConfig,
    producer_group: &str,
) -> Result<crate::proto_gen::PbCloudEvent> {
    match message {
        Message::EventMesh(message) => {
            codec::from_event_mesh_message(message, config, producer_group)
        }
        #[cfg(feature = "cloud_events")]
        Message::CloudEvent(event) => codec::from_cloudevent(event, config, producer_group),
    }
}

// ---------------------------------------------------------------------------
// Shutdown-signal helper
// ---------------------------------------------------------------------------

/// Spawn a watcher that cancels `token` when `signal` resolves.
///
/// If `signal` is `None`, nothing is spawned — the token can only be
/// cancelled by `request_shutdown()` / drop.
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

// ---------------------------------------------------------------------------
// GrpcStreamConsumer
// ---------------------------------------------------------------------------

/// gRPC stream consumer.
///
/// Opens a bidirectional gRPC stream, dispatches delivered messages to the
/// listener, and maintains a background heartbeat.  The stream, receive loop,
/// and heartbeat all run as background tokio tasks that are stopped when the
/// consumer is dropped or explicitly via [`request_shutdown`](Self::request_shutdown) /
/// [`wait_for_shutdown`](Self::wait_for_shutdown).
///
/// [`GrpcConsumerOptions::with_max_concurrent_handlers`] bounds the number of
/// messages dispatched to the listener at once. The default is one, preserving
/// the Java SDK's serial / in-order-reply semantics. Larger values allow
/// concurrent handling and can reorder replies; each reply remains
/// self-correlating through its request attributes.
///
/// Subscribe and unsubscribe RPCs can be called at any time after construction
/// — they are sent over the already-open stream (subscribe) or as independent
/// unary RPCs (unsubscribe).
///
/// # Example
///
/// ```no_run
/// # use eventmesh::{
/// #     config::{Endpoint, GrpcConfig, GrpcConsumerOptions},
/// #     GrpcChannel, GrpcStreamConsumer, Message, MessageHandler, Subscription,
/// # };
/// # struct MyListener;
/// # impl MessageHandler for MyListener {
/// #     async fn handle(&self, _: Message) -> eventmesh::Result<Option<Message>> { Ok(None) }
/// # }
/// # #[tokio::main]
/// # async fn main() -> eventmesh::Result<()> {
/// let channel = GrpcChannel::connect(
///     GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?),
/// ).await?;
/// let consumer = GrpcStreamConsumer::open(
///     channel,
///     GrpcConsumerOptions::new("consumer-group"),
///     [Subscription::new("t")],
///     MyListener,
/// ).await?;
/// consumer.join().await?;
/// # Ok(())
/// # }
/// ```
pub struct GrpcStreamConsumer<L: MessageHandler> {
    client: ChannelClient,
    config: GrpcConfig,
    options: GrpcConsumerOptions,
    subscriptions: Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    _listener: std::marker::PhantomData<Arc<L>>,
    shutdown: CancellationToken,
    heartbeat_handle: Mutex<BackgroundTask<()>>,
    stream_tx: StreamTx,
    driver_handle: Mutex<BackgroundTask<Result<()>>>,
}

impl<L: MessageHandler> GrpcStreamConsumer<L> {
    /// Open a bidirectional stream subscription and spawn the receive loop +
    /// heartbeat as background tasks.
    ///
    /// `items` are sent as the first message on the stream (the subscription
    /// request).  `shutdown_signal` is an optional future whose resolution
    /// triggers graceful shutdown of the stream and heartbeat.  When omitted,
    /// shutdown can only be initiated by [`request_shutdown`](Self::request_shutdown) or drop.
    ///
    /// Both current-thread and multi-thread Tokio runtimes are supported.
    /// The channel's owning runtime must keep running to drive the stream,
    /// heartbeat, and handler tasks. Stream establishment waits at most
    /// 15 seconds for the server's response headers.
    pub async fn subscribe_stream(
        client: ChannelClient,
        config: GrpcConfig,
        options: GrpcConsumerOptions,
        listener: L,
        items: Vec<Subscription>,
        shutdown_signal: Option<impl Future<Output = ()> + Send + 'static>,
    ) -> Result<Self> {
        options.validate()?;
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }

        let subscriptions = Arc::new(Mutex::new(HashMap::new()));
        let shutdown = CancellationToken::new();
        let stream_tx: StreamTx = Arc::new(Mutex::new(None));
        let listener = Arc::new(listener);

        // Signal watcher.
        spawn_signal_watcher(shutdown_signal, shutdown.clone());

        // Build the subscription event (first stream message).
        let event = codec::build_subscription_event(
            &config,
            options.consumer().group(),
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
            options.consumer().clone(),
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
            options.max_concurrent_handlers(),
            stream_tx.clone(),
            shutdown.clone(),
        );

        Ok(Self {
            client,
            config,
            options,
            subscriptions,
            _listener: std::marker::PhantomData,
            shutdown,
            heartbeat_handle: Mutex::new(BackgroundTask::new(heartbeat_handle)),
            stream_tx,
            driver_handle: Mutex::new(BackgroundTask::new(driver_handle)),
        })
    }

    /// Subscribe to additional topics over the already-open stream.
    ///
    /// The subscription CloudEvent is sent through the stream's request
    /// channel. Returns an error if the stream is no longer active, is shutting
    /// down, or remains backpressured beyond the configured timeout.
    pub async fn subscribe(&self, items: Vec<Subscription>) -> Result<()> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }
        let event = codec::build_subscription_event(
            &self.config,
            self.options.consumer().group(),
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
                    self.config.request_timeout(),
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
                        Err(EventMeshError::Timeout(self.config.request_timeout()))
                    }
                    heartbeat::OperationOutcome::Cancelled => Err(EventMeshError::ChannelClosed(
                        "stream is shutting down".into(),
                    )),
                }
            }
            None => Err(EventMeshError::ChannelClosed("stream is not active".into())),
        }
    }

    /// Unsubscribe stream-mode topics (registered via `subscribe_stream` or
    /// `subscribe`).
    ///
    /// This is an independent unary RPC — it is **not** sent over the open
    /// stream. The server matches stream clients by IP + PID, so no URL is
    /// needed.
    ///
    /// Known limitation: the Java Runtime closes the shared emitter even for
    /// partial unsubscribe. Remaining topics are not replayed on a new stream;
    /// EOF stops the driver and heartbeat. See the public unsubscribe rustdoc.
    pub async fn unsubscribe_stream(&self, items: Vec<Subscription>) -> Result<PublishResponse> {
        unsubscribe_stream_rpc(
            &self.client,
            &self.config,
            self.options.consumer(),
            &self.subscriptions,
            items,
        )
        .await
    }

    /// Signal the stream driver and heartbeat task to stop.
    pub fn request_shutdown(&self) {
        self.shutdown.cancel();
    }

    /// Block until the shutdown signal fires or the stream / heartbeat tasks
    /// exit on their own, then await their clean exit.
    ///
    /// If no shutdown signal was provided at construction time, this blocks
    /// until the tasks exit naturally (e.g. the server closes the stream).
    pub async fn wait_for_shutdown(&self) -> Result<()> {
        let mut driver = self.driver_handle.lock().await;
        driver.wait().await;
        self.shutdown.cancel();
        let mut heartbeat = self.heartbeat_handle.lock().await;
        heartbeat.wait().await;
        // No awaits after consuming either result: cancellation while waiting
        // for heartbeat cleanup must not discard a driver failure.
        let driver_result = driver
            .take_result()
            .unwrap_or(Ok(Ok(())))
            .map_err(|error| {
                EventMeshError::ChannelClosed(format!(
                    "gRPC consumer driver task panicked: {error}"
                ))
            })?;
        let heartbeat_result = heartbeat.take_result().unwrap_or(Ok(())).map_err(|error| {
            EventMeshError::ChannelClosed(format!("gRPC consumer heartbeat task panicked: {error}"))
        });
        driver_result.and(heartbeat_result)
    }
}

impl<L: MessageHandler> Drop for GrpcStreamConsumer<L> {
    fn drop(&mut self) {
        self.shutdown.cancel();
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
/// # use eventmesh::{
/// #     config::{ConsumerOptions, Endpoint, GrpcConfig},
/// #     GrpcChannel, GrpcWebhookConsumer, Subscription,
/// # };
/// # #[tokio::main]
/// # async fn main() -> eventmesh::Result<()> {
/// let channel = GrpcChannel::connect(
///     GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?),
/// ).await?;
/// let consumer = GrpcWebhookConsumer::new(
///     channel,
///     ConsumerOptions::new("consumer-group"),
/// ).await?;
/// consumer.subscribe(
///     [Subscription::new("t")],
///     "http://127.0.0.1:8080/cb",
/// ).await?;
/// consumer.join().await?;
/// # Ok(())
/// # }
/// ```
pub struct GrpcWebhookConsumer {
    client: ChannelClient,
    config: GrpcConfig,
    options: ConsumerOptions,
    subscriptions: Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    shutdown: CancellationToken,
    heartbeat_handle: Mutex<BackgroundTask<()>>,
}

impl GrpcWebhookConsumer {
    /// Create a webhook consumer.  Spawns a background heartbeat task.
    ///
    /// `shutdown_signal` is an optional future whose resolution triggers
    /// graceful shutdown of the heartbeat.  When omitted, shutdown can only be
    /// initiated by [`request_shutdown`](Self::request_shutdown) or drop.
    pub async fn new(
        client: ChannelClient,
        config: GrpcConfig,
        options: ConsumerOptions,
        shutdown_signal: Option<impl Future<Output = ()> + Send + 'static>,
    ) -> Result<Self> {
        options.validate()?;
        let subscriptions = Arc::new(Mutex::new(HashMap::new()));
        let shutdown = CancellationToken::new();

        spawn_signal_watcher(shutdown_signal, shutdown.clone());

        let heartbeat_handle = heartbeat::spawn(
            client.clone(),
            config.clone(),
            options.clone(),
            Arc::clone(&subscriptions),
            // Webhook mode has no stream — stream_tx is always None.
            Arc::new(Mutex::new(None)),
            shutdown.clone(),
        );

        Ok(Self {
            client,
            config,
            options,
            subscriptions,
            shutdown,
            heartbeat_handle: Mutex::new(BackgroundTask::new(heartbeat_handle)),
        })
    }

    #[cfg(test)]
    pub(crate) fn client(&self) -> &ChannelClient {
        &self.client
    }

    /// Subscribe via webhook: the server POSTs delivered events to `url`.
    pub async fn subscribe_webhook(
        &self,
        items: Vec<Subscription>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        subscribe_webhook_rpc(
            &self.client,
            &self.config,
            &self.options,
            &self.subscriptions,
            items,
            url,
        )
        .await
    }

    /// Unsubscribe webhook topics.
    ///
    /// `url` must be the same webhook URL passed to `subscribe_webhook`.
    /// The server matches webhook clients by URL — omitting or mismatching
    /// it leaves a ghost subscription that continues to receive pushes.
    pub async fn unsubscribe_webhook(
        &self,
        items: Vec<Subscription>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        unsubscribe_webhook_rpc(
            &self.client,
            &self.config,
            &self.options,
            &self.subscriptions,
            items,
            url,
        )
        .await
    }

    /// Signal the heartbeat task to stop.
    pub fn request_shutdown(&self) {
        self.shutdown.cancel();
    }

    /// Block until the shutdown signal fires or the heartbeat task exits.
    pub async fn wait_for_shutdown(&self) -> Result<()> {
        let mut task = self.heartbeat_handle.lock().await;
        task.wait().await;
        self.shutdown.cancel();
        task.take_result().unwrap_or(Ok(())).map_err(|error| {
            EventMeshError::ChannelClosed(format!("gRPC webhook heartbeat task panicked: {error}"))
        })
    }
}

impl Drop for GrpcWebhookConsumer {
    fn drop(&mut self) {
        self.shutdown.cancel();
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
    client: &ChannelClient,
    config: &GrpcConfig,
    consumer: &ConsumerOptions,
    subscriptions: &Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    items: Vec<Subscription>,
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
        consumer.group(),
        EventMeshProtocolType::EventMeshMessage,
        Some(&url),
        &items,
    )?;
    let resp = timed(config.request_timeout(), client.subscribe_webhook(event)).await?;
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
    client: &ChannelClient,
    config: &GrpcConfig,
    consumer: &ConsumerOptions,
    subscriptions: &Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    items: Vec<Subscription>,
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
        consumer.group(),
        EventMeshProtocolType::EventMeshMessage,
        None,
        &items,
    )?;
    let resp = timed(config.request_timeout(), client.unsubscribe(event)).await?;
    let response = codec::to_response(&resp);
    if response.is_success() {
        // Only the requested topics are removed locally, although the Java
        // Runtime closes their shared stream. Remaining entries do not imply
        // active delivery; no stream recreation/replay is implemented here.
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
    client: &ChannelClient,
    config: &GrpcConfig,
    consumer: &ConsumerOptions,
    subscriptions: &Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    items: Vec<Subscription>,
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
        consumer.group(),
        EventMeshProtocolType::EventMeshMessage,
        url_ref,
        &items,
    )?;
    let resp = timed(config.request_timeout(), client.unsubscribe(event)).await?;
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
/// the configured maximum number of handlers in flight at once) and sends
/// back replies as each handler completes.  Concurrency is bounded by a
/// `Semaphore`: when all permits are held by in-flight handlers, the loop
/// stops pulling from the gRPC stream, which engages gRPC flow control and
/// pauses the server — this is the backpressure path.
///
/// With a concurrency bound greater than one, replies are sent in
/// handler-completion order rather than message-arrival order. Each reply
/// carries the original request's attributes (see [`build_reply`]) so the
/// broker can correlate it independently of ordering. The default bound of one
/// preserves strict serial / in-order-reply semantics.
///
/// On shutdown (`shutdown` token cancelled or the stream ends) the loop stops
/// accepting new messages and then **drains** all in-flight handlers to
/// completion (mirroring axum's graceful-shutdown behaviour) before clearing
/// `stream_tx` and returning.  `Drop` of the consumer aborts the driver task,
/// which drops the `JoinSet` and aborts any remaining in-flight handlers.
fn spawn_stream_driver<L>(
    mut stream: tonic::Streaming<crate::proto_gen::PbCloudEvent>,
    reply_tx: Arc<tokio::sync::mpsc::Sender<crate::proto_gen::PbCloudEvent>>,
    listener: Arc<L>,
    config: GrpcConfig,
    max_concurrent_handlers: usize,
    stream_tx: StreamTx,
    shutdown: CancellationToken,
) -> JoinHandle<Result<()>>
where
    L: MessageHandler,
{
    tokio::spawn(async move {
        let semaphore = Arc::new(Semaphore::new(max_concurrent_handlers));
        let mut join_set: JoinSet<Result<()>> = JoinSet::new();
        let mut terminal_error = None;

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
                        terminal_error = Some(EventMeshError::from(status));
                        shutdown.cancel();
                        break;
                    }
                    Some(Ok(cloud_event)) => {
                        if codec::get_seq_num(&cloud_event).is_empty() {
                            // Known limitation: this also ignores subscription
                            // rejection statuscode/responsemessage attributes.
                            // Normal EOF then lets join() return success. The
                            // Java SDK likewise does not propagate these
                            // statuses; retained behavior is documented in
                            // GrpcStreamConsumer::open and ARCHITECTURE.md.
                            debug!("skipping control frame (no seqnum)");
                            continue;
                        }
                        let message = match decode_message(&cloud_event) {
                            Ok(message) => message,
                            Err(error) => {
                                terminal_error = Some(error);
                                shutdown.cancel();
                                break;
                            }
                        };
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
                            message,
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
                completed = async {
                    if !join_set.is_empty() {
                        join_set.join_next().await
                    } else {
                        std::future::pending().await
                    }
                } => {
                    match completed {
                        Some(Ok(Ok(()))) | None => {}
                        Some(Ok(Err(error))) => {
                            terminal_error = Some(error);
                            shutdown.cancel();
                            break;
                        }
                        Some(Err(error)) => {
                            terminal_error = Some(EventMeshError::ChannelClosed(format!(
                                "gRPC message handler task panicked: {error}"
                            )));
                            shutdown.cancel();
                            break;
                        }
                    }
                }
                _ = shutdown.cancelled() => {
                    debug!("subscribe stream shutting down");
                    break;
                }
            }
        }

        // Drain: wait for all in-flight handlers to finish (mirrors axum's
        // graceful-shutdown semantics).  `Drop` of the consumer aborts the
        // driver task instead, cancelling these immediately.
        while let Some(completed) = join_set.join_next().await {
            if terminal_error.is_none() {
                terminal_error = match completed {
                    Ok(Ok(())) => None,
                    Ok(Err(error)) => Some(error),
                    Err(error) => Some(EventMeshError::ChannelClosed(format!(
                        "gRPC message handler task panicked: {error}"
                    ))),
                };
            }
        }

        *stream_tx.lock().await = None;
        terminal_error.map_or(Ok(()), Err)
    })
}

/// Run a single message through the listener and send any reply.
///
/// The `_permit` is held for the lifetime of this future; dropping it (when
/// the future completes or is cancelled) releases the concurrency slot back
/// to the semaphore, allowing the receive loop to pull the next message.
async fn handle_one<L: MessageHandler>(
    cloud_event: crate::proto_gen::PbCloudEvent,
    message: Message,
    listener: Arc<L>,
    reply_tx: Arc<tokio::sync::mpsc::Sender<crate::proto_gen::PbCloudEvent>>,
    config: GrpcConfig,
    _permit: tokio::sync::OwnedSemaphorePermit,
) -> Result<()> {
    match listener.handle(message).await? {
        Some(reply) => {
            let reply_event = build_reply(&reply, &cloud_event, &config)?;
            reply_tx
                .send(reply_event)
                .await
                .map_err(|_| EventMeshError::ChannelClosed("gRPC reply channel closed".into()))?;
        }
        None => { /* async ack: nothing to send back */ }
    }
    Ok(())
}

/// Build a reply CloudEvent (used by the stream receive loop when the listener
/// returns `Some(message)`).
///
/// Mirrors the Java SDK's `SubStreamHandler.buildReplyMessage`: the incoming
/// request's attributes are carried over into the reply so the broker can
/// correlate the reply with the original request.  The reply's own attributes
/// take precedence.
pub(crate) fn build_reply(
    reply: &Message,
    request: &crate::proto_gen::PbCloudEvent,
    config: &GrpcConfig,
) -> Result<crate::proto_gen::PbCloudEvent> {
    let mut event = encode_message(reply, config, DEFAULT_REPLY_PRODUCER_GROUP)?;
    for (key, value) in &request.attributes {
        if crate::model::delivery::is_transport_property(key) {
            continue;
        }
        event
            .attributes
            .entry(key.clone())
            .or_insert_with(|| value.clone());
    }
    codec::mark_as_reply(&mut event);
    Ok(event)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn config() -> GrpcConfig {
        GrpcConfig::new(crate::config::Endpoint::new("127.0.0.1", 10_205).unwrap())
    }

    #[test]
    fn public_message_rejects_unknown_protocol() {
        let mut wire = codec::from_event_mesh_message(
            &EventMeshMessage::new("orders", "created").unwrap(),
            &config(),
            "producer",
        )
        .unwrap();
        wire.attributes.insert(
            ProtocolKey::PROTOCOL_TYPE.into(),
            crate::proto_gen::attr_str("openmessage"),
        );

        let error = decode_message(&wire).unwrap_err();
        assert!(matches!(
            error,
            EventMeshError::Protocol {
                transport: "grpc",
                ..
            }
        ));
    }

    #[test]
    fn native_reply_restores_routing_without_inheriting_sender_credentials() {
        let mut request = codec::from_event_mesh_message(
            &EventMeshMessage::new("orders", "request").unwrap(),
            &config(),
            "producer",
        )
        .unwrap();
        for (key, value) in [
            ("cluster", "request-cluster"),
            ("correlation99id", "request-id"),
            ("reply99to99client", "request-client"),
            ("req0sys", "request-system"),
            ("token", "sender-token"),
        ] {
            request
                .attributes
                .insert(key.into(), crate::proto_gen::attr_str(value));
        }
        let decoded = decode_message(&request).unwrap().into_event_mesh().unwrap();
        assert!(decoded.properties().is_empty());
        assert_eq!(
            decoded
                .delivery_context()
                .unwrap()
                .attribute("correlation99id"),
            Some("request-id")
        );

        let reply = build_reply(
            &Message::from(EventMeshMessage::new("orders", "reply").unwrap()),
            &request,
            &config(),
        )
        .unwrap();
        for key in ["cluster", "correlation99id", "reply99to99client", "req0sys"] {
            assert_eq!(reply.attributes.get(key), request.attributes.get(key));
        }
        assert!(!reply.attributes.contains_key("token"));
    }

    type TestHandler = fn(Message) -> std::future::Ready<Result<Option<Message>>>;

    fn consumer_with_tasks(
        driver: JoinHandle<Result<()>>,
        heartbeat: JoinHandle<()>,
    ) -> GrpcStreamConsumer<TestHandler> {
        let config = config();
        GrpcStreamConsumer {
            client: ChannelClient::connect_lazy(&config).unwrap(),
            config,
            options: GrpcConsumerOptions::new("consumer"),
            subscriptions: Arc::new(Mutex::new(HashMap::new())),
            _listener: std::marker::PhantomData,
            shutdown: CancellationToken::new(),
            heartbeat_handle: Mutex::new(BackgroundTask::new(heartbeat)),
            stream_tx: Arc::new(Mutex::new(None)),
            driver_handle: Mutex::new(BackgroundTask::new(driver)),
        }
    }

    #[tokio::test(start_paused = true)]
    async fn cancelled_stream_join_preserves_driver_failure() {
        let (release, released) = tokio::sync::oneshot::channel::<()>();
        let consumer = consumer_with_tasks(
            tokio::spawn(async move {
                released.await.unwrap();
                Err(EventMeshError::Server {
                    code: 17,
                    message: "driver failure".into(),
                })
            }),
            tokio::spawn(async {}),
        );
        for _ in 0..2 {
            assert!(
                tokio::time::timeout(Duration::from_secs(1), consumer.wait_for_shutdown())
                    .await
                    .is_err()
            );
            consumer.request_shutdown();
        }
        release.send(()).unwrap();
        let result = tokio::time::timeout(Duration::from_secs(1), consumer.wait_for_shutdown())
            .await
            .unwrap();
        assert!(matches!(
            result,
            Err(EventMeshError::Server { code: 17, .. })
        ));
    }

    #[tokio::test(start_paused = true)]
    async fn cancelled_stream_join_during_cleanup_preserves_driver_panic() {
        let (release, released) = tokio::sync::oneshot::channel::<()>();
        let consumer = consumer_with_tasks(
            tokio::spawn(async {
                panic!("driver regression");
            }),
            tokio::spawn(async move {
                released.await.unwrap();
            }),
        );
        for _ in 0..2 {
            assert!(
                tokio::time::timeout(Duration::from_secs(1), consumer.wait_for_shutdown())
                    .await
                    .is_err()
            );
            assert!(consumer.shutdown.is_cancelled());
        }
        release.send(()).unwrap();
        let result = tokio::time::timeout(Duration::from_secs(1), consumer.wait_for_shutdown())
            .await
            .unwrap();
        assert!(matches!(result, Err(EventMeshError::ChannelClosed(message))
            if message.contains("driver regression")));
    }

    #[tokio::test(start_paused = true)]
    async fn cancelled_webhook_join_preserves_heartbeat_panic() {
        let config = config();
        let consumer = GrpcWebhookConsumer::new(
            ChannelClient::connect_lazy(&config).unwrap(),
            config,
            ConsumerOptions::new("consumer"),
            None::<std::future::Ready<()>>,
        )
        .await
        .unwrap();
        let (release, released) = tokio::sync::oneshot::channel::<()>();
        *consumer.heartbeat_handle.lock().await = BackgroundTask::new(tokio::spawn(async move {
            released.await.unwrap();
            panic!("heartbeat regression");
        }));
        for _ in 0..2 {
            assert!(
                tokio::time::timeout(Duration::from_secs(1), consumer.wait_for_shutdown())
                    .await
                    .is_err()
            );
            consumer.request_shutdown();
        }
        release.send(()).unwrap();
        let result = tokio::time::timeout(Duration::from_secs(1), consumer.wait_for_shutdown())
            .await
            .unwrap();
        assert!(matches!(result, Err(EventMeshError::ChannelClosed(message))
            if message.contains("heartbeat regression")));
    }

    #[tokio::test]
    async fn webhook_shutdown_then_join_preserves_task_panic() {
        let config = config();
        let consumer = GrpcWebhookConsumer::new(
            ChannelClient::connect_lazy(&config).unwrap(),
            config,
            ConsumerOptions::new("consumer"),
            None::<std::future::Ready<()>>,
        )
        .await
        .unwrap();
        *consumer.heartbeat_handle.lock().await = BackgroundTask::new(tokio::spawn(async {
            panic!("heartbeat panic");
        }));

        consumer.request_shutdown();
        let error = consumer.wait_for_shutdown().await.unwrap_err();
        assert!(matches!(error, EventMeshError::ChannelClosed(_)));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn public_message_preserves_cloud_event_protocol_and_reply_metadata() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("event-1")
            .source("urn:test")
            .ty("orders.created")
            .subject("orders")
            .data("application/json", serde_json::json!({"status": "created"}))
            .build()
            .expect("build event");
        let original = Message::CloudEvent(event);
        let mut request =
            encode_message(&original, &config(), "producer").expect("encode CloudEvent request");
        request.attributes.insert(
            "correlation-id".into(),
            crate::proto_gen::attr_str("request-7"),
        );

        let decoded = decode_message(&request).expect("decode CloudEvent request");
        assert!(matches!(decoded, Message::CloudEvent(_)));
        let reply = build_reply(&original, &request, &config()).expect("encode reply");
        assert_eq!(
            reply
                .attributes
                .get(ProtocolKey::PROTOCOL_TYPE)
                .map(crate::proto_gen::attr_as_str)
                .as_deref(),
            Some(EventMeshProtocolType::CloudEvents.as_str())
        );
        assert_eq!(
            reply
                .attributes
                .get("correlation-id")
                .map(crate::proto_gen::attr_as_str)
                .as_deref(),
            Some("request-7")
        );
    }
}
