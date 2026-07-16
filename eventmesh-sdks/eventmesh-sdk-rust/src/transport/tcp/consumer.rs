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

//! TCP consumer.
//!
//! [`TcpConsumer`] is constructed via [`TcpConsumer::connect`], which opens a
//! TCP connection, performs the HELLO handshake (role = sub), sends
//! `LISTEN_REQUEST`, and spawns the receive loop + heartbeat as background
//! tasks.  Subscribe and unsubscribe RPCs can be called at any time after
//! construction — they are sent over the same connection via `conn.io()`.
//!
//! # Example
//!
//! ```ignore
//! use eventmesh::{
//!     config::TcpClientConfig, tcp::TcpConsumer,
//!     model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
//!     MessageListener,
//! };
//!
//! struct MyListener;
//! impl MessageListener for MyListener {
//!     type Message = EventMeshMessage;
//!     async fn handle(&self, msg: EventMeshMessage) -> Option<EventMeshMessage> {
//!         println!("received: {:?}", msg.content);
//!         None
//!     }
//! }
//!
//! #[tokio::main]
//! async fn main() -> eventmesh::Result<()> {
//!     let config = TcpClientConfig::builder()
//!         .server_addr("127.0.0.1").server_port(10000)
//!         .consumer_group("g")
//!         .build();
//!     let consumer = TcpConsumer::connect(
//!         config,
//!         MyListener,
//!         async { tokio::signal::ctrl_c().await.ok(); },
//!     ).await?;
//!     consumer.subscribe(vec![SubscriptionItem::new(
//!         "t", SubscriptionMode::CLUSTERING, SubscriptionType::ASYNC,
//!     )]).await?;
//!     consumer.wait_for_shutdown().await;
//!     Ok(())
//! }
//! ```

use std::future::Future;
use std::sync::Arc;

use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::{debug, info, warn};

use crate::config::TcpClientConfig;
use crate::error::{EventMeshError, Result};
use crate::message::Message;
use crate::model::{EventMeshMessage, OpenMessage, PublishResponse, SubscriptionItem};
use crate::transport::tcp::connection::TcpConnection;
use crate::transport::tcp::frame::{Command, Package, PackageBody, RedirectInfo, UserAgent};
use crate::transport::tcp::message;
use crate::MessageListener;

/// A message representation supported by the TCP consumer receive loop.
///
/// This is implemented by the SDK's native EventMeshMessage and, with the
/// `cloud_events` feature, native CloudEvents. It keeps TCP wire codecs out
/// of the normal producer and consumer APIs.
pub trait TcpMessage: Clone + Send + 'static {
    #[doc(hidden)]
    fn decode_tcp(pkg: &Package) -> Option<Self>
    where
        Self: Sized;

    #[doc(hidden)]
    fn encode_tcp_reply(&self) -> Result<Package>;

    /// Copy request routing and correlation metadata into a fresh reply when
    /// the runtime has sent a request/reply delivery.
    #[doc(hidden)]
    fn inherit_request_metadata(&mut self, request: &Self);
}

impl TcpMessage for EventMeshMessage {
    fn decode_tcp(pkg: &Package) -> Option<Self> {
        if message::is_cloudevents(pkg) {
            #[cfg(feature = "cloud_events")]
            return message::parse_cloud_event(&pkg.body)
                .map(|event| message::cloud_event_to_message(&event));
            #[cfg(not(feature = "cloud_events"))]
            return None;
        }
        message::parse_message(&pkg.body)
    }

    fn encode_tcp_reply(&self) -> Result<Package> {
        message::build_message_package(self, Command::ResponseToServer)
    }

    fn inherit_request_metadata(&mut self, request: &Self) {
        for (key, value) in &request.props {
            self.props
                .entry(key.clone())
                .or_insert_with(|| value.clone());
        }
    }
}

impl TcpMessage for Message {
    fn decode_tcp(pkg: &Package) -> Option<Self> {
        if message::is_cloudevents(pkg) {
            #[cfg(feature = "cloud_events")]
            return message::parse_cloud_event(&pkg.body).map(Self::CloudEvent);
            #[cfg(not(feature = "cloud_events"))]
            return None;
        }

        let native = message::parse_message(&pkg.body)?;
        if message::is_open_message(pkg) {
            Some(Self::Open(OpenMessage::from_event_mesh_message(native)))
        } else {
            Some(Self::EventMesh(native))
        }
    }

    fn encode_tcp_reply(&self) -> Result<Package> {
        match self {
            Self::EventMesh(message) => {
                message::build_message_package(message, Command::ResponseToServer)
            }
            Self::Open(message) => {
                message::build_open_message_package(message, Command::ResponseToServer)
            }
            #[cfg(feature = "cloud_events")]
            Self::CloudEvent(event) => {
                message::build_cloud_event_package(event, Command::ResponseToServer)
            }
        }
    }

