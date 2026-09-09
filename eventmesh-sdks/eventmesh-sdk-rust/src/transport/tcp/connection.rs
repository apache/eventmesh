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
//! via a driver-owned, `seq`-keyed pending map of `oneshot` channels.
//!
//! ## Reconnect
//!
//! When automatic reconnect is enabled (the default), the background
//! task automatically re-establishes the TCP connection + HELLO handshake after
//! an I/O error or server-side close. An optional reconnect-event channel
//! ([`TcpConnection::take_reconnect_rx`]) lets consumers replay their
//! subscriptions after a successful reconnect. This mirrors the Java SDK's
//! heartbeat-driven reconnect but with exponential backoff.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::{mpsc, oneshot, Mutex};
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::info;

use crate::config::ReconnectPolicy;
use crate::error::{EventMeshError, Result};

use super::frame::Package;
use super::message;

mod dispatcher;
mod driver;
mod supervisor;

use dispatcher::{OutboundCommand, PendingCancellation, PendingKey};
use supervisor::{ConnectionState, ConnectionSupervisor};

/// Default channel capacity for outbound and inbound message queues.
const CHANNEL_CAPACITY: usize = 256;

/// Capacity of the reconnect-event channel. A bounded channel of 1 is enough:
/// `try_send` drops intermediate notifications if the consumer hasn't drained
/// the previous one yet — the consumer re-subscribes to *all* topics each time,
/// so missing an intermediate notification is harmless.
const RECONNECT_CHANNEL_CAPACITY: usize = 1;

