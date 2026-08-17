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
use std::future::Future;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::{mpsc, Mutex};
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::{debug, info, warn};

use crate::common::constants::SDK_STREAM_URL;
use crate::common::status_code::StatusCode;
use crate::config::{ConsumerOptions, GrpcConfig};
use crate::model::EventMeshProtocolType;
use crate::proto_gen::PbCloudEvent;
use crate::subscription::Subscription;
use crate::transport::grpc::client::ChannelClient;
use crate::transport::grpc::codec;
use crate::transport::grpc::consumer::SubscriptionEntry;

/// Initial delay before the first heartbeat.
const HEARTBEAT_INITIAL_DELAY: Duration = Duration::from_secs(10);
/// Interval between heartbeats.
pub const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(30);

/// Type alias for the shared stream sender used to re-send stream subscriptions
/// during resubscribe. `None` when no stream is currently active.
pub(crate) type StreamTx = Arc<Mutex<Option<mpsc::Sender<PbCloudEvent>>>>;

/// Outcome of an operation bounded by a timeout and consumer shutdown.
pub(crate) enum OperationOutcome<T> {
    /// The operation completed before either bound was reached.
    Completed(T),
    /// The configured timeout elapsed first.
    TimedOut,
    /// Consumer shutdown was requested first.
    Cancelled,
}

/// Await an operation until it completes, its deadline expires, or shutdown
/// is requested. Shutdown wins when it is already ready so callers never
/// start more work after the consumer begins closing.
pub(crate) async fn await_with_timeout_or_shutdown<T>(
    shutdown: &CancellationToken,
    timeout: Duration,
    operation: impl Future<Output = T>,
) -> OperationOutcome<T> {
    tokio::select! {
        biased;
        _ = shutdown.cancelled() => OperationOutcome::Cancelled,
        result = tokio::time::timeout(timeout, operation) => match result {
            Ok(value) => OperationOutcome::Completed(value),
            Err(_) => OperationOutcome::TimedOut,
        },
    }
}

/// Clone the active stream sender while holding the state lock only long
/// enough to read it. Sending through a bounded channel can await indefinitely
/// under backpressure, so it must always happen after this lock is released.
pub(crate) async fn stream_sender(stream_tx: &StreamTx) -> Option<mpsc::Sender<PbCloudEvent>> {
    stream_tx.lock().await.clone()
}

