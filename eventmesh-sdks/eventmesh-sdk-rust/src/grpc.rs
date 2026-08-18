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

//! gRPC channel and role API.

use crate::config::{ConsumerOptions, GrpcConfig, GrpcConsumerOptions, ProducerOptions};
use crate::error::{EventMeshError, Result};
use crate::handler::PublicHandler;
use crate::message::{Message, PublishReceipt};
use crate::subscription::Subscription;
use crate::transport::grpc::{
    ChannelClient as TransportChannel, GrpcProducer as TransportProducer,
    GrpcStreamConsumer as TransportConsumer, GrpcWebhookConsumer as TransportWebhookConsumer,
};
use crate::transport::{Publisher as TransportPublisher, RequestReply as TransportRequestReply};
use crate::MessageHandler;

/// A connected EventMesh gRPC channel.
///
/// Create the channel inside the Tokio runtime that will use it. Producers and
/// consumers built from clones of the channel share one multiplexed HTTP/2
/// connection. To use EventMesh from another runtime, connect another channel
/// in that runtime instead of moving this value across runtime lifetimes.
#[derive(Clone)]
pub struct GrpcChannel {
    config: GrpcConfig,
    inner: TransportChannel,
}

impl GrpcChannel {
    /// Validate `config` and connect on the current Tokio runtime.
    pub async fn connect(config: GrpcConfig) -> Result<Self> {
        let inner = TransportChannel::connect(&config).await?;
        Ok(Self { config, inner })
    }

    #[cfg(test)]
    fn connect_lazy(config: GrpcConfig) -> Result<Self> {
        let inner = TransportChannel::connect_lazy(&config)?;
        Ok(Self { config, inner })
    }
}

/// gRPC publishing capability.
pub struct GrpcProducer {
    inner: TransportProducer,
    timeout: std::time::Duration,
}

/// A long-lived gRPC stream consumer.
pub struct GrpcStreamConsumer<H: MessageHandler> {
    inner: TransportConsumer<PublicHandler<H>>,
}

/// A gRPC consumer that registers HTTP webhook subscriptions.
///
/// [`shutdown`](Self::shutdown) and [`join`](Self::join) only stop local
/// heartbeat work. Before shutting down, call [`unsubscribe`](Self::unsubscribe)
/// for every remotely registered subscription and webhook URL.
pub struct GrpcWebhookConsumer {
    inner: TransportWebhookConsumer,
}

impl GrpcWebhookConsumer {
    /// Create a webhook-registration consumer over `channel`.
    ///
    /// The EventMesh runtime delivers events to the registered URL over HTTP.
    /// Use the SDK's [`crate::webhook::WebhookServer`] or an application-owned
    /// HTTP endpoint to receive those deliveries.
    pub async fn new(channel: GrpcChannel, options: ConsumerOptions) -> Result<Self> {
        Ok(Self {
            inner: TransportWebhookConsumer::new(
                channel.inner,
                channel.config,
                options,
                None::<std::future::Ready<()>>,
            )
            .await?,
        })
    }

    /// Register one or more subscriptions to an HTTP webhook URL.
    pub async fn subscribe(
        &self,
        subscriptions: impl IntoIterator<Item = Subscription>,
        webhook_url: impl Into<String>,
    ) -> Result<()> {
        let webhook_url = webhook_url.into();
        crate::webhook::validate_webhook_url(&webhook_url)?;
        let subscriptions: Vec<_> = subscriptions.into_iter().collect();
        for subscription in &subscriptions {
            subscription.validate()?;
        }
        self.inner
            .subscribe_webhook(subscriptions, webhook_url)
            .await
            .map(|_| ())
    }

    /// Remove one or more subscriptions from an HTTP webhook URL.
    pub async fn unsubscribe(
        &self,
        subscriptions: impl IntoIterator<Item = Subscription>,
        webhook_url: impl Into<String>,
    ) -> Result<()> {
        let webhook_url = webhook_url.into();
        crate::webhook::validate_webhook_url(&webhook_url)?;
        let subscriptions: Vec<_> = subscriptions.into_iter().collect();
        for subscription in &subscriptions {
            subscription.validate()?;
        }
        self.inner
            .unsubscribe_webhook(subscriptions, webhook_url)
            .await
            .map(|_| ())
    }