    fn inherit_request_metadata(&mut self, request: &Self) {
        #[cfg(feature = "cloud_events")]
        if let (Self::CloudEvent(reply), Self::CloudEvent(request)) = (&mut *self, request) {
            for (key, value) in request.iter_extensions() {
                if reply.extension(key).is_none() {
                    reply.set_extension(key, value.clone());
                }
            }
            return;
        }

        // TCP correlation metadata has no lossless slot in OpenMessage.
        // Normalize non-CloudEvent replies to EventMeshMessage before merging
        // request metadata, matching the previous public adapter's behavior.
        let mut reply = match self.clone() {
            Self::EventMesh(message) => message,
            Self::Open(message) => message.to_event_mesh_message(),
            #[cfg(feature = "cloud_events")]
            Self::CloudEvent(event) => message::cloud_event_to_message(&event),
        };
        let request = match request.clone() {
            Self::EventMesh(message) => message,
            Self::Open(message) => message.to_event_mesh_message(),
            #[cfg(feature = "cloud_events")]
            Self::CloudEvent(event) => message::cloud_event_to_message(&event),
        };
        <EventMeshMessage as TcpMessage>::inherit_request_metadata(&mut reply, &request);
        *self = Self::EventMesh(reply);
    }
}

#[cfg(feature = "cloud_events")]
impl TcpMessage for cloudevents::Event {
    fn decode_tcp(pkg: &Package) -> Option<Self> {
        if message::is_cloudevents(pkg) {
            message::parse_cloud_event(&pkg.body)
        } else {
            message::parse_message(&pkg.body)
                .and_then(|message| message::message_to_cloud_event(&message).ok())
        }
    }

    fn encode_tcp_reply(&self) -> Result<Package> {
        message::build_cloud_event_package(self, Command::ResponseToServer)
    }

    fn inherit_request_metadata(&mut self, request: &Self) {
        for (key, value) in request.iter_extensions() {
            if self.extension(key).is_none() {
                self.set_extension(key, value.clone());
            }
        }
    }
}

/// Why the TCP consumer's receive loop stopped.
///
/// Returned by [`TcpConsumer::wait_for_shutdown`] so the caller can react to
/// server-driven events like redirect.
#[derive(Debug, Clone)]
pub enum ShutdownReason {
    /// The shutdown token was cancelled — either by the user-supplied
    /// shutdown signal, an explicit `shutdown()` call, or the driver itself
    /// after a clean exit.
    Cancelled,
    /// The server sent `REDIRECT_TO_CLIENT` with a target address. The caller
    /// should connect to the advertised EventMesh node.
    ///
    /// (The Java SDK ignores this frame entirely — it falls into the `default`
    /// branch of the handler switch and is logged as a warning. The Rust SDK
    /// surfaces it so the caller can act on it.)
    Redirect(RedirectInfo),
    /// The inbound channel closed (the connection was lost and not
    /// re-established, or the consumer was dropped).
    ChannelClosed,
    /// The receive-loop driver task exited abnormally (panic or error).
    Error(String),
}

/// Result of dispatching a single inbound package.
enum InboundResult {
    /// Keep the receive loop running.
    Continue,
    /// Stop the loop and report a redirect target to the caller.
    Redirect(RedirectInfo),
    /// Stop the loop (parse failure, unknown command, etc.).
    Stop,
}

/// TCP-based consumer, generic over the user's [`MessageListener`] type.
///
/// Created via [`TcpConsumer::connect`], which opens a TCP connection, performs
/// the HELLO handshake (role = sub), sends `LISTEN_REQUEST`, and spawns the
/// receive loop + heartbeat as background tasks.
///
/// Subscribe and unsubscribe RPCs can be called at any time after construction.
/// The background tasks are stopped when the consumer is dropped or explicitly
/// via [`shutdown`](Self::shutdown) / [`wait_for_shutdown`](Self::wait_for_shutdown).
pub struct TcpConsumer<L: MessageListener> {
    conn: Arc<TcpConnection>,
    config: TcpClientConfig,
    _listener: std::marker::PhantomData<Arc<L>>,
    shutdown: CancellationToken,
    subscriptions: Arc<Mutex<Vec<SubscriptionItem>>>,
    driver_handle: Mutex<Option<JoinHandle<Result<()>>>>,
    /// Filled by the driver before it exits, so `wait_for_shutdown` can
    /// return a structured [`ShutdownReason`].
    shutdown_reason: Arc<Mutex<Option<ShutdownReason>>>,
}

/// TCP consumer facade for [`OpenMessage`].
///
/// EventMesh runtimes route TCP messages through the native EventMeshMessage
/// envelope. This facade converts at the SDK boundary, giving OpenMessaging
/// applications a typed listener while retaining interoperable wire data.
pub struct TcpOpenMessageConsumer<L: MessageListener<Message = OpenMessage>> {
    inner: TcpConsumer<OpenMessageListener<L>>,
}

struct OpenMessageListener<L> {
    listener: Arc<L>,
}

impl<L: MessageListener<Message = OpenMessage>> MessageListener for OpenMessageListener<L> {
    type Message = EventMeshMessage;

    async fn handle(&self, message: EventMeshMessage) -> Result<Option<EventMeshMessage>> {
        Ok(self
            .listener
            .handle(OpenMessage::from_event_mesh_message(message))
            .await?
            .map(|reply| reply.to_event_mesh_message()))
    }
}

impl<L: MessageListener<Message = OpenMessage>> TcpOpenMessageConsumer<L> {
    /// Connect and start an OpenMessaging-style TCP consumer.
    pub async fn connect(
        config: TcpClientConfig,
        listener: L,
        shutdown_signal: Option<impl Future<Output = ()> + Send + 'static>,
    ) -> Result<Self> {
        let listener = OpenMessageListener {
            listener: Arc::new(listener),
        };
        Ok(Self {
            inner: TcpConsumer::connect(config, listener, shutdown_signal).await?,
        })
    }

