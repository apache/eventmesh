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

//! Background heartbeat loop for the gRPC consumer.

use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::{debug, warn};

use crate::common::status_code::StatusCode;
use crate::config::GrpcClientConfig;
use crate::transport::grpc::client::GrpcClient;
use crate::transport::grpc::codec::build_heartbeat;
use crate::transport::grpc::consumer::SubscriptionEntry;

/// Initial delay before the first heartbeat.
const HEARTBEAT_INITIAL_DELAY: Duration = Duration::from_secs(10);
/// Interval between heartbeats.
pub const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(30);

/// Spawn the heartbeat loop. Reads the consumer's current `(topic, url)`
/// subscriptions each tick and reports them to the broker. The loop exits
/// promptly when `shutdown` is cancelled, so dropping / shutting down the
/// consumer no longer leaks a permanently-running task.
///
/// Returns the task's [`JoinHandle`] so the owner can await clean exit.
pub(crate) fn spawn(
    client: GrpcClient,
    config: GrpcClientConfig,
    subscriptions: Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
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
            } else if let Ok(event) = build_heartbeat(&config, &items) {
                match client.heartbeat(event).await {
                    Ok(resp) => {
                        let response =
                            crate::transport::grpc::codec::CloudEventCodec::to_response(&resp);
                        if response.code == Some(StatusCode::CLIENT_RESUBSCRIBE as i64) {
                            warn!("server requested resubscribe (CLIENT_RESUBSCRIBE)");
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
