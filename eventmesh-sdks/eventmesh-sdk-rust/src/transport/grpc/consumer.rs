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
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;
use tonic::codegen::tokio_stream::StreamExt;
use tracing::{debug, error, warn};

use crate::common::constants::SDK_STREAM_URL;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, EventMeshProtocolType, PublishResponse, SubscriptionItem};
use crate::transport::grpc::client::GrpcClient;
use crate::transport::grpc::codec::CloudEventCodec;
use crate::transport::grpc::heartbeat;
use crate::transport::Subscriber;
use crate::MessageListener;

/// Handle to an active stream subscription; keeps the reply channel alive.
#[derive(Clone)]
pub struct SubscribeStreamHandle {
    /// Holding this sender keeps the bidirectional stream's request side
    /// alive for the broker (and lets the receive loop send replies).
    #[allow(dead_code)]
    pub(crate) reply_tx: Option<Arc<tokio::sync::mpsc::Sender<crate::proto_gen::PbCloudEvent>>>,
}

/// gRPC-based consumer, generic over the user's [`MessageListener`] type.
///
/// Use [`GrpcConsumer::new`] with your own listener. The listener's
/// `Message` associated type must be [`EventMeshMessage`] for this consumer.
pub struct GrpcConsumer<L: MessageListener<Message = EventMeshMessage>> {
    client: GrpcClient,
    config: crate::config::GrpcClientConfig,
    /// topic -> entry, for heartbeat and unsubscribe.
    subscriptions: Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    /// Active stream reply sender.
    stream: Mutex<Option<SubscribeStreamHandle>>,
    listener: Arc<L>,
}

#[derive(Debug, Clone)]
pub(crate) struct SubscriptionEntry {
    #[allow(dead_code)]
    pub(crate) item: SubscriptionItem,
    pub(crate) url: String,
}

impl<L: MessageListener<Message = EventMeshMessage>> GrpcConsumer<L> {
    /// Create a consumer. Spawns a background heartbeat task.
    pub fn new(config: crate::config::GrpcClientConfig, listener: L) -> Result<Self> {
        let client = GrpcClient::new(&config)?;
        let subscriptions = Arc::new(Mutex::new(HashMap::new()));
        heartbeat::spawn(client.clone(), config.clone(), Arc::clone(&subscriptions));
        Ok(Self {
            client,
            config,
            subscriptions,
            stream: Mutex::new(None),
            listener: Arc::new(listener),
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

    /// Subscribe via bidirectional stream and spawn the receive loop driving
    /// the registered listener.
    pub async fn subscribe_stream(&self, items: Vec<SubscriptionItem>) -> Result<()> {
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

        let (reply_tx, mut stream) = self.client.subscribe_stream(event).await?;
        let reply_tx = Arc::new(reply_tx);
        let handle = SubscribeStreamHandle {
            reply_tx: Some(Arc::clone(&reply_tx)),
        };
        *self.stream.lock().await = Some(handle);

        self.record(items, SDK_STREAM_URL.to_string()).await;

        let listener = Arc::clone(&self.listener);
        let config = self.config.clone();
        tokio::spawn(async move {
            while let Some(msg) = stream.next().await {
                let cloud_event = match msg {
                    Ok(ce) => ce,
                    Err(status) => {
                        warn!("stream receive error: {status}");
                        continue;
                    }
                };
                let eventmesh_msg = CloudEventCodec::to_event_mesh_message(&cloud_event);
                // Skip control/ack frames: the broker echoes the subscription
                // request back as the first stream message. Real messages always
                // carry a seqnum; control frames don't.
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
            debug!("subscribe stream ended");
        });
        Ok(())
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
}

impl<L: MessageListener<Message = EventMeshMessage>> Subscriber for GrpcConsumer<L> {
    async fn subscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        self.subscribe_stream(items).await?;
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
