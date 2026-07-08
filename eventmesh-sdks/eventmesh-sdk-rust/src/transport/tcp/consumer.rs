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

use std::future::{Future, IntoFuture};
use std::panic::AssertUnwindSafe;
use std::pin::Pin;
use std::sync::Arc;

use futures::FutureExt;
use tokio::sync::Mutex;
use tokio_util::sync::CancellationToken;
use tracing::{debug, info, warn};

use crate::config::TcpClientConfig;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, PublishResponse, SubscriptionItem};
use crate::transport::tcp::connection::TcpConnection;
use crate::transport::tcp::frame::{Command, Package, PackageBody, UserAgent};
use crate::transport::tcp::message;
use crate::transport::Subscriber;
use crate::MessageListener;

/// TCP-based consumer, generic over the user's [`MessageListener`] type.
///
/// Created via [`TcpConsumer::connect`], which opens a TCP connection, performs
/// the HELLO handshake (role = sub), and starts the background heartbeat.
///
/// Call [`TcpConsumer::listen`] to subscribe + enter the receive loop. This
/// returns a [`ListenServe`] driver that implements [`IntoFuture`] — axum-style,
/// a single `.await` drives everything:
///
/// ```ignore
/// consumer.listen(items)?.with_graceful_shutdown(sig).await?;
/// ```
pub struct TcpConsumer<L: MessageListener<Message = EventMeshMessage>> {
    conn: Arc<TcpConnection>,
    config: TcpClientConfig,
    listener: Arc<L>,
    shutdown: CancellationToken,
    subscriptions: Arc<Mutex<Vec<SubscriptionItem>>>,
}

impl<L: MessageListener<Message = EventMeshMessage>> TcpConsumer<L> {
    /// Connect to the EventMesh TCP endpoint and perform the HELLO handshake
    /// (role = sub).
    ///
    /// The reconnect policy from the config controls automatic reconnection
    /// after I/O failures (enabled by default). When a reconnect succeeds,
    /// the receive loop automatically replays all subscriptions and re-issues
    /// `LISTEN_REQUEST`.
    pub async fn connect(config: TcpClientConfig, listener: L) -> Result<Self> {
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

        Ok(Self {
            conn: Arc::new(conn),
            config,
            listener: Arc::new(listener),
            shutdown: CancellationToken::new(),
            subscriptions: Arc::new(Mutex::new(Vec::new())),
        })
    }

    /// Prepare a subscription + receive-loop driver. Returns synchronously;
    /// the actual I/O happens on the first `.await` of the returned
    /// [`ListenServe`].
    ///
    /// The driver sends `SUBSCRIBE_REQUEST` for each topic, then
    /// `LISTEN_REQUEST`, and enters the receive loop — dispatching delivered
    /// messages to the listener and sending ACKs / replies.
    pub fn listen(&self, items: Vec<SubscriptionItem>) -> Result<ListenServe<L>> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }
        Ok(ListenServe {
            conn: Arc::clone(&self.conn),
            items,
            listener: Arc::clone(&self.listener),
            config: self.config.clone(),
            shutdown: self.shutdown.clone(),
            subscriptions: Arc::clone(&self.subscriptions),
            subscribed: false,
        })
    }

    /// Add more subscriptions without restarting the receive loop. Sends a
    /// `SUBSCRIBE_REQUEST` for each item via `io()`.
    pub async fn add_subscription(&self, items: &[SubscriptionItem]) -> Result<()> {
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

    /// Graceful shutdown: cancel the shared token and shut down the connection.
    pub async fn shutdown(&self) {
        self.shutdown.cancel();
        self.conn.shutdown().await;
    }

    /// Current config.
    pub fn config(&self) -> &TcpClientConfig {
        &self.config
    }
}

impl<L: MessageListener<Message = EventMeshMessage>> Subscriber for TcpConsumer<L> {
    async fn subscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        // Perform subscription synchronously so broker rejections (ACL, bad
        // topic, server not RUNNING) surface as an `Err` to the caller instead
        // of being swallowed into a `warn!` log inside the spawned task. This
        // matches `add_subscription`, which is what we delegate to: it records
        // each topic in `self.subscriptions` only after the server confirms it.
        self.add_subscription(&items).await?;

