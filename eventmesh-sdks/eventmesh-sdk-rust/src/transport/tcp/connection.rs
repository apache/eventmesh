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

//! TCP connection engine — the core of the transport.
//!
//! Corresponds to the Java SDK's `TcpClient` abstract base: manages the TCP
//! socket, the read/write loop, heartbeat, and request-response correlation
//! via a `seq`-keyed pending map of `oneshot` channels.
//!
//! ## Reconnect
//!
//! When [`ReconnectConfig::enabled`] is `true` (the default), the background
//! task automatically re-establishes the TCP connection + HELLO handshake after
//! an I/O error or server-side close. An optional reconnect-event channel
//! ([`TcpConnection::take_reconnect_rx`]) lets consumers replay their
//! subscriptions after a successful reconnect. This mirrors the Java SDK's
//! heartbeat-driven reconnect but with exponential backoff.

use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use tokio::net::TcpStream;
use tokio::sync::{mpsc, oneshot, Mutex};
use tokio::task::JoinHandle;
use tokio_stream::StreamExt;
use tokio_util::codec::Framed;
use tokio_util::sync::CancellationToken;
use tracing::{debug, info, warn};

use crate::config::ReconnectConfig;
use crate::error::{EventMeshError, Result};

use super::codec::TcpCodec;
use super::frame::{Command, Package};
use super::message;

// `SinkExt` is needed for `Framed::send()`.
use futures::SinkExt;

/// Default channel capacity for outbound and inbound message queues.
const CHANNEL_CAPACITY: usize = 256;

/// Capacity of the reconnect-event channel. A bounded channel of 1 is enough:
/// `try_send` drops intermediate notifications if the consumer hasn't drained
/// the previous one yet — the consumer re-subscribes to *all* topics each time,
/// so missing an intermediate notification is harmless.
const RECONNECT_CHANNEL_CAPACITY: usize = 1;

/// Why the inner I/O loop exited. Used by the outer reconnect loop to decide
/// whether to attempt a reconnect.
#[derive(Debug)]
enum IoExitReason {
    /// `CancellationToken` was fired (explicit shutdown).
    Cancelled,
    /// All `mpsc::Sender` clones were dropped (user dropped the connection
    /// handle).
    AllSendersDropped,
    /// A read or write I/O error occurred.
    IoError,
    /// The server closed the connection (EOF on read).
    ServerClosed,
    /// The inbound (consumer-facing) channel is full — the consumer is not
    /// draining pushes fast enough. Rather than silently dropping server pushes
    /// (which would lose unacked messages), we tear down the connection so the
    /// server redelivers them after reconnect.
    SlowConsumer,
}

/// A connected TCP transport.
///
/// Created by [`TcpConnection::connect`], which performs the TCP connect +
/// HELLO handshake. A background task handles all I/O (read, write, heartbeat)
/// and, when [`ReconnectConfig`] is enabled, automatically re-establishes the
/// connection after failures.
///
/// Call [`TcpConnection::io`] for request-response (blocks until the matching
/// reply arrives, keyed by the header `seq`), or [`TcpConnection::send`] for
/// fire-and-forget writes.
pub struct TcpConnection {
    /// Outbound: write packages into the background task's send loop.
    outbound_tx: mpsc::Sender<Package>,
    /// Inbound server-pushed messages (taken by the consumer via
    /// [`take_inbound_rx`]).
    inbound_rx: Mutex<Option<mpsc::Receiver<Package>>>,
    /// Reconnect-event receiver (taken by the consumer via
    /// [`take_reconnect_rx`]).
    reconnect_rx: Mutex<Option<mpsc::Receiver<()>>>,
    /// Pending request-response contexts: `seq → oneshot::Sender`.
    pending: Arc<Mutex<HashMap<String, oneshot::Sender<Package>>>>,
    /// Whether unmatched `RESPONSE_TO_CLIENT` frames should be made available
    /// to a publisher-side business handler.
    deliver_orphan_responses: Arc<AtomicBool>,
    /// Shutdown signal shared with the background task.
    cancel: CancellationToken,
    /// Set to `false` by the background task when it exits for any reason
    /// (cancellation, I/O error, server close, all-senders-dropped). Mirrors
    /// Java's `channel.isActive()` more faithfully than the cancellation token
    /// alone, which only flips on explicit shutdown.
    alive: Arc<AtomicBool>,
    /// Background task handle.
    join: Mutex<Option<JoinHandle<()>>>,
}