/// A connected TCP transport.
///
/// Created by [`TcpConnection::connect`], which performs the TCP connect +
/// HELLO handshake. A background task handles all I/O (read, write, heartbeat)
/// and, when reconnecting is enabled, automatically re-establishes the
/// connection after failures.
///
/// Call [`TcpConnection::io`] for request-response (blocks until the matching
/// reply arrives, keyed by the header `seq`), or [`TcpConnection::send`] for
/// fire-and-forget writes.
pub struct TcpConnection {
    /// Outbound: write packages into the background task's send loop.
    outbound_tx: mpsc::Sender<OutboundCommand>,
    /// Inbound server-pushed messages (taken by the consumer via
    /// [`take_inbound_rx`]).
    inbound_rx: Mutex<Option<mpsc::Receiver<Package>>>,
    /// Reconnect-event receiver (taken by the consumer via
    /// [`take_reconnect_rx`]).
    reconnect_rx: Mutex<Option<mpsc::Receiver<()>>>,
    /// Non-blocking cancellation path to the driver-owned pending map.
    pending_cancel_tx: mpsc::UnboundedSender<PendingKey>,
    /// Serializes enqueue commitment with connection teardown.
    state: Arc<Mutex<ConnectionState>>,
    /// Whether unmatched `RESPONSE_TO_CLIENT` frames should be made available
    /// to a publisher-side business handler.
    deliver_orphan_responses: Arc<AtomicBool>,
    /// Shutdown signal shared with the background task.
    cancel: CancellationToken,
    /// Maximum time fire-and-forget sends may wait for outbound queue
    /// capacity. Request-response operations use their own per-call deadline.
    outbound_timeout: Duration,
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
        reconnect: ReconnectPolicy,
    ) -> Result<Self> {
        // Initial connect is inline so the caller gets immediate feedback.
        // Subsequent reconnects happen in the background task.
        let framed = ConnectionSupervisor::establish(
            addr,
            port,
            user_agent,
            connect_timeout,
            control_timeout,
        )
        .await?;

        let (outbound_tx, outbound_rx) = mpsc::channel(CHANNEL_CAPACITY);
        let (pending_cancel_tx, pending_cancel_rx) = mpsc::unbounded_channel();
        let (inbound_tx, inbound_rx) = mpsc::channel(CHANNEL_CAPACITY);
        let (reconnect_tx, reconnect_rx) = mpsc::channel(RECONNECT_CHANNEL_CAPACITY);
        let state = Arc::new(Mutex::new(ConnectionState {
            generation: 0,
            active: true,
        }));
        let deliver_orphan_responses = Arc::new(AtomicBool::new(false));
        let cancel = CancellationToken::new();
        let alive = Arc::new(AtomicBool::new(true));

        let join = tokio::spawn(ConnectionSupervisor::run(
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
            pending_cancel_rx,
            Arc::clone(&state),
            Arc::clone(&deliver_orphan_responses),
            cancel.clone(),
            Arc::clone(&alive),
        ));

        info!(peer = %format!("{addr}:{port}"), "TCP connected");

        Ok(Self {
            outbound_tx,
            inbound_rx: Mutex::new(Some(inbound_rx)),
            reconnect_rx: Mutex::new(Some(reconnect_rx)),
            pending_cancel_tx,
            state,
            deliver_orphan_responses,
            cancel,
            outbound_timeout: control_timeout,
            alive,
            join: Mutex::new(Some(join)),
        })
    }

    fn deadline_after(timeout: Duration) -> Result<tokio::time::Instant> {
        tokio::time::Instant::now()
            .checked_add(timeout)
            .ok_or_else(|| EventMeshError::InvalidArgument("TCP timeout is too large".into()))
    }

    /// Request-response: register a pending context keyed by `seq`, send the
    /// package, and wait for the matching reply within `timeout`.
    ///
    /// Corresponds to Java `TcpClient.io()`.
    pub async fn io(&self, pkg: Package, timeout: Duration) -> Result<Package> {
        let deadline = Self::deadline_after(timeout)?;
        // Client-originated frames always carry a seq (see `message::package`),
        // so this is `Some` in practice. A `None` would mean a programming
        // error; we coalesce it to an empty string so the `pending` lookup
        // (keyed by `String`) stays consistent with the run loop below.
        let seq = pkg.header.seq.clone().unwrap_or_default();
        let (tx, rx) = oneshot::channel();

        // Reserve capacity first, then commit the pending registration and
        // enqueue while holding the lifecycle lock. Teardown takes this same
        // lock before invalidating the generation and draining the queue, so a
        // sender that slept on a full channel cannot enqueue onto the next
        // socket after teardown has completed.
        let generation = self.active_generation().await?;
        let permit = self.reserve_outbound(deadline, timeout).await?;
        let pending_key = (generation, seq);
        {
            let state = self.state.lock().await;
            if self.cancel.is_cancelled() || !state.active || state.generation != generation {
                return Err(Self::inactive_error());
            }
            if tokio::time::Instant::now() >= deadline {
                return Err(EventMeshError::Timeout(timeout));
            }
            permit.send(OutboundCommand::Request {
                package: pkg,
                key: pending_key.clone(),
                response_tx: tx,
            });
        }

        let mut pending_cancellation =
            PendingCancellation::new(pending_key, self.pending_cancel_tx.clone());

        // Wait for the response using the same deadline that bounded queue
        // reservation, so backpressure consumes the caller's timeout budget
        // instead of starting a fresh timer after enqueue.
        let result = tokio::select! {
            biased;
            _ = self.cancel.cancelled() => Err(Self::inactive_error()),
            _ = tokio::time::sleep_until(deadline) => Err(EventMeshError::Timeout(timeout)),
            response = rx => match response {
                Ok(response) => Ok(response),
                Err(_) => Err(EventMeshError::ChannelClosed(
                    "connection task exited while waiting for response".into(),
                )),
            },
        };
        if result.is_ok() {
            // The driver removes the entry before delivering the response.
            pending_cancellation.disarm();
        }
        result
    }

    async fn reserve_outbound(
        &self,
        deadline: tokio::time::Instant,
        timeout: Duration,
    ) -> Result<mpsc::Permit<'_, OutboundCommand>> {
        tokio::select! {
            biased;
            _ = self.cancel.cancelled() => Err(Self::inactive_error()),
            _ = tokio::time::sleep_until(deadline) => Err(EventMeshError::Timeout(timeout)),
            permit = self.outbound_tx.reserve() => permit.map_err(|_| {
                EventMeshError::ChannelClosed("connection send loop exited".into())
            }),
        }
    }

    /// Enqueue a package without waiting for its socket write or a reply.
    ///
    /// Corresponds to Java `TcpClient.send()`.
    pub async fn send(&self, pkg: Package) -> Result<()> {
        let timeout = self.outbound_timeout;
        let deadline = Self::deadline_after(timeout)?;
        self.enqueue_send(OutboundCommand::Send(pkg), deadline, timeout)
            .await
    }

    /// Wait for the package to be flushed to the local socket, without
    /// waiting for a server ACK. Queueing and write completion share one
    /// timeout budget. A timeout during a write leaves delivery uncertain.
    pub async fn send_and_flush(&self, pkg: Package) -> Result<()> {
        let timeout = self.outbound_timeout;
        let deadline = Self::deadline_after(timeout)?;
        let (completion_tx, completion_rx) = oneshot::channel();
        self.enqueue_send(
            OutboundCommand::SendAndFlush {
                package: pkg,
                completion_tx,
            },
            deadline,
            timeout,
        )
        .await?;

        tokio::select! {
            biased;
            _ = self.cancel.cancelled() => Err(Self::inactive_error()),
            _ = tokio::time::sleep_until(deadline) => Err(EventMeshError::Timeout(timeout)),
            result = completion_rx => result.map_err(|_| EventMeshError::ChannelClosed(
                "connection task exited before completing the socket write".into(),
            ))?,
        }
    }

    async fn enqueue_send(
        &self,
        command: OutboundCommand,
        deadline: tokio::time::Instant,
        timeout: Duration,
    ) -> Result<()> {
        let generation = self.active_generation().await?;
        let permit = self.reserve_outbound(deadline, timeout).await?;
        let state = self.state.lock().await;
        if self.cancel.is_cancelled() || !state.active || state.generation != generation {
            return Err(Self::inactive_error());
        }
        if tokio::time::Instant::now() >= deadline {
            return Err(EventMeshError::Timeout(timeout));
        }
        permit.send(command);
        Ok(())
    }

    async fn active_generation(&self) -> Result<u64> {
        let state = self.state.lock().await;
        if state.active {
            Ok(state.generation)
        } else {
            Err(Self::inactive_error())
        }
    }

    fn inactive_error() -> EventMeshError {
        EventMeshError::ChannelClosed("connection is not active (reconnecting or shut down)".into())
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
        // Best-effort goodbye. Never wait for outbound capacity here: a full
        // queue is precisely when cancellation is needed to unblock callers
        // and an in-progress socket write.
        let _ = self
            .outbound_tx
            .try_send(OutboundCommand::Send(message::goodbye()));
        self.cancel.cancel();
        if let Some(join) = self.join.lock().await.take() {
            let _ = join.await;
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
    use std::sync::Arc;
    use std::time::Duration;

    use super::*;
    use crate::config::{Endpoint, ProducerOptions, ReconnectPolicy, TcpConfig};
    use crate::model::EventMeshMessage;
    use crate::transport::tcp::codec::TcpCodec;
    use crate::transport::tcp::frame::{Command, Header, Package, PackageBody};
    use crate::transport::{Publisher, RequestReply};

    use futures::SinkExt;
    use tokio::net::TcpListener;
    use tokio_stream::StreamExt;
    use tokio_util::codec::Framed;

    fn blocked_test_connection_with_timeout(
        outbound_timeout: Duration,
    ) -> (Arc<TcpConnection>, mpsc::Receiver<OutboundCommand>) {
        let (outbound_tx, outbound_rx) = mpsc::channel(1);
        outbound_tx
            .try_send(OutboundCommand::Send(Package::new(Header::new(
                Command::AsyncMessageToServer,
                "occupied",
            ))))
            .unwrap();
        let (_inbound_tx, inbound_rx) = mpsc::channel(1);
        let (_reconnect_tx, reconnect_rx) = mpsc::channel(1);
        let (pending_cancel_tx, _pending_cancel_rx) = mpsc::unbounded_channel();

        let connection = TcpConnection {
            outbound_tx,
            inbound_rx: Mutex::new(Some(inbound_rx)),
            reconnect_rx: Mutex::new(Some(reconnect_rx)),
            pending_cancel_tx,
            state: Arc::new(Mutex::new(ConnectionState {
                generation: 0,
                active: true,
            })),
            deliver_orphan_responses: Arc::new(AtomicBool::new(false)),
            cancel: CancellationToken::new(),
            outbound_timeout,
            alive: Arc::new(AtomicBool::new(true)),
            join: Mutex::new(None),
        };
        (Arc::new(connection), outbound_rx)
    }

    fn blocked_test_connection() -> (Arc<TcpConnection>, mpsc::Receiver<OutboundCommand>) {
        blocked_test_connection_with_timeout(Duration::from_secs(5))
    }

    async fn simulate_teardown(
        conn: &TcpConnection,
        outbound_rx: &mut mpsc::Receiver<OutboundCommand>,
    ) {
        let mut state = conn.state.lock().await;
        state.active = false;
        state.generation = state.generation.wrapping_add(1);
        conn.alive.store(false, Ordering::Release);
        while outbound_rx.try_recv().is_ok() {}
    }

    #[tokio::test]
    async fn blocked_send_cannot_enqueue_after_teardown() {
        let (conn, mut outbound_rx) = blocked_test_connection();
        let sender = {
            let conn = Arc::clone(&conn);
            tokio::spawn(async move {
                conn.send(Package::new(Header::new(
                    Command::AsyncMessageToServer,
                    "stale",
                )))
                .await
            })
        };
        tokio::task::yield_now().await;

        simulate_teardown(&conn, &mut outbound_rx).await;

        assert!(matches!(
            sender.await.unwrap(),
            Err(EventMeshError::ChannelClosed(_))
        ));
        assert!(matches!(
            outbound_rx.try_recv(),
            Err(mpsc::error::TryRecvError::Empty)
        ));
    }

    #[tokio::test]
    async fn blocked_io_cannot_register_or_enqueue_after_teardown() {
        let (conn, mut outbound_rx) = blocked_test_connection();
        let sender = {
            let conn = Arc::clone(&conn);
            tokio::spawn(async move {
                conn.io(
                    Package::new(Header::new(Command::RequestToServer, "stale")),
                    Duration::from_secs(5),
                )
                .await
            })
        };
        tokio::task::yield_now().await;

        simulate_teardown(&conn, &mut outbound_rx).await;

        assert!(matches!(
            sender.await.unwrap(),
            Err(EventMeshError::ChannelClosed(_))
        ));
        assert!(matches!(
            outbound_rx.try_recv(),
            Err(mpsc::error::TryRecvError::Empty)
        ));
    }

    #[tokio::test]
    async fn blocked_io_uses_one_timeout_for_queue_capacity_and_response() {
        let (conn, _outbound_rx) = blocked_test_connection();
        let timeout = Duration::from_millis(20);

        let result = tokio::time::timeout(
            Duration::from_secs(1),
            conn.io(
                Package::new(Header::new(Command::RequestToServer, "blocked")),
                timeout,
            ),
        )
        .await
        .expect("queue wait must respect the request timeout");

        assert!(matches!(
            result,
            Err(EventMeshError::Timeout(value)) if value == timeout
        ));
    }

    #[tokio::test]
    async fn queue_wait_consumes_the_response_timeout_budget() {
        let (conn, mut outbound_rx) = blocked_test_connection();
        let request_timeout = Duration::from_millis(200);
        let request = {
            let conn = Arc::clone(&conn);
            tokio::spawn(async move {
                conn.io(
                    Package::new(Header::new(Command::RequestToServer, "delayed")),
                    request_timeout,
                )
                .await
            })
        };

        tokio::time::sleep(Duration::from_millis(120)).await;
        let occupied = outbound_rx.recv().await.expect("occupied queue entry");
        assert!(matches!(
            occupied,
            OutboundCommand::Send(pkg) if pkg.header.seq.as_deref() == Some("occupied")
        ));

        let result = tokio::time::timeout(Duration::from_millis(150), request)
            .await
            .expect("response wait must use only the original deadline's remaining time")
            .expect("request task must not panic");
        assert!(matches!(
            result,
            Err(EventMeshError::Timeout(value)) if value == request_timeout
        ));
    }

    #[tokio::test]
    async fn blocked_fire_and_forget_send_uses_the_outbound_timeout() {
        let timeout = Duration::from_millis(20);
        let (conn, _outbound_rx) = blocked_test_connection_with_timeout(timeout);

        let result = tokio::time::timeout(
            Duration::from_secs(1),
            conn.send(Package::new(Header::new(
                Command::AsyncMessageToServer,
                "blocked",
            ))),
        )
        .await
        .expect("queue wait must respect the outbound timeout");

        assert!(matches!(
            result,
            Err(EventMeshError::Timeout(value)) if value == timeout
        ));
    }

    #[tokio::test(start_paused = true)]
    async fn send_and_flush_shares_one_timeout_for_queueing_and_completion() {
        let timeout = Duration::from_millis(100);
        let (conn, mut outbound_rx) = blocked_test_connection_with_timeout(timeout);
        let sender = tokio::spawn(async move {
            conn.send_and_flush(message::package(Command::BroadcastMessageToServer))
                .await
        });
        tokio::task::yield_now().await;
        tokio::time::advance(Duration::from_millis(60)).await;
        let _occupied = outbound_rx.recv().await.unwrap();
        // Retain the command without completing its write, so both queueing
        // and completion consume the same caller-side timeout budget.
        let _broadcast = outbound_rx.recv().await.unwrap();
        assert!(
            !sender.is_finished(),
            "enqueueing alone must not complete the send"
        );
        tokio::time::advance(Duration::from_millis(40)).await;
        let result = tokio::time::timeout(Duration::from_millis(1), sender)
            .await
            .expect("completion must use the original deadline")
            .unwrap();
        assert!(matches!(result, Err(EventMeshError::Timeout(value)) if value == timeout));
    }

    #[tokio::test]
    async fn shutdown_wakes_a_sender_waiting_for_write_completion() {
        let (conn, mut outbound_rx) = blocked_test_connection();
        let _occupied = outbound_rx.recv().await.unwrap();
        let sender = {
            let conn = Arc::clone(&conn);
            tokio::spawn(async move {
                conn.send_and_flush(message::package(Command::BroadcastMessageToServer))
                    .await
            })
        };
        let _broadcast = outbound_rx.recv().await.unwrap();

        tokio::time::timeout(Duration::from_secs(1), conn.shutdown())
            .await
            .expect("waiting for write completion must not hold the lifecycle lock");
        let result = tokio::time::timeout(Duration::from_secs(1), sender)
            .await
            .expect("shutdown must interrupt the completion wait")
            .unwrap();
        assert!(matches!(result, Err(EventMeshError::ChannelClosed(_))));
    }

    #[tokio::test]
    async fn cancellation_wakes_a_sender_waiting_for_queue_capacity() {
        let (conn, _outbound_rx) = blocked_test_connection();
        let sender = {
            let conn = Arc::clone(&conn);
            tokio::spawn(async move {
                conn.send(Package::new(Header::new(
                    Command::AsyncMessageToServer,
                    "blocked",
                )))
                .await
            })
        };
        tokio::task::yield_now().await;

        conn.cancel.cancel();
        let result = tokio::time::timeout(Duration::from_secs(1), sender)
            .await
            .expect("cancellation must wake the blocked sender")
            .expect("sender task must not panic");

        assert!(matches!(result, Err(EventMeshError::ChannelClosed(_))));
    }

    #[tokio::test]
    async fn shutdown_does_not_wait_for_a_full_outbound_queue() {
        let (conn, _outbound_rx) = blocked_test_connection();

        tokio::time::timeout(Duration::from_secs(1), conn.shutdown())
            .await
            .expect("shutdown must not wait for outbound queue capacity");

        assert!(conn.cancel.is_cancelled());
    }

    #[tokio::test]
    async fn hello_response_wait_uses_the_control_timeout() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = tokio::spawn(async move {
            let (_stream, _) = listener.accept().await.unwrap();
            std::future::pending::<()>().await;
        });
        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_connect_timeout(Duration::from_secs(1))
            .with_control_timeout(Duration::from_millis(20))
            .with_heartbeat_interval(Duration::from_secs(60))
            .with_reconnect(ReconnectPolicy::default().with_enabled(false));
        let user_agent = super::super::frame::UserAgent::from_role(
            config.identity(),
            config.credentials(),
            "g",
            config.endpoint().port(),
            "pub",
        );

        let result = TcpConnection::connect(
            &config.endpoint().authority_host(),
            config.endpoint().port(),
            &user_agent,
            config.heartbeat_interval(),
            config.connect_timeout(),
            config.control_timeout(),
            config.reconnect().clone(),
        )
        .await;
        assert!(matches!(
            result,
            Err(EventMeshError::Timeout(timeout)) if timeout == Duration::from_millis(20)
        ));
        server.abort();
    }

    /// Dropping the entire `io()` future must notify the driver to remove its
    /// pending entry. A late RESPONSE_TO_CLIENT is therefore orphaned and must
    /// not be ACKed as though a caller were still waiting for it.
    #[tokio::test]
    async fn externally_cancelled_io_removes_driver_pending_entry() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (request_seen_tx, request_seen_rx) = oneshot::channel();
        let (send_late_response_tx, send_late_response_rx) = oneshot::channel();

        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());

            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            framed
                .send(Package::new(Header::new(Command::HelloResponse, "hello")))
                .await
                .unwrap();

            let request = framed.next().await.unwrap().unwrap();
            assert_eq!(request.header.cmd, Command::RequestToServer);
            let seq = request.header.seq.unwrap_or_default();
            let _ = request_seen_tx.send(());
            let _ = send_late_response_rx.await;

            // Give the driver a chance to receive the cancellation command
            // emitted synchronously when the request future was dropped.
            tokio::task::yield_now().await;
            tokio::time::sleep(Duration::from_millis(20)).await;
            framed
                .send(Package::new(Header::new(Command::ResponseToClient, seq)))
                .await
                .unwrap();

            // A leaked pending entry would make the client ACK this response.
            // An orphan response is dropped by a default producer connection.
            tokio::time::timeout(Duration::from_millis(100), framed.next())
                .await
                .is_err()
        });

        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_control_timeout(Duration::from_secs(1))
            .with_heartbeat_interval(Duration::from_secs(60))
            .with_reconnect(ReconnectPolicy::default().with_enabled(false));
        let user_agent = super::super::frame::UserAgent::from_role(
            config.identity(),
            config.credentials(),
            "g",
            config.endpoint().port(),
            "pub",
        );
        let conn = Arc::new(
            TcpConnection::connect(
                &config.endpoint().authority_host(),
                config.endpoint().port(),
                &user_agent,
                config.heartbeat_interval(),
                config.connect_timeout(),
                config.control_timeout(),
                config.reconnect().clone(),
            )
            .await
            .expect("connect"),
        );

        let request = {
            let conn = Arc::clone(&conn);
            tokio::spawn(async move {
                conn.io(
                    Package::new(Header::new(Command::RequestToServer, "cancel-me")),
                    Duration::from_secs(30),
                )
                .await
            })
        };
        request_seen_rx
            .await
            .expect("server did not receive request");
        request.abort();
        let _ = request.await;
        let _ = send_late_response_tx.send(());

        assert!(server.await.expect("server task failed"));
        conn.shutdown().await;
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

        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_control_timeout(Duration::from_secs(3))
            .with_heartbeat_interval(Duration::from_secs(60))
            .with_reconnect(ReconnectPolicy::default().with_enabled(false));

        let producer =
            crate::transport::tcp::TcpProducer::connect(config, &ProducerOptions::new("g"))
                .await
                .expect("connect");

        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("ping")
            .build()
            .unwrap();
        let reply = producer
            .request_reply(msg, Duration::from_secs(3))
            .await
            .expect("request_reply");
        assert_eq!(reply.topic(), "reply");
        assert_eq!(reply.content(), "pong");

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

        let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_control_timeout(Duration::from_secs(3))
            .with_heartbeat_interval(Duration::from_secs(60))
            .with_reconnect(
                ReconnectPolicy::default()
                    .with_enabled(true)
                    .with_initial_backoff(Duration::from_millis(100))
                    .with_max_backoff(Duration::from_millis(500)),
            );

        let user_agent = super::super::frame::UserAgent::from_role(
            config.identity(),
            config.credentials(),
            "g",
            config.endpoint().port(),
            "pub",
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