    /// Subscribe to additional topics.
    pub async fn subscribe(&self, items: &[SubscriptionItem]) -> Result<()> {
        self.inner.subscribe(items).await
    }

    /// Unsubscribe from topics.
    pub async fn unsubscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        self.inner.unsubscribe(items).await
    }

    /// Shut down the consumer and its background tasks.
    pub async fn shutdown(&self) {
        self.inner.shutdown().await;
    }

    /// Wait for the consumer to stop.
    pub async fn wait_for_shutdown(&self) -> ShutdownReason {
        self.inner.wait_for_shutdown().await
    }
}

/// Native CloudEvents TCP consumer.
///
/// Unlike the EventMeshMessage consumer, this type passes the TCP
/// CloudEvents JSON body directly to a `MessageListener<cloudevents::Event>`
/// and serializes listener replies back as CloudEvents.
#[cfg(feature = "cloud_events")]
pub type TcpCloudEventConsumer<L> = TcpConsumer<L>;

impl<L> TcpConsumer<L>
where
    L: MessageListener,
    L::Message: TcpMessage,
{
    /// Connect to the EventMesh TCP endpoint, perform the HELLO handshake
    /// (role = sub), send `LISTEN_REQUEST`, and spawn the receive loop.
    ///
    /// `shutdown_signal` is an optional future whose resolution triggers
    /// graceful shutdown.  When omitted, shutdown can only be initiated by
    /// [`shutdown`](Self::shutdown) or drop.
    ///
    /// The reconnect policy from the config controls automatic reconnection
    /// after I/O failures (enabled by default).  When a reconnect succeeds,
    /// the receive loop automatically replays all subscriptions and re-issues
    /// `LISTEN_REQUEST`.
    pub async fn connect(
        config: TcpClientConfig,
        listener: L,
        shutdown_signal: Option<impl Future<Output = ()> + Send + 'static>,
    ) -> Result<Self> {
        let user_agent = UserAgent::from_identity(&config.identity, config.server_port, "sub");
        let conn = TcpConnection::connect(
            &config.server_addr,
            config.server_port,
            &user_agent,
            config.heartbeat_interval,
            config.timeout,
            config.reconnect.clone(),
        )
        .await?;
        let conn = Arc::new(conn);

        let shutdown = CancellationToken::new();
        let subscriptions = Arc::new(Mutex::new(Vec::new()));
        let shutdown_reason = Arc::new(Mutex::new(None));
        let listener = Arc::new(listener);

        // Signal watcher.
        if let Some(signal) = shutdown_signal {
            let token = shutdown.clone();
            tokio::spawn(async move {
                tokio::select! {
                    _ = signal => token.cancel(),
                    _ = token.cancelled() => {}
                }
            });
        }

        // Send LISTEN_REQUEST and verify it succeeds.
        let listen_pkg = message::listen();
        let listen_resp = conn.io(listen_pkg, config.timeout).await?;
        let listen_status = message::response_from_pkg(&listen_resp);
        if !listen_status.is_success() {
            return Err(EventMeshError::Server {
                code: listen_status.code.unwrap_or(-1) as i32,
                message: listen_status
                    .message
                    .unwrap_or_else(|| "listen failed".into()),
            });
        }
        debug!("LISTEN ok, entering receive loop");

        // Take the inbound receiver (only available once).
        let inbound_rx = conn
            .take_inbound_rx()
            .await
            .ok_or_else(|| EventMeshError::Tcp("inbound receiver already taken".into()))?;

        // Take the reconnect-event receiver.
        let reconnect_rx = conn.take_reconnect_rx().await;

        // Spawn the receive-loop driver.
        let driver_handle = spawn_driver(
            Arc::clone(&conn),
            inbound_rx,
            reconnect_rx,
            Arc::clone(&listener),
            config.clone(),
            Arc::clone(&subscriptions),
            shutdown.clone(),
            Arc::clone(&shutdown_reason),
        );

        Ok(Self {
            conn,
            config,
            _listener: std::marker::PhantomData,
            shutdown,
            subscriptions,
            driver_handle: Mutex::new(Some(driver_handle)),
            shutdown_reason,
        })
    }

    /// Subscribe to additional topics.  Sends a `SUBSCRIBE_REQUEST` for each
    /// item via `conn.io()` and records it locally after the server confirms.
    ///
    /// This can be called at any time after construction — the connection is
    /// already open and the receive loop is running.
    pub async fn subscribe(&self, items: &[SubscriptionItem]) -> Result<()> {
        for item in items {
            let sub_pkg = message::subscribe(&item.topic, std::slice::from_ref(item));
            let resp = self.conn.io(sub_pkg, self.config.timeout).await?;
            let response = message::response_from_pkg(&resp);
            if !response.is_success() {
                return Err(EventMeshError::Server {
                    code: response.code.unwrap_or(-1) as i32,
                    message: response
                        .message
                        .unwrap_or_else(|| "subscribe failed".into()),
                });
            }
            self.subscriptions.lock().await.push(item.clone());
        }
        Ok(())
    }

    /// Unsubscribe from topics.  Sends an `UNSUBSCRIBE_REQUEST` via
    /// `conn.io()`.
    ///
    /// Note: the runtime's TCP `UnSubscribeProcessor` ignores the request body
    /// and drops **all** session topics.  The local subscription list is
    /// cleared entirely on success.
    pub async fn unsubscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "unsubscribe items must not be empty".into(),
            ));
        }
        let unsub_pkg = message::unsubscribe(&items);
        let resp = self.conn.io(unsub_pkg, self.config.timeout).await?;
        let response = message::response_from_pkg(&resp);

        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response
                    .message
                    .unwrap_or_else(|| "unsubscribe failed".into()),
            });
        }

        let mut subs = self.subscriptions.lock().await;
        let current: Vec<String> = subs.iter().map(|s| s.topic.clone()).collect();
        let passed_all =
            items.len() == current.len() && items.iter().all(|i| current.contains(&i.topic));
        if !passed_all {
            warn!(
                passed = ?items.iter().map(|i| i.topic.clone()).collect::<Vec<_>>(),
                current = ?current,
                "TCP unsubscribe drops ALL topics on the server (not just \
                 the ones passed); clearing local state to match"
            );
        }
        subs.clear();
        Ok(response)
    }

    /// Current config.
    pub fn config(&self) -> &TcpClientConfig {
        &self.config
    }

    /// Explicitly shut down: cancel the shared token, shut down the
    /// connection, and await the driver task's exit.
    pub async fn shutdown(&self) {
        self.shutdown.cancel();
        self.conn.shutdown().await;
        if let Some(handle) = self.driver_handle.lock().await.take() {
            let _ = handle.await;
        }
    }

    /// Block until the shutdown signal fires or the receive loop exits on its
    /// own (e.g. server redirect or I/O error), then return a
    /// [`ShutdownReason`] indicating why the driver stopped.
    ///
    /// If the driver task panics, the `JoinHandle` resolves with
    /// `Err(JoinError)` and this method returns `ShutdownReason::Error` — it
    /// does **not** hang forever waiting for the cancellation token (which the
    /// panicked driver would never fire).
    ///
    /// If no shutdown signal was provided at construction time, this blocks
    /// until the driver exits naturally.
    pub async fn wait_for_shutdown(&self) -> ShutdownReason {
        // Take ownership of the driver handle so we can race it against
        // cancellation.
        let handle = self.driver_handle.lock().await.take();

        if let Some(handle) = handle {
            tokio::select! {
                biased;
                _ = self.shutdown.cancelled() => {
                    // Cancellation fired (user signal, `shutdown()` call, or
                    // the driver itself called `shutdown.cancel()` before
                    // exiting). If the driver set a reason, it will be
                    // available below; otherwise the default is `Cancelled`.
                    self.conn.shutdown().await;
                    // The handle was consumed by the select — the driver
                    // task continues asynchronously and will observe the
                    // cancellation token / closed inbound channel and exit.
                }
                result = handle => {
                    // Driver exited before the token was cancelled (redirect,
                    // channel closed, error, or panic). Cancel to stop any
                    // signal-watcher task.
                    self.shutdown.cancel();
                    // If the driver panicked, capture the JoinError as a
                    // shutdown reason.
                    if let Err(join_err) = result {
                        *self.shutdown_reason.lock().await = Some(ShutdownReason::Error(
                            format!("receive-loop driver task panicked: {join_err}"),
                        ));
                    }
                }
            }
        } else {
            // Handle already taken (concurrent `shutdown()` or previous
            // `wait_for_shutdown`).
            self.shutdown.cancelled().await;
        }

        self.shutdown_reason
            .lock()
            .await
            .take()
            .unwrap_or(ShutdownReason::Cancelled)
    }
}