impl TcpConnection {
    /// Connect to the server, perform the HELLO handshake, and start the
    /// background I/O + heartbeat task.
    ///
    /// `connect_timeout` bounds the socket connection and `control_timeout`
    /// bounds the HELLO response, mirroring Java's separate 1-second Netty
    /// connect timeout and 20-second protocol request timeout.
    ///
    /// The `reconnect` config controls automatic reconnection after I/O errors.
    /// When enabled, the background task re-establishes the connection with
    /// exponential backoff after failures.
    pub async fn connect(
        addr: &str,
        port: u16,
        user_agent: &super::frame::UserAgent,
        heartbeat_interval: Duration,
        connect_timeout: Duration,
        control_timeout: Duration,
        reconnect: ReconnectConfig,
    ) -> Result<Self> {
        // Initial connect is inline so the caller gets immediate feedback.
        // Subsequent reconnects happen in the background task.
        let framed =
            Self::establish(addr, port, user_agent, connect_timeout, control_timeout).await?;

        let (outbound_tx, outbound_rx) = mpsc::channel(CHANNEL_CAPACITY);
        let (inbound_tx, inbound_rx) = mpsc::channel(CHANNEL_CAPACITY);
        let (reconnect_tx, reconnect_rx) = mpsc::channel(RECONNECT_CHANNEL_CAPACITY);
        let pending = Arc::new(Mutex::new(HashMap::new()));
        let deliver_orphan_responses = Arc::new(AtomicBool::new(false));
        let cancel = CancellationToken::new();
        let alive = Arc::new(AtomicBool::new(true));

        let join = tokio::spawn(Self::run(
            addr.to_string(),
            port,
            user_agent.clone(),
            heartbeat_interval,
            connect_timeout,
            control_timeout,
            reconnect,
            framed,
            outbound_rx,
            inbound_tx,
            reconnect_tx,
            Arc::clone(&pending),
            Arc::clone(&deliver_orphan_responses),
            cancel.clone(),
            Arc::clone(&alive),
        ));

        info!(peer = %format!("{addr}:{port}"), "TCP connected");

        Ok(Self {
            outbound_tx,
            inbound_rx: Mutex::new(Some(inbound_rx)),
            reconnect_rx: Mutex::new(Some(reconnect_rx)),
            pending,
            deliver_orphan_responses,
            cancel,
            alive,
            join: Mutex::new(Some(join)),
        })
    }

