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
//! See [`crate::tcp`] for the public client and consumer API.

use std::future::Future;
use std::sync::Arc;

use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::{debug, info, warn};

use crate::config::{ConsumerOptions, TcpConfig};
use crate::error::{EventMeshError, Result};
#[cfg(test)]
use crate::message::Message;
#[cfg(test)]
use crate::model::EventMeshMessage;
use crate::model::PublishResponse;
use crate::subscription::Subscription;
use crate::transport::task::BackgroundTask;
use crate::transport::tcp::connection::{InboundPackage, TcpConnection};
use crate::transport::tcp::delivery::dispatch;
#[cfg(test)]
use crate::transport::tcp::delivery::{decode_message, encode_reply, inherit_request_metadata};
use crate::transport::tcp::frame::{Command, PackageBody, RedirectInfo, UserAgent};
use crate::transport::tcp::message;
use crate::MessageHandler;

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
    /// Close the current session and recover according to the reconnect policy.
    Reconnect,
}

/// TCP-based consumer, generic over the user's [`MessageHandler`] type.
///
/// Created via [`TcpConsumer::connect`], which opens a TCP connection, performs
/// the HELLO handshake (role = sub), sends `LISTEN_REQUEST`, and spawns the
/// receive loop + heartbeat as background tasks.
///
/// Subscribe and unsubscribe RPCs can be called at any time after construction.
/// The background tasks are stopped when the consumer is dropped or explicitly
/// via [`request_shutdown`](Self::request_shutdown) / [`wait_for_shutdown`](Self::wait_for_shutdown).
pub struct TcpConsumer<L: MessageHandler> {
    conn: Arc<TcpConnection>,
    config: TcpConfig,
    _listener: std::marker::PhantomData<Arc<L>>,
    shutdown: CancellationToken,
    subscriptions: Arc<Mutex<Vec<Subscription>>>,
    driver_handle: Mutex<BackgroundTask<Result<()>>>,
    /// Filled by the driver before it exits, so `wait_for_shutdown` can
    /// return a structured [`ShutdownReason`].
    shutdown_reason: Arc<Mutex<Option<ShutdownReason>>>,
}

impl<L> TcpConsumer<L>
where
    L: MessageHandler,
{
    /// Connect to the EventMesh TCP endpoint, perform the HELLO handshake
    /// (role = sub), send `LISTEN_REQUEST`, and spawn the receive loop.
    ///
    /// `shutdown_signal` is an optional future whose resolution triggers
    /// graceful shutdown.  When omitted, shutdown can only be initiated by
    /// [`request_shutdown`](Self::request_shutdown) or drop.
    ///
    /// The reconnect policy from the config controls automatic reconnection
    /// after I/O failures or failed deliveries (enabled by default). After a
    /// successful reconnect, the receive loop automatically replays all
    /// subscriptions and re-issues `LISTEN_REQUEST`.
    pub async fn connect(
        config: TcpConfig,
        options: &ConsumerOptions,
        listener: L,
        shutdown_signal: Option<impl Future<Output = ()> + Send + 'static>,
    ) -> Result<Self> {
        let user_agent = UserAgent::from_role(
            config.identity(),
            config.credentials(),
            options.group(),
            config.endpoint().port(),
            "sub",
        );
        let conn = TcpConnection::connect(
            &config.endpoint().authority_host(),
            config.endpoint().port(),
            &user_agent,
            config.heartbeat_interval(),
            config.connect_timeout(),
            config.control_timeout(),
            config.reconnect().clone(),
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
        let listen_resp = conn.io(listen_pkg, config.control_timeout()).await?;
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
            driver_handle: Mutex::new(BackgroundTask::new(driver_handle)),
            shutdown_reason,
        })
    }

    /// Subscribe to additional topics.  Sends a `SUBSCRIBE_REQUEST` for each
    /// item via `conn.io()` and records it locally after the server confirms.
    ///
    /// This can be called at any time after construction — the connection is
    /// already open and the receive loop is running.
    pub async fn subscribe(&self, items: &[Subscription]) -> Result<()> {
        let mut subscriptions = self.subscriptions.lock().await;
        if self.shutdown.is_cancelled() {
            return Err(EventMeshError::ChannelClosed(
                "TCP consumer is shut down".into(),
            ));
        }
        for item in items {
            let sub_pkg = message::subscribe(&item.topic, std::slice::from_ref(item));
            let resp = self.conn.io(sub_pkg, self.config.control_timeout()).await?;
            let response = message::response_from_pkg(&resp);
            if !response.is_success() {
                return Err(EventMeshError::Server {
                    code: response.code.unwrap_or(-1) as i32,
                    message: response
                        .message
                        .unwrap_or_else(|| "subscribe failed".into()),
                });
            }
            subscriptions.retain(|subscription| subscription.topic != item.topic);
            subscriptions.push(item.clone());
        }
        Ok(())
    }

    /// Unsubscribe from topics.  Sends an `UNSUBSCRIBE_REQUEST` via
    /// `conn.io()`.
    ///
    /// Note: the runtime's TCP `UnSubscribeProcessor` ignores the request body
    /// and drops **all** session topics.  The local subscription list is
    /// cleared entirely on success.
    #[cfg(test)]
    pub async fn unsubscribe(&self, items: Vec<Subscription>) -> Result<PublishResponse> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "unsubscribe items must not be empty".into(),
            ));
        }
        let mut subs = self.subscriptions.lock().await;
        self.unsubscribe_locked(&items, &mut subs).await
    }

    async fn unsubscribe_locked(
        &self,
        items: &[Subscription],
        subs: &mut Vec<Subscription>,
    ) -> Result<PublishResponse> {
        if self.shutdown.is_cancelled() {
            return Err(EventMeshError::ChannelClosed(
                "TCP consumer is shut down".into(),
            ));
        }
        let unsub_pkg = message::unsubscribe(items);
        let resp = self
            .conn
            .io(unsub_pkg, self.config.control_timeout())
            .await?;
        let response = message::response_from_pkg(&resp);

        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response
                    .message
                    .unwrap_or_else(|| "unsubscribe failed".into()),
            });
        }

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

    /// Unsubscribe every topic on this TCP session.
    pub async fn unsubscribe_all(&self) -> Result<PublishResponse> {
        let mut subs = self.subscriptions.lock().await;
        let items = subs.clone();
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "TCP consumer has no active subscriptions".into(),
            ));
        }
        self.unsubscribe_locked(&items, &mut subs).await
    }

    /// Signal the receive-loop driver to stop.
    pub fn request_shutdown(&self) {
        self.shutdown.cancel();
    }

    /// Signal shutdown, close the connection, and await the driver task.
    #[cfg(test)]
    pub async fn shutdown(&self) {
        self.request_shutdown();
        self.conn.shutdown().await;
        self.wait_for_driver().await;
    }

    async fn wait_for_driver(&self) {
        let mut driver = self.driver_handle.lock().await;
        driver.wait().await;
        self.shutdown.cancel();
        // Acquire the state lock before consuming the task result, so a
        // cancelled wait cannot lose an error while acquiring this lock.
        let mut reason = self.shutdown_reason.lock().await;
        match driver.take_result() {
            Some(Ok(Err(error))) => {
                *reason = Some(ShutdownReason::Error(error.to_string()));
            }
            Some(Err(error)) => {
                *reason = Some(ShutdownReason::Error(format!(
                    "receive-loop driver task panicked: {error}"
                )));
            }
            _ => {}
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
        self.wait_for_driver().await;
        self.conn.shutdown().await;

        self.shutdown_reason
            .lock()
            .await
            .take()
            .unwrap_or(ShutdownReason::Cancelled)
    }
}