        // Spawn the listen + receive loop in the background. The driver skips
        // re-subscription since `add_subscription` already confirmed every
        // topic (see `ListenServe::subscribed`).
        let mut serve = self.listen(items)?;
        serve.subscribed = true;
        tokio::spawn(async move {
            if let Err(e) = serve.await {
                warn!("listen driver exited with error: {e}");
            }
        });
        Ok(PublishResponse::new(
            Some(0),
            Some("subscribed".into()),
            None,
        ))
    }

    async fn unsubscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "unsubscribe items must not be empty".into(),
            ));
        }
        let unsub_pkg = message::unsubscribe(&items);
        let resp = self.conn.io(unsub_pkg, self.config.timeout).await?;
        let response = message::response_from_pkg(&resp);

        if response.is_success() {
            let mut subs = self.subscriptions.lock().await;
            // The runtime's TCP `UnSubscribeProcessor` ignores the request
            // body and unsubscribes **every** topic in the session (it reads
            // `session.getSessionContext().getSubscribeTopics()` and removes
            // them all). This mirrors the Java SDK, whose
            // `MessageUtils.unsubscribe()` sends an `UNSUBSCRIBE_REQUEST` with
            // no body. So a successful response means the whole subscription
            // set is gone on the server regardless of which topics the caller
            // passed — clear the local map entirely to stay consistent. The
            // `Subscriber` trait signature is shared with gRPC/HTTP, which do
            // support per-topic unsubscribe, so we keep accepting `items` but
            // warn when the caller tried to narrow the scope.
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
        }
        Ok(response)
    }
}

impl<L: MessageListener<Message = EventMeshMessage>> Drop for TcpConsumer<L> {
    fn drop(&mut self) {
        self.shutdown.cancel();
    }
}

// ---------------------------------------------------------------------------
// ListenServe driver
// ---------------------------------------------------------------------------

/// Foreground driver for a TCP subscription + receive loop.
///
/// Returned (synchronously) by [`TcpConsumer::listen`]. Subscribe + listen
/// happen lazily on the first `.await`, so awaiting this driver both subscribes
/// and runs the receive loop in one step — dispatching delivered messages to
/// the registered listener and sending back ACKs/replies until the connection
/// closes or a graceful shutdown fires.
///
/// Bind an external trigger (Ctrl-C, a `oneshot`, etc.) with
/// [`ListenServe::with_graceful_shutdown`].
pub struct ListenServe<L: MessageListener<Message = EventMeshMessage>> {
    conn: Arc<TcpConnection>,
    items: Vec<SubscriptionItem>,
    listener: Arc<L>,
    config: TcpClientConfig,
    shutdown: CancellationToken,
    subscriptions: Arc<Mutex<Vec<SubscriptionItem>>>,
    /// Whether the topics in `items` have already been confirmed by the server
    /// (via [`TcpConsumer::add_subscription`]). When `true`, [`IntoFuture`]
    /// skips the subscription phase and goes straight to LISTEN + receive loop.
    /// Set by [`Subscriber::subscribe`] so broker rejections surface to the
    /// caller rather than being swallowed in the spawned task.
    subscribed: bool,
}

impl<L: MessageListener<Message = EventMeshMessage>> ListenServe<L> {
    /// Bind an external shutdown signal. When `signal` resolves the consumer's
    /// shared cancellation token is triggered, which stops the receive loop.
    pub fn with_graceful_shutdown(self, signal: impl Future<Output = ()> + Send + 'static) -> Self {
        let token = self.shutdown.clone();
        tokio::spawn(async move {
            tokio::select! {
                _ = signal => token.cancel(),
                _ = token.cancelled() => {}
            }
        });
        self
    }
}

impl<L: MessageListener<Message = EventMeshMessage>> IntoFuture for ListenServe<L> {
    type Output = Result<()>;
    type IntoFuture = Pin<Box<dyn Future<Output = Result<()>> + Send>>;

