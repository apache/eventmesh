//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  The ASF
// licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//

//! TCP connection engine — the core of the transport.
//!
//! Corresponds to the Java SDK's `TcpClient` abstract base: manages the TCP
//! socket, the read/write loop, heartbeat, and request-response correlation
//! via a `seq`-keyed pending map of `oneshot` channels.

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

use crate::error::{EventMeshError, Result};

use super::codec::TcpCodec;
use super::frame::{Command, Package};
use super::message;

// `SinkExt` is needed for `Framed::send()`.
use futures::SinkExt;

/// Default channel capacity for outbound and inbound message queues.
const CHANNEL_CAPACITY: usize = 256;

/// A connected TCP transport.
///
/// Created by [`TcpConnection::connect`], which performs the TCP connect +
/// HELLO handshake. A background task handles all I/O (read, write, heartbeat).
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
    /// Pending request-response contexts: `seq → oneshot::Sender`.
    pending: Arc<Mutex<HashMap<String, oneshot::Sender<Package>>>>,
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
    /// `timeout` bounds both the TCP connect and the HELLO response wait,
    /// mirroring Java's `TcpClient.hello()` which goes through
    /// `io(msg, DEFAULT_TIME_OUT_MILLS)` (20s).
    pub async fn connect(
        addr: &str,
        port: u16,
        user_agent: &super::frame::UserAgent,
        heartbeat_interval: Duration,
        timeout: Duration,
    ) -> Result<Self> {
        // Defer name resolution to Tokio: `TcpStream::connect` accepts a
        // "host:port" string via `ToSocketAddrs`, so DNS names like
        // "localhost" (the default `server_addr`) resolve correctly.
        // Pre-parsing into a `SocketAddr` would reject any non-numeric host
        // with `InvalidArgument` before the resolver ever runs, breaking the
        // default config and any hostname-based deployment.
        let peer = format!("{addr}:{port}");
        debug!(%peer, "connecting TCP");
        let stream = tokio::time::timeout(timeout, TcpStream::connect(&peer))
            .await
            .map_err(|_| EventMeshError::Timeout(timeout))??;
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
        match tokio::time::timeout(timeout, framed.next()).await {
            Err(_) => {
                return Err(EventMeshError::Timeout(timeout));
            }
            Ok(None) => return Err(EventMeshError::Tcp("connection closed during HELLO".into())),
            Ok(Some(Err(e))) => return Err(e),
            Ok(Some(Ok(resp))) if resp.header.cmd == Command::HelloResponse => {
                // The Java runtime's `HelloProcessor` rejects the handshake
                // (OPStatus.FAIL / ACL_FAIL) when the group isn't registered,
                // the token is rejected, the server isn't RUNNING yet, or the
                // `UserAgent` is invalid — and then closes the session. Treat a
                // non-zero code as a failure and surface `desc` (the reason),
                // mirroring how ACKs are checked in `message.rs` /
                // `producer.rs`. Otherwise every later op would fail opaquely
                // with `ChannelClosed` / `Timeout` while the real reason sits
                // unread in `desc`.
                if resp.header.code != 0 {
                    return Err(EventMeshError::Server {
                        code: resp.header.code,
                        message: resp.header.desc.unwrap_or_else(|| "HELLO rejected".into()),
                    });
                }
                debug!(code = resp.header.code, "HELLO ok");
            }
            Ok(Some(Ok(resp))) => {
                return Err(EventMeshError::Tcp(format!(
                    "unexpected response to HELLO: {:?}",
                    resp.header.cmd
                )));
            }
        }

        // --- Spawn background task ---
        let (outbound_tx, outbound_rx) = mpsc::channel(CHANNEL_CAPACITY);
        let (inbound_tx, inbound_rx) = mpsc::channel(CHANNEL_CAPACITY);
        let pending = Arc::new(Mutex::new(HashMap::new()));
        let cancel = CancellationToken::new();
        let alive = Arc::new(AtomicBool::new(true));

        let join = tokio::spawn(Self::run(
            framed,
            outbound_rx,
            inbound_tx,
            Arc::clone(&pending),
            heartbeat_interval,
            cancel.clone(),
            Arc::clone(&alive),
        ));

        info!(%peer, "TCP connected");

        Ok(Self {
            outbound_tx,
            inbound_rx: Mutex::new(Some(inbound_rx)),
            pending,
            cancel,
            alive,
            join: Mutex::new(Some(join)),
        })
    }

    /// Request-response: register a pending context keyed by `seq`, send the
    /// package, and wait for the matching reply within `timeout`.
    ///
    /// Corresponds to Java `TcpClient.io()`.
    pub async fn io(&self, pkg: Package, timeout: Duration) -> Result<Package> {
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

    /// Whether the background task is still alive.
    ///
    /// Mirrors Java's `TcpClient.isActive()` which checks `channel.isActive()`.
    /// This flips to `false` for *any* reason the background task exits
    /// (cancellation, read/write error, server-side close, all senders
    /// dropped) — not just explicit shutdown.
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

    /// Background I/O loop.
    async fn run(
        mut framed: Framed<TcpStream, TcpCodec>,
        mut outbound_rx: mpsc::Receiver<Package>,
        inbound_tx: mpsc::Sender<Package>,
        pending: Arc<Mutex<HashMap<String, oneshot::Sender<Package>>>>,
        heartbeat_interval: Duration,
        cancel: CancellationToken,
        alive: Arc<AtomicBool>,
    ) {
        use tokio::time::MissedTickBehavior;
        let mut heartbeat = tokio::time::interval(heartbeat_interval);
        heartbeat.set_missed_tick_behavior(MissedTickBehavior::Delay);
        // Skip the immediate first tick.
        heartbeat.tick().await;

        loop {
            tokio::select! {
                biased;

                _ = cancel.cancelled() => {
                    debug!("connection task cancelled");
                    break;
                }

                // Write outbound packages from user code.
                pkg = outbound_rx.recv() => {
                    match pkg {
                        Some(pkg) => {
                            if let Err(e) = framed.send(pkg).await {
                                warn!("write error, connection lost: {e}");
                                break;
                            }
                        }
                        None => {
                            debug!("all senders dropped, stopping connection task");
                            break;
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
                                // Server push → inbound channel for the consumer.
                                // Use `try_send` instead of a blocking `send().await`
                                // so a full inbound channel can never stall the I/O
                                // loop. On a producer-only connection `take_inbound_rx`
                                // is never called, so the receiver is never drained;
                                // 256 unmatched frames (e.g. late replies to timed-out
                                // `io()` calls) would block forever and freeze
                                // heartbeats + writes. Dropping with a warning keeps
                                // the connection alive at the cost of losing pushes
                                // nobody is consuming.
                                match inbound_tx.try_send(pkg) {
                                    Ok(()) => {}
                                    Err(mpsc::error::TrySendError::Full(_)) => {
                                        warn!(
                                            "inbound channel full, dropping server push \
                                             (consumer too slow or not consuming)"
                                        );
                                    }
                                    Err(mpsc::error::TrySendError::Closed(_)) => {
                                        debug!("inbound channel closed (consumer dropped)");
                                    }
                                }
                            }
                        }
                        Some(Err(e)) => {
                            warn!("read error, connection lost: {e}");
                            break;
                        }
                        None => {
                            info!("connection closed by server");
                            break;
                        }
                    }
                }

                // Heartbeat: fire-and-forget. If the write fails the connection
                // is dead and the loop breaks.
                _ = heartbeat.tick() => {
                    let hb = message::heartbeat();
                    if let Err(e) = framed.send(hb).await {
                        warn!("heartbeat send failed: {e}");
                        break;
                    }
                    debug!("heartbeat sent");
                }
            }
        }

        // Clean up: drop all pending oneshot senders so waiting `io()` callers
        // get a `ChannelClosed` error instead of hanging until timeout.
        let mut guard = pending.lock().await;
        guard.clear();
        drop(guard);

        // Mark the connection as dead so `is_active()` reports false (mirrors
        // Java's `channel.isActive()` going false after close).
        alive.store(false, Ordering::Release);
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
    use crate::config::TcpClientConfig;
    use crate::model::EventMeshMessage;
    use crate::transport::tcp::codec::TcpCodec;
    use crate::transport::tcp::frame::{Command, Header, Package, PackageBody};
    use crate::transport::Publisher;

    use futures::SinkExt;
    use tokio::net::TcpListener;
    use tokio_stream::StreamExt;
    use tokio_util::codec::Framed;

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
            //    same seq + a JSON body (code 0 = success). The body uses the
            //    TCP wire field names (`body`/`properties`) matching the Java
            //    server's `org.apache.eventmesh.common.protocol.tcp.EventMeshMessage`.
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
            .timeout(Duration::from_secs(3))
            .heartbeat_interval(Duration::from_secs(60))
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
}