impl<L: MessageListener> Drop for TcpConsumer<L> {
    fn drop(&mut self) {
        self.shutdown.cancel();
        if let Ok(mut guard) = self.driver_handle.try_lock() {
            if let Some(handle) = guard.take() {
                handle.abort();
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Receive-loop driver (spawned, not public)
// ---------------------------------------------------------------------------

/// Spawn the receive loop as a background task.
///
/// Dispatches delivered messages to the listener and sends ACKs / replies.
/// On reconnect, replays all subscriptions and re-issues `LISTEN_REQUEST`.
/// Exits when the shutdown token fires, the inbound channel closes, or a
/// `REDIRECT_TO_CLIENT` frame is received.  On exit, cancels the shutdown
/// token so `wait_for_shutdown` unblocks.
#[allow(clippy::too_many_arguments)]
fn spawn_driver<L>(
    conn: Arc<TcpConnection>,
    mut inbound_rx: tokio::sync::mpsc::Receiver<Package>,
    mut reconnect_rx: Option<tokio::sync::mpsc::Receiver<()>>,
    listener: Arc<L>,
    config: TcpClientConfig,
    subscriptions: Arc<Mutex<Vec<SubscriptionItem>>>,
    shutdown: CancellationToken,
    shutdown_reason: Arc<Mutex<Option<ShutdownReason>>>,
) -> JoinHandle<Result<()>>
where
    L: MessageListener,
    L::Message: TcpMessage,
{
    tokio::spawn(async move {
        loop {
            tokio::select! {
                biased;
                _ = shutdown.cancelled() => {
                    debug!("receive loop shutting down");
                    *shutdown_reason.lock().await = Some(ShutdownReason::Cancelled);
                    break;
                }

                // Reconnect event: the connection task has re-established
                // the TCP session. Replay all subscriptions + LISTEN.
                event = async {
                    match reconnect_rx.as_mut() {
                        Some(rx) => rx.recv().await,
                        None => std::future::pending().await,
                    }
                } => {
                    if event.is_none() {
                        info!("reconnect channel closed, exiting receive loop");
                        *shutdown_reason.lock().await = Some(ShutdownReason::ChannelClosed);
                        break;
                    }
                    info!("connection reconnected, replaying subscriptions");
                    let subs_snapshot = subscriptions.lock().await.clone();
                    let mut all_ok = true;
                    for item in &subs_snapshot {
                        let sub_pkg =
                            message::subscribe(&item.topic, std::slice::from_ref(item));
                        match conn.io(sub_pkg, config.timeout).await {
                            Ok(resp) => {
                                let r = message::response_from_pkg(&resp);
                                if !r.is_success() {
                                    warn!(
                                        topic = ?item.topic,
                                        code = r.code,
                                        "re-subscribe after reconnect rejected"
                                    );
                                    all_ok = false;
                                }
                            }
                            Err(e) => {
                                warn!(
                                    topic = ?item.topic,
                                    error = %e,
                                    "re-subscribe after reconnect error"
                                );
                                all_ok = false;
                            }
                        }
                    }
                    if all_ok {
                        match conn.io(message::listen(), config.timeout).await {
                            Ok(resp) => {
                                let r = message::response_from_pkg(&resp);
                                if !r.is_success() {
                                    warn!(code = r.code, "re-LISTEN after reconnect rejected");
                                } else {
                                    debug!("re-LISTEN ok after reconnect");
                                }
                            }
                            Err(e) => {
                                warn!(error = %e, "re-LISTEN after reconnect error");
                            }
                        }
                    }
                }

                // Inbound message from the server.
                pkg = inbound_rx.recv() => {
                    match pkg {
                        Some(pkg) => {
                            match handle_inbound(&pkg, &conn, &*listener).await {
                                InboundResult::Continue => {}
                                InboundResult::Redirect(ri) => {
                                    info!(
                                        ip = %ri.ip,
                                        port = ri.port,
                                        "receive loop stopping after REDIRECT_TO_CLIENT"
                                    );
                                    *shutdown_reason.lock().await =
                                        Some(ShutdownReason::Redirect(ri));
                                    break;
                                }
                                InboundResult::Stop => {
                                    info!("receive loop stopping (disconnect or parse failure)");
                                    // The consumer keeps an Arc to the connection after the
                                    // driver exits. Close it here so an unacknowledged delivery
                                    // is released for server-side redelivery instead of leaving
                                    // the I/O task and socket alive until the caller joins.
                                    conn.shutdown().await;
                                    *shutdown_reason.lock().await =
                                        Some(ShutdownReason::ChannelClosed);
                                    break;
                                }
                            }
                        }
                        None => {
                            info!("inbound channel closed, exiting receive loop");
                            *shutdown_reason.lock().await = Some(ShutdownReason::ChannelClosed);
                            break;
                        }
                    }
                }
            }
        }

        // Signal wait_for_shutdown that we've exited.
        shutdown.cancel();
        Ok(())
    })
}

/// Dispatch an inbound package: parse the message, invoke the listener, send
/// any reply, then send the matching ACK.
///
/// Returns [`InboundResult::Continue`] to keep the receive loop running,
/// [`InboundResult::Redirect`] to stop and report a redirect target, or
/// [`InboundResult::Stop`] to stop the loop (which triggers reconnection
/// and server-side redelivery).
///
/// If the message cannot be parsed, **no ACK is sent** and the function
/// returns [`InboundResult::Stop`] so the connection is torn down — mirroring
/// the Java SDK, where a parse exception propagates to `exceptionCaught` and
/// closes the channel.  A listener panic likewise propagates and kills the
/// receive task; it is not silently swallowed.
async fn handle_inbound<L>(pkg: &Package, conn: &TcpConnection, listener: &L) -> InboundResult
where
    L: MessageListener,
    L::Message: TcpMessage,
{
    let ack_cmd = match pkg.header.cmd {
        Command::RequestToClient => Some(Command::RequestToClientAck),
        Command::AsyncMessageToClient => Some(Command::AsyncMessageToClientAck),
        Command::BroadcastMessageToClient => Some(Command::BroadcastMessageToClientAck),
        Command::ServerGoodbyeRequest => {
            info!("server goodbye received, sending SERVER_GOODBYE_RESPONSE");
            let resp = message::ack(Command::ServerGoodbyeResponse, pkg);
            if let Err(e) = conn.send(resp).await {
                warn!(error = %e, "failed to send SERVER_GOODBYE_RESPONSE");
            }
            return InboundResult::Continue;
        }
        Command::RedirectToClient => {
            match pkg.body {
                PackageBody::RedirectInfo(ref ri) => {
                    info!(
                        ip = %ri.ip,
                        port = ri.port,
                        "received REDIRECT_TO_CLIENT; stopping receive loop so the \
                         caller can reconnect to the advertised EventMesh node"
                    );
                    return InboundResult::Redirect(ri.clone());
                }
                PackageBody::Text(ref s) => warn!(
                    body = %s,
                    "REDIRECT_TO_CLIENT body did not deserialize into RedirectInfo; \
                     stopping receive loop"
                ),
                ref other => warn!(
                    body = ?other,
                    "unexpected body shape for REDIRECT_TO_CLIENT; stopping receive loop"
                ),
            }
            return InboundResult::Stop;
        }
        cmd => {
            warn!(?cmd, "unexpected inbound command, ignoring");
            return InboundResult::Continue;
        }
    };

    let msg = match L::Message::decode_tcp(pkg) {
        Some(msg) => msg,
        None => {
            warn!("failed to parse inbound message body; disconnecting without ACK");
            return InboundResult::Stop;
        }
    };

    debug!("dispatching to listener");
    // A listener panic propagates naturally and kills the receive task —
    // mirroring Java where the exception escapes to exceptionCaught and
    // closes the channel.  The message is NOT acknowledged.
    let request = (pkg.header.cmd == Command::RequestToClient).then(|| msg.clone());
    let reply = match listener.handle(msg).await {
        Ok(reply) => reply,
        Err(error) => {
            warn!(%error, "listener failed; disconnecting without ACK");
            return InboundResult::Stop;
        }
    };
    if let Some(mut reply) = reply {
        if let Some(request) = request.as_ref() {
            reply.inherit_request_metadata(request);
        }
        match reply.encode_tcp_reply() {
            Ok(reply_pkg) => {
                if let Err(e) = conn.send(reply_pkg).await {
                    warn!(error = %e, "failed to send reply");
                }
            }
            Err(e) => warn!(error = %e, "failed to serialize reply"),
        }
    }

    if let Some(cmd) = ack_cmd {
        let ack_pkg = message::ack(cmd, pkg);
        if let Err(e) = conn.send(ack_pkg).await {
            warn!(error = %e, "failed to send ACK");
        }
    }

    InboundResult::Continue
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use super::*;
    use crate::config::TcpClientConfig;
    use crate::model::{SubscriptionItem, SubscriptionMode, SubscriptionType};
    use crate::transport::tcp::codec::TcpCodec;
    use crate::transport::tcp::frame::{Command, Header, Package, PackageBody, RedirectInfo};

    use futures::SinkExt;
    use tokio::net::TcpListener;
    use tokio_stream::StreamExt;
    use tokio_util::codec::Framed;

    /// A no-op listener used only to satisfy `TcpConsumer`'s type parameter.
    struct NoopListener;
    impl MessageListener for NoopListener {
        type Message = EventMeshMessage;
        async fn handle(&self, _: EventMeshMessage) -> Result<Option<EventMeshMessage>> {
            Ok(None)
        }
    }

    /// A listener failure must tear down the TCP session without ACKing the
    /// delivery so the server can redeliver it on a subsequent connection.
    struct FailingListener;
    impl MessageListener for FailingListener {
        type Message = EventMeshMessage;
        async fn handle(&self, _: EventMeshMessage) -> Result<Option<EventMeshMessage>> {
            Err(EventMeshError::Tcp("listener failure".into()))
        }
    }

    #[test]
    fn public_message_preserves_open_protocol() {
        let open = OpenMessage::new("orders", "created");
        let package =
            message::build_open_message_package(&open, Command::AsyncMessageToClient).unwrap();
        let decoded = <Message as TcpMessage>::decode_tcp(&package).expect("decode message");
        assert_eq!(decoded, Message::Open(open));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn public_message_preserves_cloud_event_protocol() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("event-1")
            .source("urn:test")
            .ty("orders.created")
            .subject("orders")
            .data("application/cloudevents+json", "created")
            .build()
            .expect("build event");
        let package =
            message::build_cloud_event_package(&event, Command::AsyncMessageToClient).unwrap();
        let decoded = <Message as TcpMessage>::decode_tcp(&package).expect("decode message");
        match decoded {
            Message::CloudEvent(decoded) => {
                use cloudevents::AttributesReader;
                assert_eq!(decoded.id(), "event-1");
                assert_eq!(decoded.subject(), Some("orders"));
                assert!(decoded.data().is_some());
            }
            other => panic!("expected CloudEvent, got {other:?}"),
        }
    }

    #[tokio::test]
    async fn listener_error_closes_tcp_connection_without_ack() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();

        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());

            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            framed
                .send(Package::new(Header::new(
                    Command::HelloResponse,
                    "hello-seq",
                )))
                .await
                .unwrap();

            let listen = framed.next().await.unwrap().unwrap();
            assert_eq!(listen.header.cmd, Command::ListenRequest);
            framed
                .send(Package::new(Header::new(
                    Command::ListenResponse,
                    listen.header.seq.clone().unwrap_or_default(),
                )))
                .await
                .unwrap();

            let delivery = message::build_message_package(
                &EventMeshMessage::builder()
                    .topic("topic")
                    .content("payload")
                    .build(),
                Command::AsyncMessageToClient,
            )
            .unwrap();
            framed.send(delivery).await.unwrap();

            tokio::time::timeout(Duration::from_secs(3), async {
                loop {
                    match framed.next().await {
                        Some(Ok(pkg)) if pkg.header.cmd == Command::ClientGoodbyeRequest => {}
                        Some(Ok(pkg)) => {
                            panic!(
                                "listener failure must not ACK delivery; got {:?}",
                                pkg.header.cmd
                            )
                        }
                        Some(Err(_)) | None => break,
                    }
                }
            })
            .await
            .expect("listener failure should close the TCP connection promptly");
        });

        let config = TcpClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(port)
            .consumer_group("g")
            .timeout(Duration::from_secs(3))
            .heartbeat_interval(Duration::from_secs(60))
            .build();
        let consumer =
            TcpConsumer::connect(config, FailingListener, None::<std::future::Ready<()>>)
                .await
                .expect("connect");

        server.await.unwrap();
        assert!(
            !consumer.conn.is_active(),
            "listener failure must stop the connection I/O task without requiring join"
        );
    }