    /// Establish a new TCP connection and perform the HELLO handshake.
    ///
    /// Used both by [`connect`] (initial) and the reconnect loop (subsequent).
    /// The socket connect and HELLO response have separate timeout bounds.
    async fn establish(
        addr: &str,
        port: u16,
        user_agent: &super::frame::UserAgent,
        connect_timeout: Duration,
        control_timeout: Duration,
    ) -> Result<Framed<TcpStream, TcpCodec>> {
        // Defer name resolution to Tokio: `TcpStream::connect` accepts a
        // "host:port" string via `ToSocketAddrs`, so DNS names like
        // "localhost" (the default `server_addr`) resolve correctly.
        let peer = format!("{addr}:{port}");
        debug!(%peer, "connecting TCP");
        let stream = tokio::time::timeout(connect_timeout, TcpStream::connect(&peer))
            .await
            .map_err(|_| EventMeshError::Timeout(connect_timeout))??;
        stream.set_nodelay(true).ok();

        let mut framed = Framed::new(stream, TcpCodec::new());

        // --- HELLO handshake (inline, before starting the I/O loop) ---
        // Java's `TcpClient.hello()` routes through `io(msg, timeout)`, so the
        // handshake is bounded. We do the same: if the server accepts the TCP
        // connection but never writes a HELLO_RESPONSE (half-open proxy,
        // network partition, slow server), we fail with `Timeout` instead of
        // hanging forever.
        debug!("sending HELLO");
        let hello_pkg = message::hello(user_agent);
        framed.send(hello_pkg).await?;
        match tokio::time::timeout(control_timeout, framed.next()).await {
            Err(_) => Err(EventMeshError::Timeout(control_timeout)),
            Ok(None) => Err(EventMeshError::Tcp("connection closed during HELLO".into())),
            Ok(Some(Err(e))) => Err(e),
            Ok(Some(Ok(resp))) if resp.header.cmd == Command::HelloResponse => {
                // The Java runtime's `HelloProcessor` rejects the handshake
                // (OPStatus.FAIL / ACL_FAIL) when the group isn't registered,
                // the token is rejected, the server isn't RUNNING yet, or the
                // `UserAgent` is invalid — and then closes the session. Treat
                // a non-zero code as a failure and surface `desc`.
                if resp.header.code != 0 {
                    return Err(EventMeshError::Server {
                        code: resp.header.code,
                        message: resp.header.desc.unwrap_or_else(|| "HELLO rejected".into()),
                    });
                }
                debug!(code = resp.header.code, "HELLO ok");
                Ok(framed)
            }
            Ok(Some(Ok(resp))) => Err(EventMeshError::Tcp(format!(
                "unexpected response to HELLO: {:?}",
                resp.header.cmd
            ))),
        }
    }

    /// Request-response: register a pending context keyed by `seq`, send the
    /// package, and wait for the matching reply within `timeout`.
    ///
    /// Corresponds to Java `TcpClient.io()`.
    pub async fn io(&self, pkg: Package, timeout: Duration) -> Result<Package> {
        // Fail fast: if the connection is not active (reconnecting, shut down,
        // or backing off), reject the request immediately. Without this the
        // package would sit in the outbound mpsc buffer and be sent after a
        // successful reconnect — a "ghost write" — even though the caller may
        // have already received a Timeout/ChannelClosed and retried.
        if !self.is_active() {
            return Err(EventMeshError::ChannelClosed(
                "connection is not active (reconnecting or shut down)".into(),
            ));
        }

        // Client-originated frames always carry a seq (see `message::package`),
        // so this is `Some` in practice. A `None` would mean a programming
        // error; we coalesce it to an empty string so the `pending` lookup
        // (keyed by `String`) stays consistent with the run loop below.
        let seq = pkg.header.seq.clone().unwrap_or_default();
        let (tx, rx) = oneshot::channel();

        // Register pending context BEFORE sending so the read loop can match
        // the response as soon as it arrives.
        {
            let mut guard = self.pending.lock().await;
            guard.insert(seq.clone(), tx);
        }

        // Send the package to the background task.
        if self.outbound_tx.send(pkg).await.is_err() {
            // The run loop already exited (its `clear()` is why send failed).
            // Remove our entry to stay symmetric with the timeout/closed paths
            // below, so no stale `oneshot::Sender` lingers in the map.
            self.pending.lock().await.remove(&seq);
            return Err(EventMeshError::ChannelClosed(
                "connection send loop exited".into(),
            ));
        }

        // Wait for the response.
        match tokio::time::timeout(timeout, rx).await {
            Ok(Ok(resp)) => Ok(resp),
            Ok(Err(_)) => {
                self.pending.lock().await.remove(&seq);
                Err(EventMeshError::ChannelClosed(
                    "connection task exited while waiting for response".into(),
                ))
            }
            Err(_) => {
                self.pending.lock().await.remove(&seq);
                Err(EventMeshError::Timeout(timeout))
            }
        }
    }

