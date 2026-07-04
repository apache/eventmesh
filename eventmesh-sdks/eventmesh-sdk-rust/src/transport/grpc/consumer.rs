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

//! gRPC consumer.

use std::collections::HashMap;
use std::future::{Future, IntoFuture};
use std::pin::Pin;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tonic::codegen::tokio_stream::StreamExt;
use tracing::{debug, error, warn};

use crate::common::constants::SDK_STREAM_URL;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, EventMeshProtocolType, PublishResponse, SubscriptionItem};
use crate::transport::grpc::client::GrpcClient;
use crate::transport::grpc::codec::CloudEventCodec;
use crate::transport::grpc::heartbeat::{self, StreamTx};
use crate::transport::Subscriber;
use crate::MessageListener;

/// gRPC-based consumer, generic over the user's [`MessageListener`] type.
///
/// Use [`GrpcConsumer::new`] with your own listener. The listener's
/// `Message` associated type must be [`EventMeshMessage`] for this consumer.
///
/// A background heartbeat task is spawned on construction and tied to an
/// internal cancellation token. It is stopped automatically when the consumer
/// is dropped, or explicitly via [`GrpcConsumer::shutdown`].
pub struct GrpcConsumer<L: MessageListener<Message = EventMeshMessage>> {
    client: GrpcClient,
    config: crate::config::GrpcClientConfig,
    /// topic -> entry, for heartbeat and unsubscribe.
    subscriptions: Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    listener: Arc<L>,
    /// Shared shutdown signal for the heartbeat and stream driver.
    shutdown: CancellationToken,
    /// Handle to the background heartbeat task, awaited by `shutdown` /
    /// aborted by `Drop`.
    heartbeat_handle: Mutex<Option<JoinHandle<()>>>,
    /// Sender for the currently-active bidirectional stream, if any.
    ///
    /// Set by [`StreamServe`] when the stream opens and cleared when it closes.
    /// The heartbeat loop reads this to re-send stream subscriptions when the
    /// server signals `CLIENT_RESUBSCRIBE`.
    stream_tx: StreamTx,
}

#[derive(Debug, Clone)]
pub(crate) struct SubscriptionEntry {
    #[allow(dead_code)]
    pub(crate) item: SubscriptionItem,
    pub(crate) url: String,
}

impl<L: MessageListener<Message = EventMeshMessage>> GrpcConsumer<L> {
    /// Create a consumer. Spawns a background heartbeat task that is stopped
    /// on [`GrpcConsumer::shutdown`] or drop.
    pub fn new(config: crate::config::GrpcClientConfig, listener: L) -> Result<Self> {
        let client = GrpcClient::new(&config)?;
        let subscriptions = Arc::new(Mutex::new(HashMap::new()));
        let shutdown = CancellationToken::new();
        let stream_tx: StreamTx = Arc::new(Mutex::new(None));
        let heartbeat_handle = heartbeat::spawn(
            client.clone(),
            config.clone(),
            Arc::clone(&subscriptions),
            Arc::clone(&stream_tx),
            shutdown.clone(),
        );
        Ok(Self {
            client,
            config,
            subscriptions,
            listener: Arc::new(listener),
            shutdown,
            heartbeat_handle: Mutex::new(Some(heartbeat_handle)),
            stream_tx,
        })
    }

    /// Subscribe via webhook: the server POSTs delivered events to `url`.
    pub async fn subscribe_webhook(
        &self,
        items: Vec<SubscriptionItem>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        let url = url.into();
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }
        let event = CloudEventCodec::build_subscription_event(
            &self.config,
            EventMeshProtocolType::EventMeshMessage,
            Some(&url),
            &items,
        )?;
        let resp = timed(self.config.timeout, self.client.subscribe_webhook(event)).await?;
        let response = CloudEventCodec::to_response(&resp);
        // Only record the subscription locally when the broker actually
        // accepted it, so the heartbeat loop doesn't advertise rejected subs.
        if response.is_success() {
            self.record(items, url).await;
        }
        Ok(response)
    }

    /// Prepare a bidirectional stream subscription, returning a foreground
    /// [`StreamServe`] driver.
    ///
    /// This is **synchronous** (it only validates and encodes the subscription
    /// request) so that, axum-style, a single `.await` drives everything:
    ///
    /// ```ignore
    /// // drive until the server closes the stream
    /// consumer.subscribe_stream(items)?.await?;
    /// // or bind a shutdown signal
    /// consumer.subscribe_stream(items)?.with_graceful_shutdown(sig).await?;
    /// ```
    ///
    /// The actual gRPC stream is opened on the first poll of the returned
    /// driver; opening/IO errors therefore surface from the `.await`, not from
    /// this call. Dropping the driver without awaiting opens nothing.
    pub fn subscribe_stream(&self, items: Vec<SubscriptionItem>) -> Result<StreamServe<L>> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }
        let event = CloudEventCodec::build_subscription_event(
            &self.config,
            EventMeshProtocolType::EventMeshMessage,
            None,
            &items,
        )?;
        Ok(StreamServe {
            client: self.client.clone(),
            event,
            record_items: items,
            subscriptions: Arc::clone(&self.subscriptions),
            listener: Arc::clone(&self.listener),
            config: self.config.clone(),
            shutdown: self.shutdown.clone(),
            stream_tx: Arc::clone(&self.stream_tx),
        })
    }

    async fn record(&self, items: Vec<SubscriptionItem>, url: String) {
        let mut guard = self.subscriptions.lock().await;
        for item in items {
            guard.insert(
                item.topic.clone(),
                SubscriptionEntry {
                    item,
                    url: url.clone(),
                },
            );
        }
    }

    /// Current consumer group.
    pub fn consumer_group(&self) -> &str {
        &self.config.identity.consumer_group
    }

    /// Explicitly shut down: cancel the shared token and await the heartbeat
    /// task's exit. Any active [`StreamServe`] driver observing the same token
    /// also stops.
    pub async fn shutdown(&self) {
        self.shutdown.cancel();
        if let Some(handle) = self.heartbeat_handle.lock().await.take() {
            let _ = handle.await;
        }
    }
}