    fn into_future(self) -> Self::IntoFuture {
        let Self {
            conn,
            items,
            listener,
            config,
            shutdown,
            subscriptions,
            subscribed,
        } = self;

        Box::pin(async move {
            // 1. Subscribe to each topic — unless the caller already did (e.g.
            //    `Subscriber::subscribe` calls `add_subscription` inline so
            //    broker rejections surface synchronously).
            if !subscribed {
                for item in &items {
                    let sub_pkg = message::subscribe(&item.topic, std::slice::from_ref(item));
                    let resp = conn.io(sub_pkg, config.timeout).await?;
                    let response = message::response_from_pkg(&resp);
                    if !response.is_success() {
                        return Err(EventMeshError::Server {
                            code: response.code.unwrap_or(-1) as i32,
                            message: response
                                .message
                                .unwrap_or_else(|| "subscribe failed".into()),
                        });
                    }
                    // Record the subscription ONLY after the server confirms it,
                    // mirroring add_subscription. This prevents phantom entries in
                    // self.subscriptions if the driver fails partway through.
                    subscriptions.lock().await.push(item.clone());
                    debug!(
                        topic = ?item.topic,
                        cmd = ?resp.header.cmd,
                        "subscribed"
                    );
                }
            }

            // 2. Send LISTEN_REQUEST to enter receive mode.
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

            // 3. Take the inbound receiver (only available once).
            let mut inbound_rx = conn
                .take_inbound_rx()
                .await
                .ok_or_else(|| EventMeshError::Tcp("inbound receiver already taken".into()))?;

            // 4. Take the reconnect-event receiver. When the connection task
            //    auto-reconnects, it sends `()` on this channel. The receive
            //    loop uses it to replay subscriptions + LISTEN without dropping
            //    the user's driver. `None` means reconnect is disabled or the
            //    receiver was already taken — the loop simply won't handle
            //    reconnects, which is fine for the non-reconnect case.
            let mut reconnect_rx = conn.take_reconnect_rx().await;

            // 5. Receive loop.
            loop {
                tokio::select! {
                    biased;
                    _ = shutdown.cancelled() => {
                        debug!("receive loop shutting down");
                        break;
                    }

                    // Reconnect event: the connection task has re-established
                    // the TCP session. Replay all subscriptions + LISTEN so the
                    // new server-side session mirrors the old one. Mirrors the
                    // Java SDK's `EventMeshMessageTCPSubClient.reconnect()`.
                    event = async {
                        match reconnect_rx.as_mut() {
                            Some(rx) => rx.recv().await,
                            // No reconnect channel: this arm never fires.
                            None => std::future::pending().await,
                        }
                    } => {
                        if event.is_none() {
                            info!("reconnect channel closed, exiting receive loop");
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
                                if !handle_inbound(&pkg, &conn, &*listener).await {
                                    info!("receive loop stopping after REDIRECT_TO_CLIENT");
                                    break;
                                }
                            }
                            None => {
                                info!("inbound channel closed, exiting receive loop");
                                break;
                            }
                        }
                    }
                }
            }

            Ok(())
        })
    }
}