    /// Fire-and-forget send: write the package without waiting for a reply.
    ///
    /// Corresponds to Java `TcpClient.send()`.
    pub async fn send(&self, pkg: Package) -> Result<()> {
        // Same fail-fast rationale as `io()`: prevent ghost writes during
        // reconnect.
        if !self.is_active() {
            return Err(EventMeshError::ChannelClosed(
                "connection is not active (reconnecting or shut down)".into(),
            ));
        }
        self.outbound_tx
            .send(pkg)
            .await
            .map_err(|_| EventMeshError::ChannelClosed("connection send loop exited".into()))
    }

    /// Take ownership of the inbound receiver. Called once by the consumer to
    /// start receiving server-pushed messages.
    pub async fn take_inbound_rx(&self) -> Option<mpsc::Receiver<Package>> {
        self.inbound_rx.lock().await.take()
    }

    /// Deliver unmatched server `RESPONSE_TO_CLIENT` frames to the inbound
    /// receiver. This is used by TCP publisher-side business handlers.
    pub fn enable_orphan_response_delivery(&self) {
        self.deliver_orphan_responses.store(true, Ordering::Release);
    }

    /// Take ownership of the reconnect-event receiver. Called once by the
    /// consumer to get notified when the connection has been automatically
    /// re-established, so it can replay subscriptions.
    ///
    /// Each `()` received means a reconnect just succeeded and the consumer
    /// should re-send `SUBSCRIBE_REQUEST` + `LISTEN_REQUEST`.
    pub async fn take_reconnect_rx(&self) -> Option<mpsc::Receiver<()>> {
        self.reconnect_rx.lock().await.take()
    }

    /// Whether the background task is still alive.
    ///
    /// Mirrors Java's `TcpClient.isActive()` which checks `channel.isActive()`.
    /// This flips to `false` for *any* reason the background task exits
    /// (cancellation, read/write error, server-side close, all senders
    /// dropped) — not just explicit shutdown. During a reconnect backoff it is
    /// also `false`; it returns to `true` once the new connection is
    /// established.
    pub fn is_active(&self) -> bool {
        self.alive.load(Ordering::Acquire)
    }

    /// Graceful shutdown: send CLIENT_GOODBYE, cancel the task, and join.
    pub async fn shutdown(&self) {
        // Best-effort goodbye.
        let _ = self.send(message::goodbye()).await;

        self.cancel.cancel();
        if let Some(join) = self.join.lock().await.take() {
            let _ = join.await;
        }
    }

    // -----------------------------------------------------------------------
    // Background task: outer reconnect loop + inner I/O loop
    // -----------------------------------------------------------------------