/// Spawn the heartbeat loop. Reads the consumer's current `(topic, url)`
/// subscriptions each tick and reports them to the broker. The loop exits
/// promptly when `shutdown` is cancelled, so dropping / shutting down the
/// consumer no longer leaks a permanently-running task.
///
/// Scheduling mirrors the Java SDK's `scheduleAtFixedRate`: the first tick
/// fires after `HEARTBEAT_INITIAL_DELAY`, subsequent ticks align to a fixed
/// grid of `HEARTBEAT_INTERVAL`. The default `Burst` missed-tick behavior
/// matches Java's "catch up by one (non-concurrent)" semantics — if a tick
/// overruns, the next fires immediately rather than shifting the grid.
///
/// When the server returns `CLIENT_RESUBSCRIBE`, the loop automatically
/// re-registers all active subscriptions: webhook subscriptions are re-sent via
/// the `subscribe` RPC, stream subscriptions are re-sent over `stream_tx`.
///
/// Returns the task's [`JoinHandle`] so the owner can await clean exit.
pub(crate) fn spawn(
    client: ChannelClient,
    config: GrpcConfig,
    consumer: ConsumerOptions,
    subscriptions: Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    stream_tx: StreamTx,
    shutdown: CancellationToken,
) -> JoinHandle<()> {
    tokio::spawn(async move {
        let mut interval = tokio::time::interval_at(
            tokio::time::Instant::now() + HEARTBEAT_INITIAL_DELAY,
            HEARTBEAT_INTERVAL,
        );
        // Default `MissedTickBehavior::Burst` mirrors Java's
        // `scheduleAtFixedRate`: an overrun is followed by an immediate
        // catch-up tick rather than a delayed one.
        loop {
            tokio::select! {
                _ = interval.tick() => {}
                _ = shutdown.cancelled() => return,
            }
            let items: Vec<(String, String)> = subscriptions
                .lock()
                .await
                .iter()
                .map(|((topic, url), _entry)| (topic.clone(), url.clone()))
                .collect();
            if items.is_empty() {
                debug!("heartbeat tick: no subscriptions yet");
            } else if let Ok(event) = codec::build_heartbeat(&config, consumer.group(), &items) {
                // Bound the RPC and let shutdown interrupt it, so a network
                // black-hole cannot keep the heartbeat task alive indefinitely.
                let outcome = await_with_timeout_or_shutdown(
                    &shutdown,
                    config.request_timeout(),
                    client.heartbeat(event),
                )
                .await;
                match outcome {
                    OperationOutcome::Completed(Ok(resp)) => {
                        let response = codec::to_response(&resp);
                        if response.code == Some(StatusCode::CLIENT_RESUBSCRIBE as i64) {
                            warn!("server requested resubscribe (CLIENT_RESUBSCRIBE)");
                            resubscribe(
                                &client,
                                &config,
                                &consumer,
                                &subscriptions,
                                &stream_tx,
                                &shutdown,
                            )
                            .await;
                            if shutdown.is_cancelled() {
                                return;
                            }
                        }
                        debug!("heartbeat ok: {} items", items.len());
                    }
                    OperationOutcome::Completed(Err(e)) => warn!("heartbeat failed: {e}"),
                    OperationOutcome::TimedOut => {
                        warn!("heartbeat timed out after {:?}", config.request_timeout())
                    }
                    OperationOutcome::Cancelled => return,
                }
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
    client: &ChannelClient,
    config: &GrpcConfig,
    consumer: &ConsumerOptions,
    subscriptions: &Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    stream_tx: &StreamTx,
    shutdown: &CancellationToken,
) {
    // Collect and group subscriptions by URL. We hold the lock only briefly.
    let groups: HashMap<String, Vec<Subscription>> = {
        let guard = subscriptions.lock().await;
        if guard.is_empty() {
            return;
        }
        let mut groups: HashMap<String, Vec<Subscription>> = HashMap::new();
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
            consumer.group(),
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
            match stream_sender(stream_tx).await {
                Some(tx) => {
                    // Reserve capacity before moving `event` into the channel.
                    // A timeout or shutdown therefore cancels only the wait
                    // for capacity. Crucially, the StreamTx mutex was released
                    // by `stream_sender` before this potentially long wait.
                    match await_with_timeout_or_shutdown(
                        shutdown,
                        config.request_timeout(),
                        tx.reserve(),
                    )
                    .await
                    {
                        OperationOutcome::Completed(Ok(permit)) => {
                            permit.send(event);
                            debug!("resubscribe: re-sent {} stream subscriptions", items.len());
                        }
                        OperationOutcome::Completed(Err(_)) => warn!(
                            "resubscribe: stream channel closed; \
                             stream subscriptions will not be re-sent"
                        ),
                        OperationOutcome::TimedOut => warn!(
                            "resubscribe: stream channel stayed backpressured \
                             for {:?}; stream subscriptions will not be re-sent",
                            config.request_timeout()
                        ),
                        OperationOutcome::Cancelled => return,
                    }
                }
                None => warn!(
                    "resubscribe: no active stream; \
                     stream subscriptions will not be re-sent"
                ),
            }
        } else {
            match await_with_timeout_or_shutdown(
                shutdown,
                config.request_timeout(),
                client.subscribe_webhook(event),
            )
            .await
            {
                OperationOutcome::Completed(Ok(_)) => {
                    debug!("resubscribe: webhook re-registered for url={url}")
                }
                OperationOutcome::Completed(Err(e)) => {
                    warn!("resubscribe: webhook re-register failed for url={url}: {e}")
                }
                OperationOutcome::TimedOut => warn!(
                    "resubscribe: webhook re-register timed out for url={url} after {:?}",
                    config.request_timeout()
                ),
                OperationOutcome::Cancelled => return,
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use std::future::pending;

    use super::*;

    #[tokio::test]
    async fn operation_wait_stops_on_shutdown() {
        let shutdown = CancellationToken::new();
        let (started_tx, started_rx) = tokio::sync::oneshot::channel();
        let task_shutdown = shutdown.clone();
        let task = tokio::spawn(async move {
            await_with_timeout_or_shutdown(&task_shutdown, Duration::from_secs(60), async move {
                let _ = started_tx.send(());
                pending::<()>().await
            })
            .await
        });

        started_rx.await.expect("operation should start");
        shutdown.cancel();

        assert!(matches!(
            task.await.expect("task should not panic"),
            OperationOutcome::Cancelled
        ));
    }

    #[tokio::test(start_paused = true)]
    async fn operation_wait_times_out() {
        let shutdown = CancellationToken::new();
        let (started_tx, started_rx) = tokio::sync::oneshot::channel();
        let task = tokio::spawn(async move {
            await_with_timeout_or_shutdown(&shutdown, Duration::from_secs(1), async move {
                let _ = started_tx.send(());
                pending::<()>().await
            })
            .await
        });

        started_rx.await.expect("operation should start");
        tokio::time::advance(Duration::from_secs(1)).await;

        assert!(matches!(
            task.await.expect("task should not panic"),
            OperationOutcome::TimedOut
        ));
    }

    #[tokio::test]
    async fn stream_sender_returns_a_snapshot_without_retaining_the_lock() {
        let (tx, _rx) = mpsc::channel(1);
        let stream_tx = Arc::new(Mutex::new(Some(tx)));

        assert!(stream_sender(&stream_tx).await.is_some());
        assert!(stream_tx.try_lock().is_ok());
    }
}