/// Dispatch an inbound package: parse the message, invoke the listener, send
/// any reply, then send the matching ACK.
///
/// The ACK is sent **after** the listener returns, mirroring the Java SDK's
/// `AbstractEventMeshTCPSubHandler.channelRead0` ordering (`callback` →
/// `response`). Note the Java handler always ACKs regardless of whether the
/// body parsed — we do the same: an unparseable body is logged and skipped (the
/// listener is not invoked) but the ACK is still sent, matching Java's
/// `callback(getProtocolMessage(msg), ctx); response(ack)` flow. This means
/// at-least-once redelivery applies to crashes *between* the listener returning
/// and the ACK being written, not to client-side parse failures.
///
/// Returns `true` to keep the receive loop running, or `false` to stop it. The
/// only case that returns `false` is `REDIRECT_TO_CLIENT`: the runtime has
/// advertised a new node and will unconditionally close this session after a
/// 30s grace period (`closeSessionIfTimeout`), so stopping the loop lets the
/// caller reconnect immediately instead of stalling until the forced
/// disconnect.
async fn handle_inbound<L: MessageListener<Message = EventMeshMessage>>(
    pkg: &Package,
    conn: &TcpConnection,
    listener: &L,
) -> bool {
    let ack_cmd = match pkg.header.cmd {
        Command::RequestToClient => Some(Command::RequestToClientAck),
        Command::AsyncMessageToClient => Some(Command::AsyncMessageToClientAck),
        Command::BroadcastMessageToClient => Some(Command::BroadcastMessageToClientAck),
        Command::ServerGoodbyeRequest => {
            // The server initiated a goodbye (shutdown / redirect). The Java
            // runtime's `GoodbyeProcessor` expects the client to reply with
            // `SERVER_GOODBYE_RESPONSE` (distinct from the client-initiated
            // `CLIENT_GOODBYE_REQUEST`/`CLIENT_GOODBYE_RESPONSE` pair). The
            // server closes the session regardless (`closeSessionIfTimeout` is
            // unconditional), so this ACK is best-effort: a write failure only
            // means the socket is already gone.
            info!("server goodbye received, sending SERVER_GOODBYE_RESPONSE");
            let resp = message::ack(Command::ServerGoodbyeResponse, pkg);
            if let Err(e) = conn.send(resp).await {
                warn!(error = %e, "failed to send SERVER_GOODBYE_RESPONSE");
            }
            return true;
        }
        Command::RedirectToClient => {
            // The runtime sends REDIRECT_TO_CLIENT during rebalance (body =
            // RedirectInfo{ip,port}), then closes this session after a 30s
            // grace period regardless of the client's reaction. There is no
            // ACK for this command. Surface the advertised target and stop the
            // receive loop now so the caller can reconnect to it instead of
            // waiting for the forced disconnect, which would freeze delivery
            // for up to 30s.
            match pkg.body {
                PackageBody::RedirectInfo(ref ri) => {
                    info!(
                        ip = %ri.ip,
                        port = ri.port,
                        "received REDIRECT_TO_CLIENT; stopping receive loop so the \
                         caller can reconnect to the advertised EventMesh node"
                    );
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
            return false;
        }
        cmd => {
            warn!(?cmd, "unexpected inbound command, ignoring");
            return true;
        }
    };

    // Parse message body and invoke listener BEFORE sending the ACK.
    //
    // CloudEvents bodies (`protocoltype=cloudevents`) arrive as CloudEvents
    // JSON in the package body. When the `cloud_events` feature is enabled,
    // they are parsed and converted to EventMeshMessage so the listener
    // handles them uniformly. Without the feature, the message is logged and
    // skipped (the ACK is still sent so the broker doesn't redeliver).
    let parsed = if message::is_cloudevents(pkg) {
        #[cfg(feature = "cloud_events")]
        {
            message::parse_cloud_event(&pkg.body).map(|ev| message::cloud_event_to_message(&ev))
        }
        #[cfg(not(feature = "cloud_events"))]
        {
            warn!(
                cmd = ?pkg.header.cmd,
                "received CloudEvents message but the cloud_events feature is disabled; skipping"
            );
            None
        }
    } else {
        message::parse_message(&pkg.body)
    };

    if let Some(msg) = parsed {
        debug!(topic = ?msg.topic, "dispatching to listener");
        // Snapshot the request's wire properties before the listener
        // consumes the message. When the listener returns a fresh reply we
        // merge these back in so the broker can correlate the reply with the
        // original request — RocketMQ reply-to / correlation-id and similar
        // extensions ride in `props`, and a hand-built reply drops them,
        // causing `RESPONSE_TO_SERVER` to be unmatchable and
        // `TcpProducer::request_reply` to time out. The gRPC consumer does
        // the same merge in `build_reply`.
        let request_props = msg.props.clone();
        // Listener may return a reply (for REQUEST_TO_CLIENT). The Java SDK
        // sends RESPONSE_TO_SERVER inside the callback, before the ACK.
        // Guard against a panicking listener so it cannot kill the receive
        // loop (which would stall the inbound channel and freeze heartbeats).
        match AssertUnwindSafe(listener.handle(msg)).catch_unwind().await {
            Ok(Some(mut reply)) => {
                // Carry the request's correlation metadata into the reply;
                // the reply's own values take precedence (mirrors gRPC
                // `build_reply`'s `or_insert_with` semantics).
                for (key, value) in &request_props {
                    reply
                        .props
                        .entry(key.clone())
                        .or_insert_with(|| value.clone());
                }
                match message::build_message_package(&reply, Command::ResponseToServer) {
                    Ok(reply_pkg) => {
                        if let Err(e) = conn.send(reply_pkg).await {
                            warn!(error = %e, "failed to send reply");
                        }
                    }
                    Err(e) => warn!(error = %e, "failed to serialize reply"),
                }
            }
            Ok(None) => {}
            Err(_) => warn!("message listener panicked; ACK will still be sent"),
        }
    } else {
        warn!("failed to parse inbound message body");
    }

    // Send ACK after the listener has processed the message.
    if let Some(cmd) = ack_cmd {
        let ack_pkg = message::ack(cmd, pkg);
        if let Err(e) = conn.send(ack_pkg).await {
            warn!(error = %e, "failed to send ACK");
        }
    }

    true
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
        async fn handle(&self, _: EventMeshMessage) -> Option<EventMeshMessage> {
            None
        }
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

            // 2. Reply to each SUBSCRIBE_REQUEST with SubscribeResponse (code 0).
            for _ in 0..2 {
                let req = framed.next().await.unwrap().unwrap();
                assert_eq!(req.header.cmd, Command::SubscribeRequest);
                let resp = Package::new(Header::new(
                    Command::SubscribeResponse,
                    req.header.seq.clone().unwrap_or_default(),
                ));
                framed.send(resp).await.unwrap();
            }

            // 3. Reply to the UNSUBSCRIBE_REQUEST with UnsubscribeResponse (code 0).
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

        let consumer = TcpConsumer::connect(config, NoopListener)
            .await
            .expect("connect");

        // Subscribe to two topics via `add_subscription` (avoids spawning the
        // listen/receive loop). Each call records into `self.subscriptions`.
        let item_a =
            SubscriptionItem::new("A", SubscriptionMode::CLUSTERING, SubscriptionType::SYNC);
        let item_b =
            SubscriptionItem::new("B", SubscriptionMode::CLUSTERING, SubscriptionType::SYNC);
        consumer
            .add_subscription(&[item_a, item_b])
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

    /// On rebalance the runtime sends `REDIRECT_TO_CLIENT` with an
    /// `ip`/`port` body, then closes the session after a 30s grace period.
    /// The receive loop must not ignore the frame (the old behavior silently
    /// dropped it and froze delivery until the forced disconnect); it should
    /// stop the loop promptly so the caller can reconnect to the advertised
    /// node. This loopback test drives the full codec path: the fake server
    /// sends a wire-format `REDIRECT_TO_CLIENT` frame and we assert that
    /// `ListenServe` resolves on its own (no external shutdown needed).
    #[tokio::test]
    async fn redirect_to_client_stops_receive_loop() {
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

            // 2. Reply to SUBSCRIBE_REQUEST.
            let req = framed.next().await.unwrap().unwrap();
            assert_eq!(req.header.cmd, Command::SubscribeRequest);
            framed
                .send(Package::new(Header::new(
                    Command::SubscribeResponse,
                    req.header.seq.clone().unwrap_or_default(),
                )))
                .await
                .unwrap();

            // 3. Reply to LISTEN_REQUEST.
            let req = framed.next().await.unwrap().unwrap();
            assert_eq!(req.header.cmd, Command::ListenRequest);
            framed
                .send(Package::new(Header::new(
                    Command::ListenResponse,
                    req.header.seq.clone().unwrap_or_default(),
                )))
                .await
                .unwrap();

            // 4. Send REDIRECT_TO_CLIENT with a wire-format RedirectInfo body,
            //    exactly as EventMeshTcp2Client.redirectClient2NewEventMesh
            //    does (seq = null, body = {"ip":..,"port":..}).
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

        let consumer = TcpConsumer::connect(config, NoopListener)
            .await
            .expect("connect");

        let item = SubscriptionItem::new("A", SubscriptionMode::CLUSTERING, SubscriptionType::SYNC);
        let serve = consumer.listen(vec![item]).expect("listen");

        // The redirect frame should make the driver resolve on its own, without
        // the user triggering a graceful shutdown. A 30s hang here is the
        // failure mode this test guards against.
        let result = tokio::time::timeout(Duration::from_secs(10), serve).await;
        assert!(
            result.is_ok(),
            "REDIRECT_TO_CLIENT should stop the receive loop promptly"
        );
        result.unwrap().expect("driver should exit cleanly");

        consumer.shutdown().await;
        let _ = server.await;
    }
}