impl<L: MessageListener<Message = EventMeshMessage>> Subscriber for GrpcConsumer<L> {
    async fn subscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        // Fire-and-forget: spawn the driver in the background. It stops when
        // the consumer is dropped / shut down (shared cancellation token).
        let serve = self.subscribe_stream(items)?;
        tokio::spawn(async move {
            if let Err(e) = serve.await {
                warn!("stream driver exited with error: {e}");
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
        let event = CloudEventCodec::build_subscription_event(
            &self.config,
            EventMeshProtocolType::EventMeshMessage,
            None,
            &items,
        )?;
        let resp = timed(self.config.timeout, self.client.unsubscribe(event)).await?;
        let response = CloudEventCodec::to_response(&resp);
        // Only drop the local entries when the server confirms the unsubscribe;
        // otherwise the heartbeat loop would stop reporting still-active subs.
        if response.is_success() {
            let mut guard = self.subscriptions.lock().await;
            for item in &items {
                guard.remove(&item.topic);
            }
        }
        Ok(response)
    }
}

impl<L: MessageListener<Message = EventMeshMessage>> Drop for GrpcConsumer<L> {
    fn drop(&mut self) {
        // Signal the heartbeat (and any stream driver) to stop, then abort the
        // heartbeat task if it is still running. `Drop` is sync, so we cannot
        // await; explicit `shutdown()` is the awaitable path.
        self.shutdown.cancel();
        if let Ok(mut guard) = self.heartbeat_handle.try_lock() {
            if let Some(handle) = guard.take() {
                handle.abort();
            }
        }
    }
}

/// Apply the config's default request timeout to a short unary RPC.
async fn timed<T>(timeout: Duration, f: impl std::future::Future<Output = Result<T>>) -> Result<T> {
    tokio::time::timeout(timeout, f)
        .await
        .map_err(|_| EventMeshError::Timeout(timeout))?
}

/// Build a reply CloudEvent (used by the stream receive loop when the listener
/// returns `Some(message)`).
///
/// Mirrors the Java SDK's `SubStreamHandler.buildReplyMessage`: the incoming
/// request's attributes are carried over into the reply so the broker can
/// correlate the reply with the original request. The reply's own attributes
/// take precedence; the request only fills keys the reply leaves unset (notably
/// `correlation99id` / `reply99to99client`, which RocketMQ needs to match the
/// reply back to the pending `RequestFuture`).
pub(crate) fn build_reply(
    reply: EventMeshMessage,
    request: &crate::proto_gen::PbCloudEvent,
    config: &crate::config::GrpcClientConfig,
) -> Result<crate::proto_gen::PbCloudEvent> {
    let mut event = CloudEventCodec::from_event_mesh_message(&reply, config)?;
    for (key, value) in &request.attributes {
        event
            .attributes
            .entry(key.clone())
            .or_insert_with(|| value.clone());
    }
    CloudEventCodec::mark_as_reply(&mut event);
    Ok(event)
}

/// Foreground driver for a bidirectional stream subscription.
///
/// Returned (synchronously) by [`GrpcConsumer::subscribe_stream`]. The gRPC
/// stream is opened lazily on the first poll, so awaiting this driver both
/// subscribes and runs the receive loop in one step — dispatching delivered
/// messages to the registered listener and sending back replies until the
/// server closes the stream or a graceful shutdown fires.
///
/// Bind an external trigger (Ctrl-C, a `oneshot`, etc.) with
/// [`StreamServe::with_graceful_shutdown`]; resolving that signal cancels the
/// consumer's shared token, stopping both this driver and the background
/// heartbeat.
///
/// # Example
///
/// ```no_run
/// # use eventmesh::{
/// #     config::GrpcClientConfig, grpc::GrpcConsumer,
/// #     model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
/// #     MessageListener,
/// # };
/// # #[tokio::main]
/// # async fn main() -> eventmesh::Result<()> {
/// #     let consumer = GrpcConsumer::new(GrpcClientConfig::builder().build(), MyListener)?;
/// #     let items = vec![SubscriptionItem::new("t", SubscriptionMode::CLUSTERING, SubscriptionType::ASYNC)];
/// consumer
///     .subscribe_stream(items)?
///     .with_graceful_shutdown(async { tokio::signal::ctrl_c().await.ok(); })
///     .await?;
/// #     Ok(())
/// # }
/// # struct MyListener;
/// # impl MessageListener for MyListener {
/// #     type Message = EventMeshMessage;
/// #     async fn handle(&self, _: Self::Message) -> Option<Self::Message> { None }
/// # }
/// ```
pub struct StreamServe<L: MessageListener<Message = EventMeshMessage>> {
    client: GrpcClient,
    event: crate::proto_gen::PbCloudEvent,
    record_items: Vec<SubscriptionItem>,
    subscriptions: Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    listener: Arc<L>,
    config: crate::config::GrpcClientConfig,
    shutdown: CancellationToken,
    /// Shared slot where the stream sender is registered so the heartbeat
    /// resubscribe path can re-send subscriptions over the active stream.
    stream_tx: StreamTx,
}

impl<L: MessageListener<Message = EventMeshMessage>> StreamServe<L> {
    /// Bind an external shutdown signal.
    ///
    /// When `signal` resolves the consumer's shared cancellation token is
    /// triggered, which stops both this driver's receive loop and the
    /// background heartbeat. The returned `StreamServe` is the receiver, so
    /// you can chain `.await` directly.
    pub fn with_graceful_shutdown(self, signal: impl Future<Output = ()> + Send + 'static) -> Self {
        let token = self.shutdown.clone();
        tokio::spawn(async move {
            // Exit the watcher either when the signal fires (cancel the token)
            // or when the token is already cancelled (consumer dropped), so the
            // watcher task never leaks.
            tokio::select! {
                _ = signal => token.cancel(),
                _ = token.cancelled() => {}
            }
        });
        self
    }
}

impl<L: MessageListener<Message = EventMeshMessage>> IntoFuture for StreamServe<L> {
    type Output = Result<()>;
    type IntoFuture = Pin<Box<dyn Future<Output = Result<()>> + Send>>;

    fn into_future(self) -> Self::IntoFuture {
        let Self {
            client,
            event,
            record_items,
            subscriptions,
            listener,
            config,
            shutdown,
            stream_tx,
        } = self;
        Box::pin(async move {
            // Open the stream lazily here so `subscribe_stream` can stay
            // synchronous (single `.await` at the call site, axum-style).
            let (reply_tx, mut stream) = client.subscribe_stream(event).await?;
            let reply_tx = Arc::new(reply_tx);

            // Register the stream sender so the heartbeat loop can re-send
            // subscriptions when the server signals CLIENT_RESUBSCRIBE.
            {
                *stream_tx.lock().await = Some((*reply_tx).clone());
            }

            // Record the subscription so the heartbeat advertises it.
            {
                let mut guard = subscriptions.lock().await;
                for item in record_items {
                    guard.insert(
                        item.topic.clone(),
                        SubscriptionEntry {
                            item,
                            url: SDK_STREAM_URL.to_string(),
                        },
                    );
                }
            }

            loop {
                tokio::select! {
                    msg = stream.next() => match msg {
                        None => {
                            debug!("subscribe stream ended");
                            break;
                        }
                        Some(Err(status)) => {
                            warn!("stream receive error: {status}");
                            continue;
                        }
                        Some(Ok(cloud_event)) => {
                            let eventmesh_msg =
                                CloudEventCodec::to_event_mesh_message(&cloud_event);
                            // Skip control/ack frames: the broker echoes the
                            // subscription request back as the first stream
                            // message. Real messages always carry a seqnum;
                            // control frames don't.
                            if eventmesh_msg.biz_seq_no.is_none() {
                                debug!("skipping control frame (no seqnum)");
                                continue;
                            }
                            debug!("delivered topic={:?}", eventmesh_msg.topic);
                            match listener.handle(eventmesh_msg).await {
                                Some(reply) => match build_reply(reply, &cloud_event, &config) {
                                    Ok(reply_event) => {
                                        if reply_tx.send(reply_event).await.is_err() {
                                            warn!("reply channel closed; stopping receive loop");
                                            break;
                                        }
                                    }
                                    Err(e) => error!("failed to encode reply: {e}"),
                                },
                                None => { /* async ack: nothing to send back */ }
                            }
                        }
                    },
                    _ = shutdown.cancelled() => {
                        debug!("subscribe stream shutting down");
                        break;
                    }
                }
            }

            // Clear the registered stream sender so the heartbeat loop knows
            // the stream is no longer active and won't try to re-send through
            // a stale channel.
            *stream_tx.lock().await = None;

            Ok(())
        })
    }
}