    #[test]
    fn request_reply_inherits_routing_properties_without_overwriting_reply() {
        let request = EventMeshMessage::builder()
            .topic("request-topic")
            .content("request")
            .prop("cluster", "remote-cluster")
            .prop("correlation-id", "request-id")
            .build();
        let mut reply = EventMeshMessage::builder()
            .topic("reply-topic")
            .content("reply")
            .prop("correlation-id", "reply-id")
            .build();

        reply.inherit_request_metadata(&request);
        let pkg = reply.encode_tcp_reply().expect("encode reply");
        let encoded = message::parse_message(&pkg.body).expect("decode reply");

        assert_eq!(encoded.get_prop("cluster"), Some("remote-cluster"));
        assert_eq!(encoded.get_prop("correlation-id"), Some("reply-id"));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn cloud_event_reply_inherits_request_extensions() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let request = EventBuilderV10::new()
            .id("request")
            .source("urn:test")
            .ty("test")
            .extension("cluster", "remote-cluster")
            .build()
            .expect("build request");
        let mut reply = EventBuilderV10::new()
            .id("reply")
            .source("urn:test")
            .ty("test")
            .build()
            .expect("build reply");

        reply.inherit_request_metadata(&request);
        assert_eq!(
            reply.extension("cluster").unwrap().to_string(),
            "remote-cluster"
        );
    }

