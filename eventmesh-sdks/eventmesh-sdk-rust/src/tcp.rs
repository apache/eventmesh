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

//! Native TCP client API.

use crate::config::{ConsumerOptions, ProducerOptions, TcpConfig};
use crate::error::Result;
use crate::handler::PublicHandler;
use crate::message::{Message, PublishReceipt};
use crate::subscription::Subscription;
use crate::transport::tcp::{TcpConsumer as LegacyConsumer, TcpProducer as LegacyProducer};
use crate::transport::Publisher as LegacyPublisher;
use crate::MessageHandler;

/// A configured EventMesh TCP client.
#[derive(Clone)]
pub struct TcpClient {
    config: TcpConfig,
}

impl TcpClient {
    /// Create a TCP client handle.  Connections are opened by role factories.
    pub fn new(config: TcpConfig) -> Self {
        Self { config }
    }

    /// Connect a producer role to the TCP endpoint.
    pub async fn producer(&self, options: ProducerOptions) -> Result<TcpProducer> {
        Ok(TcpProducer {
            inner: LegacyProducer::connect(self.config.legacy(Some(&options), None)).await?,
            timeout: self.config.request_timeout(),
        })
    }

    /// Connect a long-lived TCP consumer role.
    pub async fn consumer<H>(&self, options: ConsumerOptions, handler: H) -> Result<TcpConsumer<H>>
    where
        H: MessageHandler,
    {
        Ok(TcpConsumer {
            inner: LegacyConsumer::connect(
                self.config.legacy(None, Some(&options)),
                PublicHandler::new(handler),
                None::<std::future::Ready<()>>,
            )
            .await?,
        })
    }
}

/// TCP publishing capability.
pub struct TcpProducer {
    inner: LegacyProducer,
    timeout: std::time::Duration,
}

/// A long-lived TCP consumer.
pub struct TcpConsumer<H: MessageHandler> {
    inner: LegacyConsumer<PublicHandler<H>>,
}

impl<H: MessageHandler> TcpConsumer<H> {
    /// Add a TCP subscription.
    pub async fn subscribe(&self, subscription: Subscription) -> Result<()> {
        self.inner.subscribe(&[subscription.as_legacy()]).await
    }

    /// Remove a TCP subscription.
    pub async fn unsubscribe(&self, subscription: Subscription) -> Result<()> {
        self.inner
            .unsubscribe(vec![subscription.as_legacy()])
            .await
            .map(|_| ())
    }

    /// Request consumer shutdown.
    pub async fn shutdown(&self) {
        self.inner.shutdown().await;
    }

    /// Wait for TCP consumer shutdown.
    pub async fn join(&self) -> Result<()> {
        shutdown_result(self.inner.wait_for_shutdown().await)
    }
}

fn shutdown_result(reason: crate::transport::tcp::ShutdownReason) -> Result<()> {
    match reason {
        crate::transport::tcp::ShutdownReason::Cancelled => Ok(()),
        crate::transport::tcp::ShutdownReason::Redirect(info) => {
            Err(crate::error::EventMeshError::Tcp(format!(
                "server redirected consumer to {}:{}",
                info.ip, info.port
            )))
        }
        crate::transport::tcp::ShutdownReason::ChannelClosed => Err(
            crate::error::EventMeshError::ChannelClosed("TCP consumer connection closed".into()),
        ),
        crate::transport::tcp::ShutdownReason::Error(message) => {
            Err(crate::error::EventMeshError::Tcp(message))
        }
    }
}

impl TcpProducer {
    /// Publish one event and wait for EventMesh acknowledgement.
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

    /// Broadcast an event without waiting for a broker acknowledgement.
    pub async fn broadcast(&self, message: Message) -> Result<()> {
        match message {
            Message::EventMesh(message) => self.inner.broadcast(message).await,
            Message::Open(message) => self.inner.broadcast_open_message(message).await,
            #[cfg(feature = "cloud_events")]
            Message::CloudEvent(event) => self.inner.broadcast_cloud_event(event).await,
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

    /// Shut down the TCP connection.
    pub async fn shutdown(&self) {
        self.inner.shutdown().await;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::transport::tcp::frame::RedirectInfo;
    use crate::transport::tcp::ShutdownReason;

    #[test]
    fn abnormal_consumer_shutdown_is_an_error() {
        assert!(shutdown_result(ShutdownReason::ChannelClosed).is_err());
        assert!(shutdown_result(ShutdownReason::Error("driver failed".into())).is_err());
        let error = shutdown_result(ShutdownReason::Redirect(RedirectInfo {
            ip: "127.0.0.2".into(),
            port: 10_000,
        }))
        .unwrap_err();
        assert!(error.to_string().contains("127.0.0.2:10000"));
    }

    #[test]
    fn cancelled_consumer_shutdown_is_clean() {
        assert!(shutdown_result(ShutdownReason::Cancelled).is_ok());
    }
}