    /// Signal the heartbeat task to stop.
    ///
    /// This does not unregister webhook subscriptions from EventMesh. Call
    /// [`unsubscribe`](Self::unsubscribe) first when performing a graceful
    /// shutdown.
    pub fn shutdown(&self) {
        self.inner.request_shutdown();
    }

    /// Wait for the heartbeat task to stop and report task failure.
    pub async fn join(&self) -> Result<()> {
        self.inner.wait_for_shutdown().await
    }
}

impl<H: MessageHandler> GrpcStreamConsumer<H> {
    /// Open a bidirectional subscription stream over `channel`.
    ///
    /// At least one initial subscription is required. Additional subscriptions
    /// can be added after the stream opens with [`subscribe`](Self::subscribe).
    /// This operation requires a multi-threaded Tokio runtime so tonic's
    /// connection driver can progress while the stream is being opened.
    pub async fn open(
        channel: GrpcChannel,
        options: GrpcConsumerOptions,
        subscriptions: impl IntoIterator<Item = Subscription>,
        handler: H,
    ) -> Result<Self> {
        let subscriptions: Vec<_> = subscriptions.into_iter().collect();
        for subscription in &subscriptions {
            subscription.validate()?;
        }
        let inner = TransportConsumer::subscribe_stream(
            channel.inner,
            channel.config,
            options,
            PublicHandler::new(handler),
            subscriptions,
            None::<std::future::Ready<()>>,
        )
        .await?;
        Ok(Self { inner })
    }

    /// Add a subscription to the active stream.
    pub async fn subscribe(&self, subscription: Subscription) -> Result<()> {
        subscription.validate()?;
        self.inner.subscribe(vec![subscription]).await
    }

    /// Remove a stream subscription.
    pub async fn unsubscribe(&self, subscription: Subscription) -> Result<()> {
        subscription.validate()?;
        self.inner
            .unsubscribe_stream(vec![subscription])
            .await
            .map(|_| ())
    }

    /// Signal graceful stream shutdown.
    pub fn shutdown(&self) {
        self.inner.request_shutdown();
    }

    /// Wait for stream shutdown.
    pub async fn join(&self) -> Result<()> {
        self.inner.wait_for_shutdown().await
    }
}

impl GrpcProducer {
    /// Create a publishing role over `channel`.
    pub fn new(channel: GrpcChannel, options: ProducerOptions) -> Result<Self> {
        let timeout = channel.config.request_timeout();
        Ok(Self {
            inner: TransportProducer::new(channel.inner, channel.config, options)?,
            timeout,
        })
    }

    /// Publish one event and wait for the EventMesh acknowledgement.
    pub async fn publish(&self, message: Message) -> Result<PublishReceipt> {
        match message {
            Message::EventMesh(message) => self
                .inner
                .publish(message)
                .await
                .map(PublishReceipt::from_response),
            #[cfg(feature = "cloud_events")]
            Message::CloudEvent(event) => self
                .inner
                .publish_cloud_event(event)
                .await
                .map(PublishReceipt::from_response),
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
                .map(PublishReceipt::from_response);
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
            .map(PublishReceipt::from_response)
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::Endpoint;

    fn channel() -> GrpcChannel {
        let config = GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205).unwrap());
        GrpcChannel::connect_lazy(config).unwrap()
    }

    #[tokio::test]
    async fn cloned_channels_and_their_producers_share_one_connection() {
        let channel = channel();
        let cloned_channel = channel.clone();
        let first = GrpcProducer::new(channel.clone(), ProducerOptions::new("producer-a")).unwrap();
        let second =
            GrpcProducer::new(cloned_channel.clone(), ProducerOptions::new("producer-b")).unwrap();

        assert!(channel.inner.shares_channel_with(&cloned_channel.inner));
        assert!(channel.inner.shares_channel_with(first.inner.client()));
        assert!(channel.inner.shares_channel_with(second.inner.client()));
    }

    #[tokio::test]
    async fn producer_and_webhook_consumer_share_one_channel() {
        let channel = channel();
        let producer =
            GrpcProducer::new(channel.clone(), ProducerOptions::new("producer")).unwrap();
        let consumer = GrpcWebhookConsumer::new(channel.clone(), ConsumerOptions::new("consumer"))
            .await
            .unwrap();

        assert!(channel.inner.shares_channel_with(producer.inner.client()));
        assert!(channel.inner.shares_channel_with(consumer.inner.client()));

        consumer.shutdown();
        consumer.join().await.unwrap();
    }
}