    /// Loopback test: the runtime's TCP `UnSubscribeProcessor` ignores the
    /// request body and drops **all** session topics. After subscribing to A
    /// and B and calling `unsubscribe([A])`, the local `subscriptions` map
    /// must be empty (not just missing A) so it matches the server.
    #[tokio::test]
    async fn unsubscribe_clears_all_local_state() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();

        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());

            // 1. HELLO handshake.
            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            let hello_resp = Package::new(Header::new(Command::HelloResponse, "hello-seq"));
            framed.send(hello_resp).await.unwrap();

            // 2. Reply to LISTEN_REQUEST.
            let req = framed.next().await.unwrap().unwrap();
            assert_eq!(req.header.cmd, Command::ListenRequest);
            framed
                .send(Package::new(Header::new(
                    Command::ListenResponse,
                    req.header.seq.clone().unwrap_or_default(),
                )))
                .await
                .unwrap();

            // 3. Reply to each SUBSCRIBE_REQUEST with SubscribeResponse (code 0).
            for _ in 0..2 {
                let req = framed.next().await.unwrap().unwrap();
                assert_eq!(req.header.cmd, Command::SubscribeRequest);
                let resp = Package::new(Header::new(
                    Command::SubscribeResponse,
                    req.header.seq.clone().unwrap_or_default(),
                ));
                framed.send(resp).await.unwrap();
            }

            // 4. Reply to the UNSUBSCRIBE_REQUEST with UnsubscribeResponse (code 0).
            let req = framed.next().await.unwrap().unwrap();
            assert_eq!(req.header.cmd, Command::UnsubscribeRequest);
            let resp = Package::new(Header::new(
                Command::UnsubscribeResponse,
                req.header.seq.clone().unwrap_or_default(),
            ));
            framed.send(resp).await.unwrap();

            // Keep the connection alive until the client drops it.
            let _ = framed.close().await;
        });

        let config = TcpClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(port)
            .consumer_group("g")
            .timeout(Duration::from_secs(3))
            .heartbeat_interval(Duration::from_secs(60))
            .build();

        let consumer = TcpConsumer::connect(config, NoopListener, None::<std::future::Ready<()>>)
            .await
            .expect("connect");

        // Subscribe to two topics. Each call records into `self.subscriptions`.
        let item_a =
            SubscriptionItem::new("A", SubscriptionMode::CLUSTERING, SubscriptionType::SYNC);
        let item_b =
            SubscriptionItem::new("B", SubscriptionMode::CLUSTERING, SubscriptionType::SYNC);
        consumer
            .subscribe(&[item_a, item_b])
            .await
            .expect("subscribe A+B");
        {
            let subs = consumer.subscriptions.lock().await;
            assert_eq!(subs.len(), 2, "both subscriptions should be recorded");
        }

        // Unsubscribe only A. The server drops ALL topics, so the local map
        // must be fully cleared — not left with a phantom B entry.
        let item_a =
            SubscriptionItem::new("A", SubscriptionMode::CLUSTERING, SubscriptionType::SYNC);
        consumer
            .unsubscribe(vec![item_a])
            .await
            .expect("unsubscribe A");
        {
            let subs = consumer.subscriptions.lock().await;
            assert!(
                subs.is_empty(),
                "local subscriptions must be fully cleared after unsubscribe, got: {:?}",
                *subs
            );
        }

        consumer.shutdown().await;
        let _ = server.await;
    }

    /// When the server returns a non-zero code for UNSUBSCRIBE_REQUEST, the
    /// SDK must return `Err(Server)` — not `Ok` with a failed response.
    /// Local subscription state must be preserved on failure.
    #[tokio::test]
    async fn unsubscribe_nonzero_returns_err() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();

        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());

            // 1. HELLO handshake.
            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            framed
                .send(Package::new(Header::new(
                    Command::HelloResponse,
                    "hello-seq",
                )))
                .await
                .unwrap();

            // 2. Reply to LISTEN_REQUEST.
            let req = framed.next().await.unwrap().unwrap();
            assert_eq!(req.header.cmd, Command::ListenRequest);
            framed
                .send(Package::new(Header::new(
                    Command::ListenResponse,
                    req.header.seq.clone().unwrap_or_default(),
                )))
                .await
                .unwrap();

            // 3. Reply to SUBSCRIBE_REQUEST with code 0.
            let req = framed.next().await.unwrap().unwrap();
            assert_eq!(req.header.cmd, Command::SubscribeRequest);
            framed
                .send(Package::new(Header::new(
                    Command::SubscribeResponse,
                    req.header.seq.clone().unwrap_or_default(),
                )))
                .await
                .unwrap();

            // 4. Reply to UNSUBSCRIBE_REQUEST with code 1 (FAIL).
            let req = framed.next().await.unwrap().unwrap();
            assert_eq!(req.header.cmd, Command::UnsubscribeRequest);
            let mut resp = Header::new(
                Command::UnsubscribeResponse,
                req.header.seq.clone().unwrap_or_default(),
            );
            resp.code = 1;
            resp.desc = Some("group not found".into());
            framed.send(Package::new(resp)).await.unwrap();

            let _ = framed.close().await;
        });

        let config = TcpClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(port)
            .consumer_group("g")
            .timeout(Duration::from_secs(3))
            .heartbeat_interval(Duration::from_secs(60))
            .build();

        let consumer = TcpConsumer::connect(config, NoopListener, None::<std::future::Ready<()>>)
            .await
            .expect("connect");

        let item = SubscriptionItem::new("A", SubscriptionMode::CLUSTERING, SubscriptionType::SYNC);
        consumer.subscribe(&[item]).await.expect("subscribe");

        // Server returns code 1 → must be Err, not Ok.
        let item = SubscriptionItem::new("A", SubscriptionMode::CLUSTERING, SubscriptionType::SYNC);
        let err = consumer
            .unsubscribe(vec![item])
            .await
            .expect_err("should fail");
        assert!(
            err.to_string().contains("server error"),
            "expected Server error, got: {err}"
        );

        // Local state must be preserved on failure.
        let subs = consumer.subscriptions.lock().await;
        assert_eq!(
            subs.len(),
            1,
            "subscriptions must not be cleared on failure"
        );

        consumer.shutdown().await;
        let _ = server.await;
    }
    /// The server sends `REDIRECT_TO_CLIENT` with an `ip`/`port` body.
    /// The receive loop must stop promptly and `wait_for_shutdown` must
    /// return `ShutdownReason::Redirect` carrying the advertised address.
    #[tokio::test]
    async fn redirect_to_client_returns_redirect_reason() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();

        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());

            // 1. HELLO handshake.
            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            framed
                .send(Package::new(Header::new(
                    Command::HelloResponse,
                    "hello-seq",
                )))
                .await
                .unwrap();

            // 2. Reply to LISTEN_REQUEST.
            let req = framed.next().await.unwrap().unwrap();
            assert_eq!(req.header.cmd, Command::ListenRequest);
            framed
                .send(Package::new(Header::new(
                    Command::ListenResponse,
                    req.header.seq.clone().unwrap_or_default(),
                )))
                .await
                .unwrap();

            // 3. Send REDIRECT_TO_CLIENT.
            let redirect = Package::new(Header::new(Command::RedirectToClient, "redirect-seq"))
                .with_body(PackageBody::RedirectInfo(RedirectInfo {
                    ip: "10.0.0.9".into(),
                    port: 10000,
                }));
            framed.send(redirect).await.unwrap();

            let _ = framed.close().await;
        });

        let config = TcpClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(port)
            .consumer_group("g")
            .timeout(Duration::from_secs(3))
            .heartbeat_interval(Duration::from_secs(60))
            .build();

        let consumer = TcpConsumer::connect(config, NoopListener, None::<std::future::Ready<()>>)
            .await
            .expect("connect");

        // The redirect frame should make the driver exit on its own.
        // wait_for_shutdown must return promptly with the redirect reason.
        let reason =
            tokio::time::timeout(Duration::from_secs(10), consumer.wait_for_shutdown()).await;
        assert!(
            reason.is_ok(),
            "REDIRECT_TO_CLIENT should stop the receive loop promptly"
        );

        match reason.unwrap() {
            ShutdownReason::Redirect(ri) => {
                assert_eq!(ri.ip, "10.0.0.9", "redirect ip must match");
                assert_eq!(ri.port, 10000, "redirect port must match");
            }
            other => panic!("expected ShutdownReason::Redirect, got {other:?}"),
        }

        let _ = server.await;
    }
}
