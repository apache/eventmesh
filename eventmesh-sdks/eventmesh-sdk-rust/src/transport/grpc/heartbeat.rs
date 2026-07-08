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

//! Background heartbeat loop for the gRPC consumer.

use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::{mpsc, Mutex};
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::{debug, info, warn};

use crate::common::constants::SDK_STREAM_URL;
use crate::common::status_code::StatusCode;
use crate::config::GrpcClientConfig;
use crate::model::{EventMeshProtocolType, SubscriptionItem};
use crate::proto_gen::PbCloudEvent;
use crate::transport::grpc::client::GrpcClient;
use crate::transport::grpc::codec;
use crate::transport::grpc::consumer::SubscriptionEntry;

/// Initial delay before the first heartbeat.
const HEARTBEAT_INITIAL_DELAY: Duration = Duration::from_secs(10);
/// Interval between heartbeats.
pub const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(30);

/// Type alias for the shared stream sender used to re-send stream subscriptions
/// during resubscribe. `None` when no stream is currently active.
pub(crate) type StreamTx = Arc<Mutex<Option<mpsc::Sender<PbCloudEvent>>>>;

/// Spawn the heartbeat loop. Reads the consumer's current `(topic, url)`
/// subscriptions each tick and reports them to the broker. The loop exits
/// promptly when `shutdown` is cancelled, so dropping / shutting down the
/// consumer no longer leaks a permanently-running task.
///
/// When the server returns `CLIENT_RESUBSCRIBE`, the loop automatically
/// re-registers all active subscriptions: webhook subscriptions are re-sent via
/// the `subscribe` RPC, stream subscriptions are re-sent over `stream_tx`.
///
/// Returns the task's [`JoinHandle`] so the owner can await clean exit.
pub(crate) fn spawn(
    client: GrpcClient,
    config: GrpcClientConfig,
    subscriptions: Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    stream_tx: StreamTx,
    shutdown: CancellationToken,
) -> JoinHandle<()> {
    tokio::spawn(async move {
        // Initial delay is itself interruptible.
        tokio::select! {
            _ = tokio::time::sleep(HEARTBEAT_INITIAL_DELAY) => {}
            _ = shutdown.cancelled() => return,
        }
        loop {
            let items: Vec<(String, String)> = subscriptions
                .lock()
                .await
                .iter()
                .map(|(t, e)| (t.clone(), e.url.clone()))
                .collect();
            if items.is_empty() {
                debug!("heartbeat tick: no subscriptions yet");
            } else if let Ok(event) = codec::build_heartbeat(&config, &items) {
                match client.heartbeat(event).await {
                    Ok(resp) => {
                        let response = codec::to_response(&resp);
                        if response.code == Some(StatusCode::CLIENT_RESUBSCRIBE as i64) {
                            warn!("server requested resubscribe (CLIENT_RESUBSCRIBE)");
                            resubscribe(&client, &config, &subscriptions, &stream_tx).await;
                        }
                        debug!("heartbeat ok: {} items", items.len());
                    }
                    Err(e) => warn!("heartbeat failed: {e}"),
                }
            }
            tokio::select! {
                _ = tokio::time::sleep(HEARTBEAT_INTERVAL) => {}
                _ = shutdown.cancelled() => break,
            }
        }
    })
}

/// Re-register all active subscriptions after the server signals
/// `CLIENT_RESUBSCRIBE`.
///
/// Subscriptions are grouped by URL. Webhook groups (url != `SDK_STREAM_URL`)
/// are re-registered via the `subscribe` unary RPC. Stream groups
/// (url == `SDK_STREAM_URL`) are re-sent as a subscription CloudEvent through
/// the active stream sender. If no stream is currently open, a warning is
/// logged and the stream subscriptions are skipped (the user must re-call
/// `subscribe_stream`).
async fn resubscribe(
    client: &GrpcClient,
    config: &GrpcClientConfig,
    subscriptions: &Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    stream_tx: &StreamTx,
) {
    // Collect and group subscriptions by URL. We hold the lock only briefly.
    let groups: HashMap<String, Vec<SubscriptionItem>> = {
        let guard = subscriptions.lock().await;
        if guard.is_empty() {
            return;
        }
        let mut groups: HashMap<String, Vec<SubscriptionItem>> = HashMap::new();
        for entry in guard.values() {
            groups
                .entry(entry.url.clone())
                .or_default()
                .push(entry.item.clone());
        }
        groups
    };

    info!("resubscribing {} group(s)", groups.len());

    for (url, items) in groups {
        let is_stream = url == SDK_STREAM_URL;
        let event = match codec::build_subscription_event(
            config,
            EventMeshProtocolType::EventMeshMessage,
            if is_stream { None } else { Some(&url) },
            &items,
        ) {
            Ok(e) => e,
            Err(e) => {
                warn!("resubscribe: failed to build subscription event for url={url}: {e}");
                continue;
            }
        };

        if is_stream {
            let guard = stream_tx.lock().await;
            match guard.as_ref() {
                Some(tx) => {
                    if tx.send(event).await.is_err() {
                        warn!(
                            "resubscribe: stream channel closed; \
                             stream subscriptions will not be re-sent"
                        );
                    } else {
                        debug!("resubscribe: re-sent {} stream subscriptions", items.len());
                    }
                }
                None => warn!(
                    "resubscribe: no active stream; \
                     stream subscriptions will not be re-sent"
                ),
            }
        } else {
            match client.subscribe_webhook(event).await {
                Ok(_) => debug!("resubscribe: webhook re-registered for url={url}"),
                Err(e) => warn!("resubscribe: webhook re-register failed for url={url}: {e}"),
            }
        }
    }
}