    /// Outer run loop. Owns the channels across reconnects. On I/O error /
    /// server close, attempts reconnection with exponential backoff (when
    /// enabled). On cancellation / all-senders-dropped, exits immediately.
    #[allow(clippy::too_many_arguments)]
    async fn run(
        addr: String,
        port: u16,
        user_agent: super::frame::UserAgent,
        heartbeat_interval: Duration,
        connect_timeout: Duration,
        control_timeout: Duration,
        reconnect: ReconnectConfig,
        mut framed: Framed<TcpStream, TcpCodec>,
        mut outbound_rx: mpsc::Receiver<Package>,
        inbound_tx: mpsc::Sender<Package>,
        reconnect_tx: mpsc::Sender<()>,
        pending: Arc<Mutex<HashMap<String, oneshot::Sender<Package>>>>,
        deliver_orphan_responses: Arc<AtomicBool>,
        cancel: CancellationToken,
        alive: Arc<AtomicBool>,
    ) {
        loop {
            // Run the I/O loop with the current framed stream.
            let reason = Self::io_loop(
                &mut framed,
                &mut outbound_rx,
                &inbound_tx,
                Arc::clone(&pending),
                Arc::clone(&deliver_orphan_responses),
                heartbeat_interval,
                &cancel,
                alive.as_ref(),
            )
            .await;

            // Clean up pending requests from the (now dead) connection so
            // waiting `io()` callers get a ChannelClosed error instead of
            // hanging until timeout. Mirrors Java's behavior where orphaned
            // RequestContext entries simply time out, but is more prompt.
            pending.lock().await.clear();
            alive.store(false, Ordering::Release);

            // Drain the outbound queue so stale packages from the dead
            // connection are never re-sent on the next connection. Without
            // this, a package enqueued via io()/send() that hadn't been
            // written to the socket yet would survive in the mpsc buffer
            // across the reconnect and produce a "ghost write" — a message
            // sent to the new connection even though the caller already
            // received a Timeout/ChannelClosed error and may have retried.
            // (The Java SDK avoids this by writing directly via
            // `channel.writeAndFlush()` with no user-space queue.)
            while outbound_rx.try_recv().is_ok() {
                // Discard; these packages were never written to the wire.
            }

            match reason {
                IoExitReason::Cancelled | IoExitReason::AllSendersDropped => {
                    debug!("connection task exiting ({:?})", reason);
                    return;
                }
                IoExitReason::IoError | IoExitReason::ServerClosed | IoExitReason::SlowConsumer => {
                }
            }

            // Decide whether to attempt reconnect.
            if !reconnect.enabled || cancel.is_cancelled() {
                debug!("reconnect disabled or cancelled, exiting");
                return;
            }

            // Reconnect with exponential backoff.
            let mut backoff = reconnect.initial_backoff;
            let mut attempt: usize = 0;

            loop {
                attempt += 1;
                if attempt > reconnect.max_retries {
                    warn!(
                        attempts = attempt - 1,
                        "max reconnect attempts ({}) exceeded, giving up", reconnect.max_retries
                    );
                    return;
                }

                debug!(attempt, backoff = ?backoff, "reconnect backoff");
                tokio::select! {
                    biased;
                    _ = cancel.cancelled() => {
                        debug!("cancelled during reconnect backoff");
                        return;
                    }
                    _ = tokio::time::sleep(backoff) => {}
                }
                backoff = backoff.saturating_mul(2).min(reconnect.max_backoff);

                match Self::establish(&addr, port, &user_agent, connect_timeout, control_timeout)
                    .await
                {
                    Ok(new_framed) => {
                        info!(
                            attempt,
                            peer = %format!("{addr}:{port}"),
                            "TCP reconnected"
                        );
                        alive.store(true, Ordering::Release);

                        // Notify the consumer that it should replay
                        // subscriptions. `try_send` drops the notification if
                        // the channel is full (the consumer hasn't drained the
                        // previous one) — which is fine because the consumer
                        // re-subscribes *all* topics each time.
                        let _ = reconnect_tx.try_send(());

                        framed = new_framed;
                        break; // Back to outer loop → new io_loop with new framed.
                    }
                    Err(e) => {
                        warn!(attempt, error = %e, "reconnect attempt failed");
                        // Continue inner loop to retry with increased backoff.
                    }
                }
            }
        }
    }

