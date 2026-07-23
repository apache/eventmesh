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

//! gRPC client API.

use crate::config::{ConsumerOptions, GrpcConfig, GrpcConsumerOptions, ProducerOptions};
use crate::error::{EventMeshError, Result};
use crate::handler::PublicHandler;
use crate::message::{Message, PublishReceipt};
use crate::subscription::Subscription;
use crate::transport::grpc::{
    GrpcClient as ChannelClient, GrpcProducer as LegacyProducer,
    GrpcStreamConsumer as LegacyConsumer, GrpcWebhookConsumer as LegacyWebhookConsumer,
};
use crate::transport::Publisher as LegacyPublisher;
use crate::MessageHandler;

/// A configured EventMesh gRPC client.
#[derive(Clone)]
pub struct GrpcClient {
    config: GrpcConfig,
}

impl GrpcClient {
    /// Validate and create a gRPC client handle.
    ///
    /// Channel creation remains lazy; network I/O begins with the first
    /// operation or stream consumer.
    pub fn new(config: GrpcConfig) -> Result<Self> {
        config.validate()?;
        ChannelClient::new(&config.legacy(None, None))?;
        Ok(Self { config })
    }

    /// Create a producer role using `options`.
    pub fn producer(&self, options: ProducerOptions) -> Result<GrpcProducer> {
        options.validate()?;
        Ok(GrpcProducer {
            inner: LegacyProducer::connect(self.config.legacy(Some(&options), None))?,
            timeout: self.config.request_timeout(),
        })
    }

    /// Open a long-lived gRPC stream consumer.
    ///
    /// gRPC requires at least one initial subscription to open its stream;
    /// additional subscriptions can be added on the returned consumer.
    pub async fn stream_consumer<H>(
        &self,
        options: GrpcConsumerOptions,
        subscriptions: impl IntoIterator<Item = Subscription>,
        handler: H,
    ) -> Result<GrpcConsumer<H>>
    where
        H: MessageHandler,
    {
        options.validate()?;
        let subscriptions: Vec<_> = subscriptions
            .into_iter()
            .map(|subscription| subscription.as_legacy())
            .collect();
        let inner = LegacyConsumer::subscribe_stream(
            self.config.legacy_stream(&options),
            PublicHandler::new(handler),
            subscriptions,
            None::<std::future::Ready<()>>,
        )
        .await?;
        Ok(GrpcConsumer { inner })
    }

    /// Create a gRPC webhook-registration consumer.
    ///
    /// The EventMesh runtime delivers to the URL registered on the returned
    /// value over HTTP. Use the SDK's `webhook::WebhookServer` (with the
    /// `http` feature) or an application-owned HTTP endpoint to receive those
    /// deliveries.
    pub async fn webhook_consumer(&self, options: ConsumerOptions) -> Result<GrpcWebhookConsumer> {
        options.validate()?;
        Ok(GrpcWebhookConsumer {
            inner: LegacyWebhookConsumer::new(
                self.config.legacy(None, Some(&options)),
                None::<std::future::Ready<()>>,
            )
            .await?,
        })
    }
}

/// gRPC publishing capability.
pub struct GrpcProducer {
    inner: LegacyProducer,
    timeout: std::time::Duration,
}

/// A long-lived gRPC stream consumer.
pub struct GrpcConsumer<H: MessageHandler> {
    inner: LegacyConsumer<PublicHandler<H>>,
}

/// A gRPC consumer that registers HTTP webhook subscriptions.
pub struct GrpcWebhookConsumer {
    inner: LegacyWebhookConsumer,
}

impl GrpcWebhookConsumer {
    /// Register one or more subscriptions to an HTTP webhook URL.
    pub async fn subscribe(
        &self,
        subscriptions: impl IntoIterator<Item = Subscription>,
        webhook_url: impl Into<String>,
    ) -> Result<()> {
        self.inner
            .subscribe_webhook(
                subscriptions
                    .into_iter()
                    .map(|subscription| subscription.as_legacy())
                    .collect(),
                webhook_url,
            )
            .await
            .map(|_| ())
    }

