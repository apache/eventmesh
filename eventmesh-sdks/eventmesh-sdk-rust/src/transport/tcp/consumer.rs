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
use crate::model::{EventMeshMessage, PublishResponse, SubscriptionItem};
use crate::transport::tcp::connection::TcpConnection;
use crate::transport::tcp::frame::{Command, Package, PackageBody, UserAgent};
use crate::transport::tcp::message;
use crate::MessageListener;

/// TCP-based consumer, generic over the user's [`MessageListener`] type.
///
/// Created via [`TcpConsumer::connect`], which opens a TCP connection, performs
/// the HELLO handshake (role = sub), sends `LISTEN_REQUEST`, and spawns the
/// receive loop + heartbeat as background tasks.
///
/// Subscribe and unsubscribe RPCs can be called at any time after construction.
/// The background tasks are stopped when the consumer is dropped or explicitly
/// via [`shutdown`](Self::shutdown) / [`wait_for_shutdown`](Self::wait_for_shutdown).
pub struct TcpConsumer<L: MessageListener<Message = EventMeshMessage>> {
    conn: Arc<TcpConnection>,
    config: TcpClientConfig,
    _listener: std::marker::PhantomData<Arc<L>>,
    shutdown: CancellationToken,
    subscriptions: Arc<Mutex<Vec<SubscriptionItem>>>,
    driver_handle: Mutex<Option<JoinHandle<Result<()>>>>,
}

impl<L: MessageListener<Message = EventMeshMessage>> TcpConsumer<L> {
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
        );

        Ok(Self {
            conn,
            config,
            _listener: std::marker::PhantomData,
            shutdown,
            subscriptions,
            driver_handle: Mutex::new(Some(driver_handle)),
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

        if response.is_success() {
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
        }
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
    /// own (e.g. server goodbye, redirect, or I/O error), then await the
    /// driver task's clean exit.
    ///
    /// If no shutdown signal was provided at construction time, this blocks
    /// until the driver exits naturally.
    pub async fn wait_for_shutdown(&self) {
        self.shutdown.cancelled().await;
        self.conn.shutdown().await;
        if let Some(handle) = self.driver_handle.lock().await.take() {
            let _ = handle.await;
        }
    }
}

impl<L: MessageListener<Message = EventMeshMessage>> Drop for TcpConsumer<L> {
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
fn spawn_driver(
    conn: Arc<TcpConnection>,
    mut inbound_rx: tokio::sync::mpsc::Receiver<Package>,
    mut reconnect_rx: Option<tokio::sync::mpsc::Receiver<()>>,
    listener: Arc<impl MessageListener<Message = EventMeshMessage>>,
    config: TcpClientConfig,
    subscriptions: Arc<Mutex<Vec<SubscriptionItem>>>,
    shutdown: CancellationToken,
) -> JoinHandle<Result<()>> {
    tokio::spawn(async move {
        loop {
            tokio::select! {
                biased;
                _ = shutdown.cancelled() => {
                    debug!("receive loop shutting down");
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

        // Signal wait_for_shutdown that we've exited.
        shutdown.cancel();
        Ok(())
    })
}

/// Dispatch an inbound package: parse the message, invoke the listener, send
/// any reply, then send the matching ACK.
///
/// Returns `true` to keep the receive loop running, or `false` to stop it
/// (which triggers reconnection and server-side redelivery).
///
/// If the message cannot be parsed, **no ACK is sent** and the function
/// returns `false` so the connection is torn down — mirroring the Java SDK,
/// where a parse exception propagates to `exceptionCaught` and closes the
/// channel.  A listener panic likewise propagates and kills the receive
/// task; it is not silently swallowed.
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
            info!("server goodbye received, sending SERVER_GOODBYE_RESPONSE");
            let resp = message::ack(Command::ServerGoodbyeResponse, pkg);
            if let Err(e) = conn.send(resp).await {
                warn!(error = %e, "failed to send SERVER_GOODBYE_RESPONSE");
            }
            return true;
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

    let parsed = if message::is_cloudevents(pkg) {
        #[cfg(feature = "cloud_events")]
        {
            message::parse_cloud_event(&pkg.body).map(|ev| message::cloud_event_to_message(&ev))
        }
        #[cfg(not(feature = "cloud_events"))]
        {
            warn!(
                cmd = ?pkg.header.cmd,
                "received CloudEvents message but the cloud_events feature is disabled; \
                 disconnecting so the server can redeliver"
            );
            return false;
        }
    } else {
        message::parse_message(&pkg.body)
    };

    let msg = match parsed {
        Some(msg) => msg,
        None => {
            warn!("failed to parse inbound message body; disconnecting without ACK");
            return false;
        }
    };

    debug!(topic = ?msg.topic, "dispatching to listener");
    let request_props = msg.props.clone();
    // A listener panic propagates naturally and kills the receive task —
    // mirroring Java where the exception escapes to exceptionCaught and
    // closes the channel.  The message is NOT acknowledged.
    if let Some(mut reply) = listener.handle(msg).await {
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

    /// On rebalance the runtime sends `REDIRECT_TO_CLIENT` with an
    /// `ip`/`port` body, then closes the session after a 30s grace period.
    /// The receive loop must stop promptly so the caller can reconnect.
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

        // The redirect frame should make the driver exit on its own, which
        // cancels the shutdown token. wait_for_shutdown should return promptly.
        let result =
            tokio::time::timeout(Duration::from_secs(10), consumer.wait_for_shutdown()).await;
        assert!(
            result.is_ok(),
            "REDIRECT_TO_CLIENT should stop the receive loop promptly"
        );

        let _ = server.await;
    }
}
