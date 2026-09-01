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

//! Connection establishment, HELLO handshake, and reconnect supervision.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use futures::SinkExt;
use tokio::net::TcpStream;
use tokio::sync::{mpsc, Mutex};
use tokio_stream::StreamExt;
use tokio_util::codec::Framed;
use tokio_util::sync::CancellationToken;
use tracing::{debug, info, warn};

use crate::config::ReconnectPolicy;
use crate::error::{EventMeshError, Result};

use super::super::codec::TcpCodec;
use super::super::frame::{Command, Package, UserAgent};
use super::super::message;
use super::dispatcher::{OutboundCommand, PendingKey, RpcDispatcher};
use super::driver::{IoExitReason, SocketDriver};

#[derive(Debug)]
pub(super) struct ConnectionState {
    pub(super) generation: u64,
    pub(super) active: bool,
}

pub(super) struct ConnectionSupervisor;

impl ConnectionSupervisor {
    pub(super) async fn establish(
        addr: &str,
        port: u16,
        user_agent: &UserAgent,
        connect_timeout: Duration,
        control_timeout: Duration,
    ) -> Result<Framed<TcpStream, TcpCodec>> {
        let peer = format!("{addr}:{port}");
        debug!(%peer, "connecting TCP");
        let stream = tokio::time::timeout(connect_timeout, TcpStream::connect(&peer))
            .await
            .map_err(|_| EventMeshError::Timeout(connect_timeout))??;
        stream.set_nodelay(true).ok();

        let mut framed = Framed::new(stream, TcpCodec::new());
        debug!("sending HELLO");
        let deadline = Self::deadline_after(control_timeout)?;
        tokio::time::timeout_at(deadline, framed.send(message::hello(user_agent)))
            .await
            .map_err(|_| EventMeshError::Timeout(control_timeout))??;

        match tokio::time::timeout_at(deadline, framed.next()).await {
            Err(_) => Err(EventMeshError::Timeout(control_timeout)),
            Ok(None) => Err(EventMeshError::Tcp("connection closed during HELLO".into())),
            Ok(Some(Err(error))) => Err(error),
            Ok(Some(Ok(response))) if response.header.cmd == Command::HelloResponse => {
                if response.header.code != 0 {
                    return Err(EventMeshError::Server {
                        code: response.header.code,
                        message: response
                            .header
                            .desc
                            .unwrap_or_else(|| "HELLO rejected".into()),
                    });
                }
                debug!(code = response.header.code, "HELLO ok");
                Ok(framed)
            }
            Ok(Some(Ok(response))) => Err(EventMeshError::Tcp(format!(
                "unexpected response to HELLO: {:?}",
                response.header.cmd
            ))),
        }
    }

    #[allow(clippy::too_many_arguments)]
    pub(super) async fn run(
        addr: String,
        port: u16,
        user_agent: UserAgent,
        heartbeat_interval: Duration,
        connect_timeout: Duration,
        control_timeout: Duration,
        reconnect: ReconnectPolicy,
        mut framed: Framed<TcpStream, TcpCodec>,
        mut outbound_rx: mpsc::Receiver<OutboundCommand>,
        inbound_tx: mpsc::Sender<Package>,
        reconnect_tx: mpsc::Sender<()>,
        mut pending_cancel_rx: mpsc::UnboundedReceiver<PendingKey>,
        state: Arc<Mutex<ConnectionState>>,
        deliver_orphan_responses: Arc<AtomicBool>,
        cancel: CancellationToken,
        alive: Arc<AtomicBool>,
    ) {
        let mut dispatcher = RpcDispatcher::new();
        loop {
            let generation = state.lock().await.generation;
            let reason = SocketDriver::run(
                &mut framed,
                &mut outbound_rx,
                &mut pending_cancel_rx,
                &inbound_tx,
                &mut dispatcher,
                &deliver_orphan_responses,
                generation,
                heartbeat_interval,
                control_timeout,
                &cancel,
            )
            .await;

            {
                let mut state = state.lock().await;
                state.active = false;
                state.generation = state.generation.wrapping_add(1);
                alive.store(false, Ordering::Release);
                dispatcher.connection_lost();
                while outbound_rx.try_recv().is_ok() {}
            }

            match reason {
                IoExitReason::Cancelled | IoExitReason::AllSendersDropped => {
                    debug!(?reason, "connection task exiting");
                    return;
                }
                IoExitReason::IoError | IoExitReason::ServerClosed | IoExitReason::SlowConsumer => {
                }
            }

            if !reconnect.enabled() || cancel.is_cancelled() {
                debug!("reconnect disabled or cancelled, exiting");
                return;
            }

            let mut backoff = reconnect.initial_backoff();
            let mut attempt = 0usize;
            loop {
                attempt += 1;
                if attempt > reconnect.max_retries() {
                    warn!(
                        attempts = attempt - 1,
                        "max reconnect attempts ({}) exceeded, giving up",
                        reconnect.max_retries()
                    );
                    return;
                }

                debug!(attempt, ?backoff, "reconnect backoff");
                tokio::select! {
                    biased;
                    _ = cancel.cancelled() => {
                        debug!("cancelled during reconnect backoff");
                        return;
                    }
                    _ = tokio::time::sleep(backoff) => {}
                }
                backoff = backoff.saturating_mul(2).min(reconnect.max_backoff());

                let result = tokio::select! {
                    biased;
                    _ = cancel.cancelled() => {
                        debug!("cancelled during reconnect attempt");
                        return;
                    }
                    result = Self::establish(
                        &addr,
                        port,
                        &user_agent,
                        connect_timeout,
                        control_timeout,
                    ) => result,
                };

                match result {
                    Ok(new_framed) => {
                        info!(attempt, peer = %format!("{addr}:{port}"), "TCP reconnected");
                        state.lock().await.active = true;
                        alive.store(true, Ordering::Release);
                        let _ = reconnect_tx.try_send(());
                        framed = new_framed;
                        break;
                    }
                    Err(error) => warn!(attempt, %error, "reconnect attempt failed"),
                }
            }
        }
    }

    fn deadline_after(timeout: Duration) -> Result<tokio::time::Instant> {
        tokio::time::Instant::now()
            .checked_add(timeout)
            .ok_or_else(|| EventMeshError::InvalidArgument("TCP timeout is too large".into()))
    }
}