    /// Remove one or more subscriptions from an HTTP webhook URL.
    pub async fn unsubscribe(
        &self,
        subscriptions: impl IntoIterator<Item = Subscription>,
        webhook_url: impl Into<String>,
    ) -> Result<()> {
        self.inner
            .unsubscribe_webhook(
                subscriptions
                    .into_iter()
                    .map(|subscription| subscription.as_legacy())
                    .collect(),
                webhook_url,
            )
            .await
            .map(|_| ())
    }

    /// Stop the heartbeat task.
    pub async fn shutdown(&self) {
        self.inner.shutdown().await;
    }

    /// Wait for the heartbeat task to stop.
    pub async fn join(&self) {
        self.inner.wait_for_shutdown().await;
    }
}

impl<H: MessageHandler> GrpcConsumer<H> {
    /// Add a subscription to the active stream.
    pub async fn subscribe(&self, subscription: Subscription) -> Result<()> {
        self.inner.subscribe(vec![subscription.as_legacy()]).await
    }

    /// Remove a stream subscription.
    pub async fn unsubscribe(&self, subscription: Subscription) -> Result<()> {
        self.inner
            .unsubscribe_stream(vec![subscription.as_legacy()])
            .await
            .map(|_| ())
    }

    /// Request graceful stream shutdown.
    pub async fn shutdown(&self) {
        self.inner.shutdown().await;
    }

    /// Wait for stream shutdown.
    pub async fn join(&self) -> Result<()> {
        self.inner.wait_for_shutdown().await
    }
}

impl GrpcProducer {
    /// Publish one event and wait for the EventMesh acknowledgement.
    pub async fn publish(&self, message: Message) -> Result<PublishReceipt> {
        match message {
            Message::EventMesh(message) => self
                .inner
                .publish(message)
                .await
                .map(PublishReceipt::from_legacy),
            #[cfg(feature = "cloud_events")]
            Message::CloudEvent(event) => self
                .inner
                .publish_cloud_event(event)
                .await
                .map(PublishReceipt::from_legacy),
        }
    }

    /// Publish a homogeneous batch of events through gRPC's batch RPC.
    pub async fn publish_batch(&self, messages: Vec<Message>) -> Result<PublishReceipt> {
        if messages.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "batch publish requires at least one message".into(),
            ));
        }

        #[cfg(feature = "cloud_events")]
        if messages
            .iter()
            .all(|message| matches!(message, Message::CloudEvent(_)))
        {
            let events = messages
                .into_iter()
                .map(|message| match message {
                    Message::CloudEvent(event) => event,
                    _ => unreachable!("all messages were checked above"),
                })
                .collect();
            return self
                .inner
                .publish_cloud_event_batch(events)
                .await
                .map(PublishReceipt::from_legacy);
        }

        #[cfg(feature = "cloud_events")]
        if messages
            .iter()
            .any(|message| matches!(message, Message::CloudEvent(_)))
        {
            return Err(EventMeshError::Unsupported(
                "mixed EventMesh and CloudEvents gRPC batches".into(),
            ));
        }
        self.inner
            .publish_message_batch(messages)
            .await
            .map(PublishReceipt::from_legacy)
    }

    /// Send an event and await its reply.
    pub async fn request_reply(&self, message: Message) -> Result<Message> {
        self.request_reply_with_timeout(message, self.timeout).await
    }

    /// Send an event and await its reply with a per-operation timeout.
    pub async fn request_reply_with_timeout(
        &self,
        message: Message,
        timeout: std::time::Duration,
    ) -> Result<Message> {
        if timeout.is_zero() {
            return Err(EventMeshError::InvalidArgument(
                "request/reply timeout must be greater than zero".into(),
            ));
        }
        match message {
            Message::EventMesh(message) => self
                .inner
                .request_reply(message, timeout)
                .await
                .map(Message::EventMesh),
            #[cfg(feature = "cloud_events")]
            Message::CloudEvent(event) => self
                .inner
                .request_reply_cloud_event(event, timeout)
                .await
                .map(Message::CloudEvent),
        }
    }
}