impl<L: MessageHandler> Drop for TcpConsumer<L> {
    fn drop(&mut self) {
        self.shutdown.cancel();
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
    mut inbound_rx: tokio::sync::mpsc::Receiver<InboundPackage>,
    mut reconnect_rx: Option<tokio::sync::watch::Receiver<u64>>,
    listener: Arc<L>,
    config: TcpConfig,
    subscriptions: Arc<Mutex<Vec<Subscription>>>,
    shutdown: CancellationToken,
    shutdown_reason: Arc<Mutex<Option<ShutdownReason>>>,
) -> JoinHandle<Result<()>>
where
    L: MessageHandler,
{
    tokio::spawn(async move {
        let mut recovering_generation = None;
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
                        Some(rx) => match rx.changed().await {
                            Ok(()) => Some(*rx.borrow_and_update()),
                            Err(_) => None,
                        },
                        None => std::future::pending().await,
                    }
                } => {
                    let Some(generation) = event else {
                        info!("reconnect channel closed, exiting receive loop");
                        *shutdown_reason.lock().await = Some(ShutdownReason::ChannelClosed);
                        break;
                    };
                    if recovering_generation.is_some_and(|minimum| generation < minimum) {
                        continue;
                    }
                    if !conn.is_generation_active(generation).await {
                        continue;
                    }
                    info!("connection reconnected, replaying subscriptions");
                    // Hold the subscription lock through replay and LISTEN.
                    // Shutdown must interrupt both lock acquisition and I/O.
                    let replay = tokio::select! {
                        biased;
                        _ = shutdown.cancelled() => {
                            *shutdown_reason.lock().await = Some(ShutdownReason::Cancelled);
                            break;
                        }
                        result = replay_subscriptions(&conn, &config, &subscriptions, generation) => result,
                    };
                    if let Err(error) = replay {
                        *shutdown_reason.lock().await = Some(ShutdownReason::Error(error.to_string()));
                        break;
                    }
                    recovering_generation = None;
                }

                // Inbound message from the server.
                pkg = inbound_rx.recv(), if recovering_generation.is_none() => {
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
                                InboundResult::Reconnect => {
                                    info!("closing failed delivery's session without ACK");
                                    recovering_generation = Some(conn.request_reconnect(pkg.generation).await);
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

        conn.shutdown().await;
        // Signal wait_for_shutdown that we've exited.
        shutdown.cancel();
        Ok(())
    })
}

/// Replay a single generation under the same control lock as subscription RPCs.
async fn replay_subscriptions(
    conn: &TcpConnection,
    config: &TcpConfig,
    subscriptions: &Mutex<Vec<Subscription>>,
    generation: u64,
) -> Result<()> {
    let subscriptions = subscriptions.lock().await;
    for item in subscriptions.iter() {
        let package = message::subscribe(&item.topic, std::slice::from_ref(item));
        let response = conn
            .io_in_generation(package, config.control_timeout(), generation)
            .await
            .map_err(|error| {
                EventMeshError::Tcp(format!(
                    "re-subscribe after reconnect failed for topic {:?}: {error}",
                    item.topic
                ))
            })?;
        let status = message::response_from_pkg(&response);
        if !status.is_success() {
            return Err(EventMeshError::Tcp(format!(
                "re-subscribe after reconnect rejected for topic {:?}: server code {:?}",
                item.topic, status.code
            )));
        }
    }
    let response = conn
        .io_in_generation(message::listen(), config.control_timeout(), generation)
        .await
        .map_err(|error| {
            EventMeshError::Tcp(format!("re-LISTEN after reconnect failed: {error}"))
        })?;
    let status = message::response_from_pkg(&response);
    if !status.is_success() {
        return Err(EventMeshError::Tcp(format!(
            "re-LISTEN after reconnect rejected: server code {:?}",
            status.code
        )));
    }
    Ok(())
}

/// Dispatch an inbound package: parse the message, invoke the listener, send
/// any reply, then send the matching ACK.
///
/// Returns [`InboundResult::Continue`] to keep the receive loop running,
/// [`InboundResult::Redirect`] to stop and report a redirect target, or
/// [`InboundResult::Reconnect`] to close the session without ACK and recover
/// according to the configured reconnect policy. Redelivery is Runtime-dependent.
///
/// If the message cannot be parsed, **no ACK is sent** and the function
/// returns [`InboundResult::Reconnect`] so the connection is torn down — mirroring
/// the Java SDK, where a parse exception propagates to `exceptionCaught` and
/// closes the channel. A listener failure or a reply encoding / enqueue
/// failure follows the same no-ACK path so the server can redeliver the
/// request. An unwinding listener panic follows the same path as a listener
/// error: no reply or ACK, then session teardown, reconnect, and subscription replay.
async fn handle_inbound<L>(
    delivery: &InboundPackage,
    conn: &TcpConnection,
    listener: &L,
) -> InboundResult
where
    L: MessageHandler,
{
    // Terminal control frames still matter after the sender closes its socket.
    // Business deliveries are checked against their generation by dispatch().
    let pkg = &delivery.package;
    let ack_cmd = match pkg.header.cmd {
        Command::RequestToClient => Some(Command::RequestToClientAck),
        Command::AsyncMessageToClient => Some(Command::AsyncMessageToClientAck),
        Command::BroadcastMessageToClient => Some(Command::BroadcastMessageToClientAck),
        Command::ServerGoodbyeRequest => {
            info!("server goodbye received, sending SERVER_GOODBYE_RESPONSE");
            let resp = message::ack(Command::ServerGoodbyeResponse, pkg);
            if let Err(e) = conn.send_in_generation(resp, delivery.generation).await {
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
            return InboundResult::Reconnect;
        }
        cmd => {
            warn!(?cmd, "unexpected inbound command, ignoring");
            return InboundResult::Continue;
        }
    };

    if let Some(ack_cmd) = ack_cmd {
        if let Err(error) = dispatch(delivery, conn, listener, ack_cmd).await {
            warn!(%error, "failed TCP delivery; closing its session without ACK");
            return InboundResult::Reconnect;
        }
    }

    InboundResult::Continue
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::time::Duration;

    use super::*;
    use crate::config::{ConsumerOptions, Endpoint, ReconnectPolicy, TcpConfig};
    use crate::subscription::{DeliveryType, Subscription};
    use crate::transport::tcp::codec::TcpCodec;
    use crate::transport::tcp::frame::{Command, Header, Package, PackageBody, RedirectInfo};

    use futures::SinkExt;
    use tokio::net::{TcpListener, TcpStream};
    use tokio_stream::StreamExt;
    use tokio_util::codec::Framed;

    /// A no-op listener used only to satisfy `TcpConsumer`'s type parameter.
    struct NoopListener;
    impl MessageHandler for NoopListener {
        async fn handle(&self, _: Message) -> Result<Option<Message>> {
            Ok(None)
        }
    }

    #[tokio::test]
    async fn cancelled_join_preserves_tcp_driver_failure() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());
            for (request, response) in [
                (Command::HelloRequest, Command::HelloResponse),
                (Command::ListenRequest, Command::ListenResponse),
            ] {
                let package = framed.next().await.unwrap().unwrap();
                assert_eq!(package.header.cmd, request);
                framed
                    .send(Package::new(Header::new(
                        response,
                        package.header.seq.unwrap(),
                    )))
                    .await
                    .unwrap();
            }
            while framed.next().await.is_some() {}
        });
        let consumer = TcpConsumer::connect(
            TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
                .with_reconnect(ReconnectPolicy::default().with_enabled(false)),
            &ConsumerOptions::new("consumer"),
            NoopListener,
            None::<std::future::Ready<()>>,
        )
        .await
        .unwrap();
        let (release, released) = tokio::sync::oneshot::channel::<()>();
        *consumer.driver_handle.lock().await = BackgroundTask::new(tokio::spawn(async move {
            released.await.unwrap();
            Err(EventMeshError::Tcp("driver regression".into()))
        }));
        for _ in 0..2 {
            assert!(
                tokio::time::timeout(Duration::from_millis(20), consumer.wait_for_shutdown())
                    .await
                    .is_err()
            );
            consumer.request_shutdown();
        }
        release.send(()).unwrap();
        let reason = tokio::time::timeout(Duration::from_secs(3), consumer.wait_for_shutdown())
            .await
            .unwrap();
        assert!(matches!(reason, ShutdownReason::Error(message)
            if message.contains("driver regression")));
        tokio::time::timeout(Duration::from_secs(3), server)
            .await
            .unwrap()
            .unwrap();
    }

    /// A listener failure must tear down the TCP session without ACKing the
    /// delivery so the server can redeliver it on a subsequent connection.
    struct FailingListener;
    impl MessageHandler for FailingListener {
        async fn handle(&self, _: Message) -> Result<Option<Message>> {
            Err(EventMeshError::Tcp("listener failure".into()))
        }
    }

    #[derive(Clone, Copy)]
    enum HandlerFailure {
        Error,
        PanicBeforeFuture,
        PanicDuringPoll,
    }

    struct FailsOnce {
        calls: Arc<AtomicUsize>,
        failure: HandlerFailure,
    }

    impl MessageHandler for FailsOnce {
        fn handle(&self, _: Message) -> impl Future<Output = Result<Option<Message>>> + Send {
            let call = self.calls.fetch_add(1, Ordering::SeqCst);
            let failure = self.failure;
            if call == 0 && matches!(failure, HandlerFailure::PanicBeforeFuture) {
                panic!("handler panicked before returning its future");
            }
            async move {
                tokio::task::yield_now().await;
                if call == 0 {
                    match failure {
                        HandlerFailure::Error => {
                            return Err(EventMeshError::Tcp("listener failure".into()));
                        }
                        HandlerFailure::PanicBeforeFuture | HandlerFailure::PanicDuringPoll => {
                            std::panic::panic_any(String::from("handler panicked after yielding"));
                        }
                    }
                }
                Ok(None)
            }
        }
    }

    async fn confirm_request(
        framed: &mut Framed<TcpStream, TcpCodec>,
        request: Command,
        response: Command,
    ) -> Package {
        let package = framed.next().await.unwrap().unwrap();
        assert_eq!(package.header.cmd, request);
        framed
            .send(Package::new(Header::new(
                response,
                package.header.seq.clone().unwrap(),
            )))
            .await
            .unwrap();
        package
    }

    async fn assert_handler_failure_recovers(failure: HandlerFailure) {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (deliver_tx, deliver_rx) = tokio::sync::oneshot::channel();
        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut first = Framed::new(stream, TcpCodec::new());
            for (request, response) in [
                (Command::HelloRequest, Command::HelloResponse),
                (Command::ListenRequest, Command::ListenResponse),
            ] {
                confirm_request(&mut first, request, response).await;
            }
            let mut subscriptions = Vec::new();
            for _ in 0..2 {
                let package = confirm_request(
                    &mut first,
                    Command::SubscribeRequest,
                    Command::SubscribeResponse,
                )
                .await;
                let PackageBody::Subscription(subscription) = package.body else {
                    panic!("expected subscription body");
                };
                subscriptions.push(serde_json::to_value(subscription).unwrap());
            }
            // Wait until subscribe() has recorded both confirmed subscriptions.
            deliver_rx.await.unwrap();

            let mut delivery = message::build_message_package(
                &EventMeshMessage::new("orders", "failed delivery").unwrap(),
                Command::RequestToClient,
            )
            .unwrap();
            delivery.header.seq = Some("failed".into());
            let mut stale = message::build_message_package(
                &EventMeshMessage::new("orders", "old session delivery").unwrap(),
                Command::AsyncMessageToClient,
            )
            .unwrap();
            stale.header.seq = Some("stale".into());
            first.feed(delivery.clone()).await.unwrap();
            first.feed(stale).await.unwrap();
            first.flush().await.unwrap();
            match first.next().await {
                None | Some(Err(_)) => {}
                Some(Ok(package)) => panic!(
                    "a failed handler must close its session without reply or ACK: {package:?}"
                ),
            }

            let (stream, _) = listener.accept().await.unwrap();
            let mut second = Framed::new(stream, TcpCodec::new());
            confirm_request(&mut second, Command::HelloRequest, Command::HelloResponse).await;
            for expected in subscriptions {
                let replay = confirm_request(
                    &mut second,
                    Command::SubscribeRequest,
                    Command::SubscribeResponse,
                )
                .await;
                let PackageBody::Subscription(subscription) = replay.body else {
                    panic!("expected replayed subscription body");
                };
                assert_eq!(serde_json::to_value(subscription).unwrap(), expected);
            }
            confirm_request(&mut second, Command::ListenRequest, Command::ListenResponse).await;

            // Simulate Runtime redelivery only after the new session is ready.
            second.send(delivery).await.unwrap();
            let ack = second.next().await.unwrap().unwrap();
            assert_eq!(ack.header.cmd, Command::RequestToClientAck);
            assert_eq!(ack.header.seq.as_deref(), Some("failed"));

            let mut next = message::build_message_package(
                &EventMeshMessage::new("audit", "new delivery").unwrap(),
                Command::AsyncMessageToClient,
            )
            .unwrap();
            next.header.seq = Some("next".into());
            second.send(next).await.unwrap();
            let ack = second.next().await.unwrap().unwrap();
            assert_eq!(ack.header.cmd, Command::AsyncMessageToClientAck);
            assert_eq!(ack.header.seq.as_deref(), Some("next"));
            second
        });

        let calls = Arc::new(AtomicUsize::new(0));
        let consumer = TcpConsumer::connect(
            TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
                .with_reconnect(
                    ReconnectPolicy::default()
                        .with_initial_backoff(Duration::from_millis(20))
                        .with_max_backoff(Duration::from_millis(20))
                        .with_max_retries(3),
                )
                .with_control_timeout(Duration::from_secs(3))
                .with_heartbeat_interval(Duration::from_secs(60)),
            &ConsumerOptions::new("g"),
            FailsOnce {
                calls: Arc::clone(&calls),
                failure,
            },
            None::<std::future::Ready<()>>,
        )
        .await
        .unwrap();
        consumer
            .subscribe(&[
                Subscription::new("orders").with_delivery_type(DeliveryType::Sync),
                Subscription::new("audit"),
            ])
            .await
            .unwrap();
        deliver_tx.send(()).unwrap();

        let _server_connection = tokio::time::timeout(Duration::from_secs(5), server)
            .await
            .expect("handler failure must reconnect and replay subscriptions")
            .unwrap();
        assert!(consumer.conn.is_active());
        assert_eq!(
            calls.load(Ordering::SeqCst),
            3,
            "stale frames must be discarded"
        );
        assert_eq!(consumer.subscriptions.lock().await.len(), 2);
        consumer.request_shutdown();
        let reason = tokio::time::timeout(Duration::from_secs(3), consumer.wait_for_shutdown())
            .await
            .expect("consumer must still shut down cleanly");
        assert!(matches!(reason, ShutdownReason::Cancelled));
    }

    #[tokio::test]
    async fn handler_panic_before_future_reconnects_and_replays_subscriptions() {
        assert_handler_failure_recovers(HandlerFailure::PanicBeforeFuture).await;
    }

    #[tokio::test]
    async fn handler_panic_during_poll_reconnects_and_replays_subscriptions() {
        assert_handler_failure_recovers(HandlerFailure::PanicDuringPoll).await;
    }

    #[tokio::test]
    async fn handler_error_reconnects_and_replays_subscriptions() {
        assert_handler_failure_recovers(HandlerFailure::Error).await;
    }

    #[tokio::test]
    async fn late_handler_failure_keeps_replacement_session() {
        struct BlockedFailure {
            calls: Arc<AtomicUsize>,
            started: Mutex<Option<tokio::sync::oneshot::Sender<()>>>,
            release: Mutex<tokio::sync::oneshot::Receiver<()>>,
        }

        impl MessageHandler for BlockedFailure {
            async fn handle(&self, _: Message) -> Result<Option<Message>> {
                if self.calls.fetch_add(1, Ordering::SeqCst) == 0 {
                    self.started.lock().await.take().unwrap().send(()).unwrap();
                    (&mut *self.release.lock().await).await.unwrap();
                    return Err(EventMeshError::Tcp("failure after I/O reconnect".into()));
                }
                Ok(None)
            }
        }

        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (started_tx, started_rx) = tokio::sync::oneshot::channel();
        let (release_tx, release_rx) = tokio::sync::oneshot::channel();
        let (reconnected_tx, reconnected_rx) = tokio::sync::oneshot::channel();
        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut first = Framed::new(stream, TcpCodec::new());
            confirm_request(&mut first, Command::HelloRequest, Command::HelloResponse).await;
            confirm_request(&mut first, Command::ListenRequest, Command::ListenResponse).await;
            confirm_request(
                &mut first,
                Command::SubscribeRequest,
                Command::SubscribeResponse,
            )
            .await;
            let delivery = message::build_message_package(
                &EventMeshMessage::new("orders", "payload").unwrap(),
                Command::AsyncMessageToClient,
            )
            .unwrap();
            first.send(delivery.clone()).await.unwrap();
            started_rx.await.unwrap();
            drop(first);

            // An I/O reconnect completes while the original handler is blocked.
            let (stream, _) = listener.accept().await.unwrap();
            let mut second = Framed::new(stream, TcpCodec::new());
            confirm_request(&mut second, Command::HelloRequest, Command::HelloResponse).await;
            reconnected_tx.send(()).unwrap();
            // The late failure belongs to the retired socket. It must neither
            // close this healthy replacement nor ACK the old delivery here.
            confirm_request(
                &mut second,
                Command::SubscribeRequest,
                Command::SubscribeResponse,
            )
            .await;
            confirm_request(&mut second, Command::ListenRequest, Command::ListenResponse).await;
            second.send(delivery.clone()).await.unwrap();
            let ack = second.next().await.unwrap().unwrap();
            assert_eq!(ack.header.cmd, Command::AsyncMessageToClientAck);
            assert_eq!(ack.header.seq, delivery.header.seq);
            second
        });
        let calls = Arc::new(AtomicUsize::new(0));
        let consumer = TcpConsumer::connect(
            TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
                .with_reconnect(
                    ReconnectPolicy::default()
                        .with_initial_backoff(Duration::from_millis(20))
                        .with_max_backoff(Duration::from_millis(20)),
                )
                .with_control_timeout(Duration::from_secs(3))
                .with_heartbeat_interval(Duration::from_secs(60)),
            &ConsumerOptions::new("g"),
            BlockedFailure {
                calls: Arc::clone(&calls),
                started: Mutex::new(Some(started_tx)),
                release: Mutex::new(release_rx),
            },
            None::<std::future::Ready<()>>,
        )
        .await
        .unwrap();
        consumer
            .subscribe(&[Subscription::new("orders")])
            .await
            .unwrap();
        tokio::time::timeout(Duration::from_secs(3), async {
            reconnected_rx.await.unwrap();
            while !consumer.conn.is_active() {
                tokio::task::yield_now().await;
            }
        })
        .await
        .expect("I/O reconnect must finish while the handler is blocked");
        release_tx.send(()).unwrap();
        let _server_connection = tokio::time::timeout(Duration::from_secs(5), server)
            .await
            .expect("late failure must recover on the existing replacement session")
            .unwrap();
        assert_eq!(calls.load(Ordering::SeqCst), 2);
        consumer.shutdown().await;
    }

    /// A reply that fails the TCP CloudEvents compatibility check must not ACK
    /// the request or leave the session accepting deliveries.
    #[cfg(feature = "cloud_events")]
    struct ReplyEncodingFailingListener;

    #[cfg(feature = "cloud_events")]
    impl MessageHandler for ReplyEncodingFailingListener {
        async fn handle(&self, _: Message) -> Result<Option<Message>> {
            use cloudevents::{EventBuilder, EventBuilderV10};

            let reply = EventBuilderV10::new()
                .id("reply")
                .source("urn:test")
                .ty("reply")
                .subject("topic")
                .data("text/plain", "reply")
                .build()
                .unwrap();
            Ok(Some(Message::CloudEvent(reply)))
        }
    }

    #[test]
    fn public_message_rejects_unknown_protocol() {
        let mut package = message::build_message_package(
            &EventMeshMessage::new("orders", "created").unwrap(),
            Command::AsyncMessageToClient,
        )
        .unwrap();
        package.header.set_property("protocoltype", "openmessage");

        assert!(decode_message(&package).is_none());
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
        let decoded = decode_message(&package).expect("decode message");
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

    async fn failed_delivery_consumer(
        reconnect: ReconnectPolicy,
    ) -> (TcpConsumer<FailingListener>, TcpListener) {
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
                    .build()
                    .unwrap(),
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
            listener
        });

        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_reconnect(reconnect)
            .with_control_timeout(Duration::from_secs(3))
            .with_heartbeat_interval(Duration::from_secs(60));
        let consumer = TcpConsumer::connect(
            config,
            &ConsumerOptions::new("g"),
            FailingListener,
            None::<std::future::Ready<()>>,
        )
        .await
        .expect("connect");

        let listener = tokio::time::timeout(Duration::from_secs(5), server)
            .await
            .expect("failed delivery must close the old socket without requiring join")
            .unwrap();
        (consumer, listener)
    }

    #[tokio::test]
    async fn listener_error_stops_without_ack_when_reconnect_disabled() {
        let (consumer, _listener) =
            failed_delivery_consumer(ReconnectPolicy::default().with_enabled(false)).await;
        let reason = tokio::time::timeout(Duration::from_secs(3), consumer.wait_for_shutdown())
            .await
            .expect("disabled reconnect must stop the consumer");
        assert!(matches!(reason, ShutdownReason::ChannelClosed));
        assert!(
            !consumer.conn.is_active(),
            "listener failure must stop the connection I/O task without requiring join"
        );
    }

    #[tokio::test]
    async fn listener_error_stops_when_reconnect_attempts_are_exhausted() {
        let (consumer, listener) = failed_delivery_consumer(
            ReconnectPolicy::default()
                .with_max_retries(2)
                .with_initial_backoff(Duration::from_millis(20))
                .with_max_backoff(Duration::from_millis(20)),
        )
        .await;
        drop(listener);
        let reason = tokio::time::timeout(Duration::from_secs(3), consumer.wait_for_shutdown())
            .await
            .expect("exhausted reconnect must stop the consumer");
        assert!(matches!(reason, ShutdownReason::ChannelClosed));
    }

    #[tokio::test]
    async fn shutdown_cancels_handler_failure_reconnect_backoff() {
        let (consumer, _listener) = failed_delivery_consumer(
            ReconnectPolicy::default()
                .with_initial_backoff(Duration::from_secs(60))
                .with_max_backoff(Duration::from_secs(60)),
        )
        .await;
        consumer.request_shutdown();
        let reason = tokio::time::timeout(Duration::from_secs(3), consumer.wait_for_shutdown())
            .await
            .expect("shutdown must interrupt reconnect backoff");
        assert!(matches!(reason, ShutdownReason::Cancelled));
        assert!(!consumer.conn.is_active());
    }

    #[cfg(feature = "cloud_events")]
    #[tokio::test]
    async fn reply_encoding_error_closes_tcp_connection_without_ack() {
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

            use cloudevents::{EventBuilder, EventBuilderV10};
            let request = EventBuilderV10::new()
                .id("request")
                .source("urn:test")
                .ty("request")
                .subject("topic")
                .data("application/cloudevents+json", "payload")
                .build()
                .unwrap();
            let delivery =
                message::build_cloud_event_package(&request, Command::RequestToClient).unwrap();
            framed.send(delivery).await.unwrap();

            tokio::time::timeout(Duration::from_secs(3), async {
                loop {
                    match framed.next().await {
                        Some(Ok(pkg)) if pkg.header.cmd == Command::ClientGoodbyeRequest => {}
                        Some(Ok(pkg)) => {
                            panic!(
                                "reply encoding failure must not send a reply or ACK; got {:?}",
                                pkg.header.cmd
                            )
                        }
                        Some(Err(_)) | None => break,
                    }
                }
            })
            .await
            .expect("reply encoding failure should close the TCP connection promptly");
        });

        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_reconnect(ReconnectPolicy::default().with_enabled(false))
            .with_control_timeout(Duration::from_secs(3))
            .with_heartbeat_interval(Duration::from_secs(60));
        let consumer = TcpConsumer::connect(
            config,
            &ConsumerOptions::new("g"),
            ReplyEncodingFailingListener,
            None::<std::future::Ready<()>>,
        )
        .await
        .expect("connect");

        server.await.unwrap();
        assert!(
            !consumer.conn.is_active(),
            "reply encoding failure must stop the connection I/O task without requiring join"
        );
    }

    #[tokio::test]
    async fn native_handler_reply_preserves_whitespace_and_sends_ack() {
        native_handler_reply(" \t\n", 4000).await;
    }

    #[tokio::test]
    async fn native_handler_reply_preserves_inherited_ttl_outside_publish_range() {
        native_handler_reply("reply", i64::from(i32::MAX) + 1).await;
    }

    async fn native_handler_reply(content: &'static str, ttl: i64) {
        struct Reply(&'static str);
        impl MessageHandler for Reply {
            async fn handle(&self, request: Message) -> Result<Option<Message>> {
                let request = request.into_event_mesh()?;
                Ok(Some(EventMeshMessage::new(request.topic(), self.0)?.into()))
            }
        }

        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (delivered_tx, delivered_rx) = tokio::sync::oneshot::channel();
        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());
            for (request, response) in [
                (Command::HelloRequest, Command::HelloResponse),
                (Command::ListenRequest, Command::ListenResponse),
            ] {
                confirm_request(&mut framed, request, response).await;
            }
            let request = Package::new(Header::new(Command::RequestToClient, "request-seq"))
                .with_body(PackageBody::Text(
                    serde_json::json!({
                        "topic": "orders", "body": "request",
                        "properties": {"ttl": ttl.to_string(), "correlation99id": "request-id"}
                    })
                    .to_string(),
                ));
            framed.send(request).await.unwrap();

            let reply = framed
                .next()
                .await
                .expect("handler reply must not close the session")
                .unwrap();
            assert_eq!(reply.header.cmd, Command::ResponseToServer);
            let decoded = message::parse_message(&reply.body).unwrap();
            assert_eq!(decoded.content(), content);
            assert_eq!(decoded.ttl_millis(), Some(ttl));
            assert_eq!(
                decoded
                    .delivery_context()
                    .unwrap()
                    .attribute("correlation99id"),
                Some("request-id")
            );
            let ack = framed
                .next()
                .await
                .expect("handler reply must be followed by ACK")
                .unwrap();
            assert_eq!(ack.header.cmd, Command::RequestToClientAck);
            assert_eq!(ack.header.seq.as_deref(), Some("request-seq"));
            delivered_tx.send(()).unwrap();
            while let Some(frame) = framed.next().await {
                assert_eq!(frame.unwrap().header.cmd, Command::ClientGoodbyeRequest);
            }
        });
        let consumer = TcpConsumer::connect(
            TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
                .with_reconnect(ReconnectPolicy::default().with_enabled(false))
                .with_heartbeat_interval(Duration::from_secs(60)),
            &ConsumerOptions::new("reply-test"),
            Reply(content),
            None::<std::future::Ready<()>>,
        )
        .await
        .unwrap();
        let delivered = tokio::time::timeout(Duration::from_secs(3), delivered_rx).await;
        consumer.request_shutdown();
        let reason = tokio::time::timeout(Duration::from_secs(3), consumer.wait_for_shutdown())
            .await
            .unwrap();
        server.await.unwrap();
        delivered.unwrap().unwrap();
        assert!(matches!(reason, ShutdownReason::Cancelled));
    }

    #[test]
    fn request_reply_inherits_routing_properties_without_overwriting_reply() {
        let request = EventMeshMessage::builder()
            .topic("request-topic")
            .content("request")
            .ttl_millis(4000)
            .prop("correlation-id", "request-id")
            .build()
            .unwrap();
        let reply = EventMeshMessage::builder()
            .topic("reply-topic")
            .content("reply")
            .prop("correlation-id", "reply-id")
            .build()
            .unwrap();

        let request = crate::transport::decode_native_message(
            request,
            std::collections::HashMap::from([
                ("cluster".into(), "remote-cluster".into()),
                ("correlation-id".into(), "request-id".into()),
                ("ttl".into(), "4000".into()),
            ]),
        )
        .unwrap();
        let mut reply = Message::from(reply);
        inherit_request_metadata(&mut reply, &Message::from(request));
        let pkg = encode_reply(&reply).expect("encode reply");
        let encoded = message::parse_message(&pkg.body).expect("decode reply");

        assert_eq!(encoded.ttl_millis(), Some(4000));
        assert_eq!(encoded.get_prop("ttl"), None);
        assert_eq!(encoded.get_prop("cluster"), None);
        assert_eq!(
            encoded.delivery_context().unwrap().attribute("cluster"),
            Some("remote-cluster")
        );
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
        let reply = EventBuilderV10::new()
            .id("reply")
            .source("urn:test")
            .ty("test")
            .build()
            .expect("build reply");

        let mut reply = Message::from(reply);
        inherit_request_metadata(&mut reply, &Message::from(request));
        assert_eq!(
            match reply {
                Message::CloudEvent(reply) => reply.extension("cluster").unwrap().to_string(),
                other => panic!("expected CloudEvent, got {other:?}"),
            },
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

        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_control_timeout(Duration::from_secs(3))
            .with_heartbeat_interval(Duration::from_secs(60));

        let consumer = TcpConsumer::connect(
            config,
            &ConsumerOptions::new("g"),
            NoopListener,
            None::<std::future::Ready<()>>,
        )
        .await
        .expect("connect");

        // Subscribe to two topics. Each call records into `self.subscriptions`.
        let item_a = Subscription::new("A").with_delivery_type(DeliveryType::Sync);
        let item_b = Subscription::new("B").with_delivery_type(DeliveryType::Sync);
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
        let item_a = Subscription::new("A").with_delivery_type(DeliveryType::Sync);
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

        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_control_timeout(Duration::from_secs(3))
            .with_heartbeat_interval(Duration::from_secs(60));

        let consumer = TcpConsumer::connect(
            config,
            &ConsumerOptions::new("g"),
            NoopListener,
            None::<std::future::Ready<()>>,
        )
        .await
        .expect("connect");

        let item = Subscription::new("A").with_delivery_type(DeliveryType::Sync);
        consumer.subscribe(&[item]).await.expect("subscribe");

        // Server returns code 1 → must be Err, not Ok.
        let item = Subscription::new("A").with_delivery_type(DeliveryType::Sync);
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

    #[tokio::test]
    async fn rejected_reconnect_replay_stops_the_consumer() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();

        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut first = Framed::new(stream, TcpCodec::new());
            let hello = first.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            first
                .send(Package::new(Header::new(
                    Command::HelloResponse,
                    hello.header.seq.unwrap_or_default(),
                )))
                .await
                .unwrap();
            let listen = first.next().await.unwrap().unwrap();
            assert_eq!(listen.header.cmd, Command::ListenRequest);
            first
                .send(Package::new(Header::new(
                    Command::ListenResponse,
                    listen.header.seq.unwrap_or_default(),
                )))
                .await
                .unwrap();
            let subscribe = first.next().await.unwrap().unwrap();
            assert_eq!(subscribe.header.cmd, Command::SubscribeRequest);
            first
                .send(Package::new(Header::new(
                    Command::SubscribeResponse,
                    subscribe.header.seq.unwrap_or_default(),
                )))
                .await
                .unwrap();
            drop(first);

            let (stream, _) = listener.accept().await.unwrap();
            let mut second = Framed::new(stream, TcpCodec::new());
            let hello = second.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            second
                .send(Package::new(Header::new(
                    Command::HelloResponse,
                    hello.header.seq.unwrap_or_default(),
                )))
                .await
                .unwrap();
            let replay = second.next().await.unwrap().unwrap();
            assert_eq!(replay.header.cmd, Command::SubscribeRequest);
            let mut rejection = Header::new(
                Command::SubscribeResponse,
                replay.header.seq.unwrap_or_default(),
            );
            rejection.code = 17;
            rejection.desc = Some("replay rejected".into());
            second.send(Package::new(rejection)).await.unwrap();

            let _ = tokio::time::timeout(Duration::from_secs(3), second.next()).await;
        });

        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_control_timeout(Duration::from_secs(3))
            .with_heartbeat_interval(Duration::from_secs(60))
            .with_reconnect(
                ReconnectPolicy::default()
                    .with_enabled(true)
                    .with_initial_backoff(Duration::from_millis(20))
                    .with_max_backoff(Duration::from_millis(50)),
            );
        let consumer = TcpConsumer::connect(
            config,
            &ConsumerOptions::new("g"),
            NoopListener,
            None::<std::future::Ready<()>>,
        )
        .await
        .expect("connect");
        consumer
            .subscribe(&[Subscription::new("orders")])
            .await
            .expect("initial subscribe");

        let reason = tokio::time::timeout(Duration::from_secs(5), consumer.wait_for_shutdown())
            .await
            .expect("rejected replay must stop the consumer");
        assert!(matches!(
            reason,
            ShutdownReason::Error(message) if message.contains("re-subscribe")
        ));
        server.await.unwrap();
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

        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_control_timeout(Duration::from_secs(3))
            .with_heartbeat_interval(Duration::from_secs(60));

        let consumer = TcpConsumer::connect(
            config,
            &ConsumerOptions::new("g"),
            NoopListener,
            None::<std::future::Ready<()>>,
        )
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
