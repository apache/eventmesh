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

//! Read, write, heartbeat, and push routing for one connected socket.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use futures::SinkExt;
use tokio::net::TcpStream;
use tokio::sync::mpsc;
use tokio_stream::StreamExt;
use tokio_util::codec::Framed;
use tokio_util::sync::CancellationToken;
use tracing::{debug, info, warn};

use crate::error::EventMeshError;

use super::super::codec::TcpCodec;
use super::super::frame::{Command, Package};
use super::super::message;
use super::dispatcher::{OutboundCommand, PendingKey, RpcDispatcher};

#[derive(Debug)]
pub(super) enum IoExitReason {
    Cancelled,
    AllSendersDropped,
    IoError,
    ServerClosed,
    SlowConsumer,
}

/// Runs all I/O for exactly one established TCP socket.
pub(super) struct SocketDriver;

impl SocketDriver {
    #[allow(clippy::too_many_arguments)]
    pub(super) async fn run(
        framed: &mut Framed<TcpStream, TcpCodec>,
        outbound_rx: &mut mpsc::Receiver<OutboundCommand>,
        pending_cancel_rx: &mut mpsc::UnboundedReceiver<PendingKey>,
        inbound_tx: &mpsc::Sender<Package>,
        dispatcher: &mut RpcDispatcher,
        deliver_orphan_responses: &Arc<AtomicBool>,
        generation: u64,
        heartbeat_interval: Duration,
        write_timeout: Duration,
        cancel: &CancellationToken,
    ) -> IoExitReason {
        use tokio::time::MissedTickBehavior;

        let mut heartbeat = tokio::time::interval(heartbeat_interval);
        heartbeat.set_missed_tick_behavior(MissedTickBehavior::Delay);
        heartbeat.tick().await;

        loop {
            tokio::select! {
                biased;

                _ = cancel.cancelled() => {
                    debug!("connection task cancelled");
                    return IoExitReason::Cancelled;
                }

                key = pending_cancel_rx.recv() => {
                    if let Some(key) = key {
                        dispatcher.cancel(&key);
                    }
                }

                command = outbound_rx.recv() => {
                    match command {
                        Some(OutboundCommand::Send(package)) => {
                            if let Err((reason, _)) = Self::write_frame(
                                framed,
                                package,
                                write_timeout,
                                cancel,
                            ).await {
                                return reason;
                            }
                        }
                        Some(OutboundCommand::SendAndFlush { package, completion_tx }) => {
                            // Do not write a queued broadcast whose caller was
                            // cancelled or timed out before the driver reached it.
                            if completion_tx.is_closed() {
                                continue;
                            }
                            match Self::write_frame(framed, package, write_timeout, cancel).await {
                                Ok(()) => {
                                    let _ = completion_tx.send(Ok(()));
                                }
                                Err((reason, error)) => {
                                    let _ = completion_tx.send(Err(error));
                                    return reason;
                                }
                            }
                        }
                        Some(OutboundCommand::Request { package, key, response_tx }) => {
                            if !dispatcher.register(key.clone(), response_tx) {
                                continue;
                            }
                            if let Err((reason, _)) = Self::write_frame(
                                framed,
                                package,
                                write_timeout,
                                cancel,
                            ).await {
                                dispatcher.cancel(&key);
                                return reason;
                            }
                        }
                        None => {
                            debug!("all senders dropped, stopping connection task");
                            return IoExitReason::AllSendersDropped;
                        }
                    }
                }

                result = framed.next() => {
                    match result {
                        Some(Ok(package)) => {
                            if let Some(reason) = Self::handle_inbound(
                                framed,
                                inbound_tx,
                                dispatcher,
                                deliver_orphan_responses,
                                generation,
                                write_timeout,
                                cancel,
                                package,
                            ).await {
                                return reason;
                            }
                        }
                        Some(Err(error)) => {
                            warn!(%error, "read error, connection lost");
                            return IoExitReason::IoError;
                        }
                        None => {
                            info!("connection closed by server");
                            return IoExitReason::ServerClosed;
                        }
                    }
                }

                _ = heartbeat.tick() => {
                    if let Err((reason, _)) = Self::write_frame(
                        framed,
                        message::heartbeat(),
                        write_timeout,
                        cancel,
                    ).await {
                        return reason;
                    }
                    debug!("heartbeat sent");
                }
            }
        }
    }

    #[allow(clippy::too_many_arguments)]
    async fn handle_inbound(
        framed: &mut Framed<TcpStream, TcpCodec>,
        inbound_tx: &mpsc::Sender<Package>,
        dispatcher: &mut RpcDispatcher,
        deliver_orphan_responses: &Arc<AtomicBool>,
        generation: u64,
        write_timeout: Duration,
        cancel: &CancellationToken,
        package: Package,
    ) -> Option<IoExitReason> {
        if package.header.cmd == Command::HeartbeatResponse {
            debug!("heartbeat response received");
            return None;
        }

        let seq = package.header.seq.clone().unwrap_or_default();
        if let Some(response_tx) = dispatcher.take_response(generation, seq) {
            if package.header.cmd == Command::ResponseToClient {
                let ack = message::response_to_client_ack(&package);
                if let Err((reason, _)) =
                    Self::write_frame(framed, ack, write_timeout, cancel).await
                {
                    let _ = response_tx.send(package);
                    return Some(reason);
                }
            }
            let _ = response_tx.send(package);
            return None;
        }

        if package.header.cmd == Command::ResponseToClient
            && !deliver_orphan_responses.load(Ordering::Acquire)
        {
            debug!("dropping orphan RESPONSE_TO_CLIENT");
            return None;
        }

        match inbound_tx.try_send(package) {
            Ok(()) => None,
            Err(mpsc::error::TrySendError::Full(_)) => {
                warn!(
                    "inbound channel full — disconnecting to trigger server redelivery of \
                     unacked messages"
                );
                Some(IoExitReason::SlowConsumer)
            }
            Err(mpsc::error::TrySendError::Closed(_)) => {
                debug!("inbound channel closed (consumer dropped)");
                None
            }
        }
    }

    async fn write_frame(
        framed: &mut Framed<TcpStream, TcpCodec>,
        package: Package,
        timeout: Duration,
        cancel: &CancellationToken,
    ) -> std::result::Result<(), (IoExitReason, EventMeshError)> {
        tokio::select! {
            biased;
            _ = cancel.cancelled() => Err((
                IoExitReason::Cancelled,
                EventMeshError::ChannelClosed("TCP write cancelled by shutdown".into()),
            )),
            result = tokio::time::timeout(timeout, framed.send(package)) => match result {
                Ok(Ok(())) => Ok(()),
                Ok(Err(error)) => {
                    warn!(%error, "TCP write failed; connection lost");
                    Err((IoExitReason::IoError, error))
                }
                Err(_) => {
                    warn!(?timeout, "TCP write timed out; connection lost");
                    Err((IoExitReason::IoError, EventMeshError::Timeout(timeout)))
                }
            },
        }
    }
}