    /// Inner I/O loop — read, write, and heartbeat on a single connection.
    /// Returns when the connection is lost or the task is cancelled.
    #[allow(clippy::too_many_arguments)]
    async fn io_loop(
        framed: &mut Framed<TcpStream, TcpCodec>,
        outbound_rx: &mut mpsc::Receiver<Package>,
        inbound_tx: &mpsc::Sender<Package>,
        pending: Arc<Mutex<HashMap<String, oneshot::Sender<Package>>>>,
        deliver_orphan_responses: Arc<AtomicBool>,
        heartbeat_interval: Duration,
        cancel: &CancellationToken,
        alive: &AtomicBool,
    ) -> IoExitReason {
        use tokio::time::MissedTickBehavior;
        let _ = alive; // already set to true by caller; no need to touch here
        let mut heartbeat = tokio::time::interval(heartbeat_interval);
        heartbeat.set_missed_tick_behavior(MissedTickBehavior::Delay);
        // Skip the immediate first tick.
        heartbeat.tick().await;

        loop {
            tokio::select! {
                biased;

                _ = cancel.cancelled() => {
                    debug!("connection task cancelled");
                    return IoExitReason::Cancelled;
                }

                // Write outbound packages from user code.
                pkg = outbound_rx.recv() => {
                    match pkg {
                        Some(pkg) => {
                            if let Err(e) = framed.send(pkg).await {
                                warn!("write error, connection lost: {e}");
                                return IoExitReason::IoError;
                            }
                        }
                        None => {
                            debug!("all senders dropped, stopping connection task");
                            return IoExitReason::AllSendersDropped;
                        }
                    }
                }

                // Read inbound frames from the server.
                result = framed.next() => {
                    match result {
                        Some(Ok(pkg)) => {
                            // Heartbeats are sent fire-and-forget below, so
                            // their responses are never registered in `pending`.
                            // Drop them here: otherwise they'd be forwarded to
                            // the inbound channel. Only the consumer drains that
                            // channel — a producer-only connection would let
                            // heartbeats pile up until `inbound_tx.send` blocks
                            // (channel cap 256, ~30s interval), stalling this
                            // whole select! arm and freezing I/O after ~2 hours.
                            if pkg.header.cmd == Command::HeartbeatResponse {
                                debug!("heartbeat response received");
                                continue;
                            }
                            let seq = pkg.header.seq.clone().unwrap_or_default();
                            // Try to match a pending request-response context.
                            // Server-initiated frames (GOODBYE/REDIRECT) arrive
                            // with no seq, so `seq` is "" here and never
                            // matches a client's random 10-char correlation key
                            // — they fall through to the inbound channel below
                            // so `handle_inbound` can ACK them.
                            let entry = {
                                let mut guard = pending.lock().await;
                                guard.remove(&seq)
                            };
                            if let Some(tx) = entry {
                                // A `RESPONSE_TO_CLIENT` (the server's RR reply)
                                // carries the seq of the originating
                                // `REQUEST_TO_SERVER`, so it lands here as a
                                // matched `io()` response rather than as a server
                                // push. The consumer ACKs pushes via
                                // `handle_inbound`; mirror the Java client
                                // (`PubClientImpl` / `AbstractEventMeshTCPPubHandler`)
                                // by ACKing the RR reply with
                                // `RESPONSE_TO_CLIENT_ACK` (copied seq + body)
                                // before handing it to the waiter. Server-side
                                // this is bookkeeping only (`MessageAckProcessor`
                                // is a no-op for RR replies).
                                if pkg.header.cmd == Command::ResponseToClient {
                                    let ack_pkg = message::response_to_client_ack(&pkg);
                                    if let Err(e) = framed.send(ack_pkg).await {
                                        warn!(error = %e, "failed to send RESPONSE_TO_CLIENT_ACK");
                                    }
                                }
                                let _ = tx.send(pkg);
                            } else {
                                // An orphan RESPONSE_TO_CLIENT is a late reply
                                // to an `io()` call that already timed out and
                                // removed its pending entry. Drop it unless a
                                // publisher-side handler explicitly requested
                                // delivery; a default producer has no inbound
                                // receiver and would otherwise fill the queue.
                                if pkg.header.cmd == Command::ResponseToClient
                                    && !deliver_orphan_responses.load(Ordering::Acquire)
                                {
                                    debug!("dropping orphan RESPONSE_TO_CLIENT");
                                    continue;
                                }

                                // Server push → inbound channel for the consumer.
                                // Use `try_send` instead of a blocking `send().await`
                                // so a full inbound channel can never stall the I/O
                                // loop indefinitely.
                                //
                                // When the channel is **full**, the consumer is not
                                // draining fast enough. Rather than silently dropping
                                // the push (which would lose an unacked message), we
                                // tear down the connection. The server has not
                                // received an ACK for this message (ACKs are sent by
                                // the consumer-side driver *after* it reads from this
                                // channel), so the server will redeliver after
                                // reconnect. This mirrors the Java SDK's behavior
                                // where a slow user callback blocks the Netty event
                                // loop, creating natural TCP backpressure — but
                                // avoids stalling heartbeats and writes in our async
                                // model.
                                match inbound_tx.try_send(pkg) {
                                    Ok(()) => {}
                                    Err(mpsc::error::TrySendError::Full(_)) => {
                                        warn!(
                                            "inbound channel full — disconnecting to \
                                             trigger server redelivery of unacked messages"
                                        );
                                        return IoExitReason::SlowConsumer;
                                    }
                                    Err(mpsc::error::TrySendError::Closed(_)) => {
                                        debug!("inbound channel closed (consumer dropped)");
                                    }
                                }
                            }
                        }
                        Some(Err(e)) => {
                            warn!("read error, connection lost: {e}");
                            return IoExitReason::IoError;
                        }
                        None => {
                            info!("connection closed by server");
                            return IoExitReason::ServerClosed;
                        }
                    }
                }

                // Heartbeat: fire-and-forget. If the write fails the connection
                // is dead and the loop breaks.
                _ = heartbeat.tick() => {
                    let hb = message::heartbeat();
                    if let Err(e) = framed.send(hb).await {
                        warn!("heartbeat send failed: {e}");
                        return IoExitReason::IoError;
                    }
                    debug!("heartbeat sent");
                }
            }
        }
    }
}

