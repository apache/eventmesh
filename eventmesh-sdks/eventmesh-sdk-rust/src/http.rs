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

//! HTTP client API.

/// Framework-independent helpers for custom webhook endpoints.
pub mod codec {
    pub use crate::transport::http::codec::{
        parse_push_body, PushMessageRequestBody, WebhookReply,
    };
}

use crate::config::{ConsumerOptions, HttpConfig, ProducerOptions};
use crate::error::Result;
use crate::message::{Message, PublishReceipt};
use crate::subscription::{DeliveryType, Subscription};
use crate::transport::http::{HttpConsumer as LegacyConsumer, HttpProducer as LegacyProducer};
use crate::transport::Publisher as LegacyPublisher;

/// A configured EventMesh HTTP client.
#[derive(Clone)]
pub struct HttpClient {
    config: HttpConfig,
}

impl HttpClient {
    /// Validate and create an HTTP client handle.
    pub fn new(config: HttpConfig) -> Result<Self> {
        // Constructing the private HTTP client validates the endpoint set and
        // request client without issuing network I/O.
        LegacyProducer::new(config.legacy(None, None))?;
        Ok(Self { config })
    }

    /// Create a publishing role.
    pub fn producer(&self, options: ProducerOptions) -> Result<HttpProducer> {
        Ok(HttpProducer {
            inner: LegacyProducer::new(self.config.legacy(Some(&options), None))?,
            timeout: self.config.request_timeout(),
        })
    }

    /// Create a long-lived webhook-registration consumer.
    pub fn webhook_consumer(&self, options: ConsumerOptions) -> Result<HttpConsumer> {
        Ok(HttpConsumer {
            inner: LegacyConsumer::new(
                self.config.legacy(None, Some(&options)),
                None::<std::future::Ready<()>>,
            )?,
        })
    }
}

/// HTTP publishing capability.
pub struct HttpProducer {
    inner: LegacyProducer,
    timeout: std::time::Duration,
}

impl HttpProducer {
    /// Publish one event.
    pub async fn publish(&self, message: Message) -> Result<PublishReceipt> {
        match message {
            Message::EventMesh(message) => self
                .inner
                .publish(message)
                .await
                .map(PublishReceipt::from_legacy),
            Message::Open(message) => self
                .inner
                .publish_open_message(message)
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

    /// Send an event and await its reply.
    pub async fn request_reply(&self, message: Message) -> Result<Message> {
        match message {
            Message::EventMesh(message) => self
                .inner
                .request_reply(message, self.timeout)
                .await
                .map(Message::EventMesh),
            Message::Open(message) => self
                .inner
                .request_reply_open_message(message, self.timeout)
                .await
                .map(Message::Open),
            #[cfg(feature = "cloud_events")]
            Message::CloudEvent(event) => self
                .inner
                .request_reply_cloud_event(event, self.timeout)
                .await
                .map(Message::CloudEvent),
        }
    }
}

/// A long-lived HTTP webhook registration and heartbeat manager.
pub struct HttpConsumer {
    inner: LegacyConsumer,
}

impl HttpConsumer {
    /// Register `subscription` to send deliveries to `webhook_url`.
    pub async fn subscribe(
        &self,
        subscription: Subscription,
        webhook_url: impl Into<String>,
    ) -> Result<()> {
        if subscription.delivery_type == DeliveryType::Sync {
            return Err(crate::error::EventMeshError::Unsupported(
                "HTTP request/reply subscriptions".into(),
            ));
        }
        self.inner
            .subscribe_webhook(vec![subscription.as_legacy()], webhook_url)
            .await
            .map(|_| ())
    }

    /// Remove a previously registered webhook subscription.
    pub async fn unsubscribe(&self, subscription: Subscription) -> Result<()> {
        self.inner
            .unsubscribe(vec![subscription.as_legacy()])
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