impl Drop for TcpConnection {
    fn drop(&mut self) {
        self.cancel.cancel();
        if let Ok(mut guard) = self.join.try_lock() {
            if let Some(join) = guard.take() {
                join.abort();
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use super::*;
    use crate::config::{ReconnectConfig, TcpClientConfig};
    use crate::model::EventMeshMessage;
    use crate::transport::tcp::codec::TcpCodec;
    use crate::transport::tcp::frame::{Command, Header, Package, PackageBody};
    use crate::transport::{Publisher, RequestReply};

    use futures::SinkExt;
    use tokio::net::TcpListener;
    use tokio_stream::StreamExt;
    use tokio_util::codec::Framed;

    #[tokio::test]
    async fn hello_response_wait_uses_the_control_timeout() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = tokio::spawn(async move {
            let (_stream, _) = listener.accept().await.unwrap();
            std::future::pending::<()>().await;
        });
        let config = TcpClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(port)
            .producer_group("g")
            .connect_timeout(Duration::from_secs(1))
            .control_timeout(Duration::from_millis(20))
            .heartbeat_interval(Duration::from_secs(60))
            .reconnect(ReconnectConfig::builder().enabled(false).build())
            .build();
        let user_agent = super::super::frame::UserAgent::from_identity(
            &config.identity,
            config.server_port,
            "pub",
        );

        let result = TcpConnection::connect(
            &config.server_addr,
            config.server_port,
            &user_agent,
            config.heartbeat_interval,
            config.connect_timeout,
            config.control_timeout,
            config.reconnect,
        )
        .await;
        assert!(matches!(
            result,
            Err(EventMeshError::Timeout(timeout)) if timeout == Duration::from_millis(20)
        ));
        server.abort();
    }

    /// Loopback test: a request/reply round-trip must produce a
    /// `RESPONSE_TO_CLIENT_ACK` back to the server (mirroring the Java client).
    #[tokio::test]
    async fn request_reply_acks_response_to_client() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();

        let (ack_tx, ack_rx) = oneshot::channel();

        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());

            // 1. HELLO handshake.
            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            let hello_resp = Package::new(Header::new(Command::HelloResponse, "hello-seq"));
            framed.send(hello_resp).await.unwrap();

            // 2. Receive REQUEST_TO_SERVER; echo a RESPONSE_TO_CLIENT with the
            //    same seq + a JSON body (code 0 = success).
            let req = framed.next().await.unwrap().unwrap();
            assert_eq!(req.header.cmd, Command::RequestToServer);
            let seq = req.header.seq.clone().unwrap_or_default();
            let body = PackageBody::Text(
                serde_json::json!({
                    "topic": "reply",
                    "body": "pong",
                })
                .to_string(),
            );
            let mut resp_hdr = Header::new(Command::ResponseToClient, seq.clone());
            resp_hdr.code = 0;
            framed
                .send(Package {
                    header: resp_hdr,
                    body,
                })
                .await
                .unwrap();

            // 3. Expect the client to ACK with RESPONSE_TO_CLIENT_ACK carrying
            //    the same seq. Heartbeat frames may interleave, so scan until we
            //    see the ACK (heartbeat interval is large, so usually first).
            let mut got_ack = None;
            for _ in 0..8 {
                match framed.next().await {
                    Some(Ok(pkg)) => {
                        if pkg.header.cmd == Command::ResponseToClientAck {
                            got_ack = Some(pkg.header.seq.clone().unwrap_or_default());
                            break;
                        }
                    }
                    _ => break,
                }
            }
            let _ = ack_tx.send(got_ack);

            // Keep the connection open until the client drops it.
            let _ = framed.close().await;
        });

        let config = TcpClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(port)
            .producer_group("g")
            .control_timeout(Duration::from_secs(3))
            .heartbeat_interval(Duration::from_secs(60))
            .reconnect(ReconnectConfig::builder().enabled(false).build())
            .build();

        let producer = crate::transport::tcp::TcpProducer::connect(config)
            .await
            .expect("connect");

        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("ping")
            .build();
        let reply = producer
            .request_reply(msg, Duration::from_secs(3))
            .await
            .expect("request_reply");
        assert_eq!(reply.topic.as_deref(), Some("reply"));
        assert_eq!(reply.content.as_deref(), Some("pong"));

        producer.shutdown().await;

        let ack_seq = ack_rx
            .await
            .expect("server did not observe any frames after the reply")
            .expect("no RESPONSE_TO_CLIENT_ACK received by the server");
        // The ACK must echo the RR correlation seq.
        assert!(
            !ack_seq.is_empty(),
            "RESPONSE_TO_CLIENT_ACK must carry the reply seq"
        );

        let _ = server.await;
    }

    /// After a server-side close, the connection must automatically reconnect
    /// (when enabled) and the consumer must receive a reconnect event so it
    /// can replay subscriptions.
    #[tokio::test]
    async fn reconnect_after_server_close() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();

        // Server: accept two connections on the same listener. Close the first
        // one to force a reconnect, then HELLO the second.
        let server = tokio::spawn(async move {
            // --- First connection ---
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());
            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            framed
                .send(Package::new(Header::new(Command::HelloResponse, "hello-1")))
                .await
                .unwrap();
            // Drop to force a reconnect.
            drop(framed);

            // --- Second connection (the auto-reconnect) ---
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());
            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            framed
                .send(Package::new(Header::new(Command::HelloResponse, "hello-2")))
                .await
                .unwrap();

            // Keep alive briefly so the reconnect stabilizes.
            tokio::time::sleep(Duration::from_secs(1)).await;
        });

        let config = TcpClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(port)
            .producer_group("g")
            .control_timeout(Duration::from_secs(3))
            .heartbeat_interval(Duration::from_secs(60))
            .reconnect(
                ReconnectConfig::builder()
                    .enabled(true)
                    .initial_backoff(Duration::from_millis(100))
                    .max_backoff(Duration::from_millis(500))
                    .build(),
            )
            .build();

        let user_agent = super::super::frame::UserAgent::from_identity(
            &config.identity,
            config.server_port,
            "pub",
        );
        let conn = TcpConnection::connect(
            &config.server_addr,
            config.server_port,
            &user_agent,
            config.heartbeat_interval,
            config.connect_timeout,
            config.control_timeout,
            config.reconnect.clone(),
        )
        .await
        .expect("initial connect");

        // Wait for the server to close the first connection and the client to
        // reconnect. The reconnect event channel fires after the new HELLO.
        let mut reconnect_rx = conn.take_reconnect_rx().await.expect("reconnect receiver");

        let result = tokio::time::timeout(Duration::from_secs(5), reconnect_rx.recv()).await;
        assert!(
            result.is_ok(),
            "should receive a reconnect event within 5 s"
        );
        assert!(
            conn.is_active(),
            "connection should be alive after reconnect"
        );

        conn.shutdown().await;
        let _ = server.await;
    }
}
