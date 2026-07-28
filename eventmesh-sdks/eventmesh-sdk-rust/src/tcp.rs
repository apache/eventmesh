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
use crate::error::{EventMeshError, Result};
use crate::handler::PublicHandler;
use crate::message::{Message, PublishReceipt};
use crate::subscription::Subscription;
use crate::transport::tcp::{
    TcpConsumer as LegacyConsumer, TcpMessage, TcpProducer as LegacyProducer,
};
use crate::transport::{Publisher as LegacyPublisher, RequestReply as LegacyRequestReply};
use crate::MessageHandler;
use tracing::warn;

/// A configured EventMesh TCP client.
#[derive(Clone)]
pub struct TcpClient {
    config: TcpConfig,
}

impl TcpClient {
    /// Validate and create a TCP client handle. Connections are opened by role
    /// factories.
    pub fn new(config: TcpConfig) -> Result<Self> {
        config.validate()?;
        Ok(Self { config })
    }

    /// Connect a producer role to the TCP endpoint.
    pub async fn producer(&self, options: ProducerOptions) -> Result<TcpProducer> {
        options.validate()?;
        Ok(TcpProducer {
            inner: LegacyProducer::connect(self.config.legacy(Some(&options), None)).await?,
            timeout: self.config.request_timeout(),
            response_driver: tokio::sync::Mutex::new(None),
        })
    }

    /// Connect a producer that can handle server `RESPONSE_TO_CLIENT` frames.
    ///
    /// This is the Rust equivalent of Java TCP's `registerPubBusiHandler`.
    /// Normal request/reply responses remain owned by their originating
    /// `request_reply` futures; this handler receives unmatched server pushes.
    pub async fn producer_with_handler<H>(
        &self,
        options: ProducerOptions,
        handler: H,
    ) -> Result<TcpProducer>
    where
        H: MessageHandler,
    {
        let producer = self.producer(options).await?;
        producer.start_response_handler(handler).await?;
        Ok(producer)
    }

    /// Connect a long-lived TCP consumer role.
    pub async fn consumer<H>(&self, options: ConsumerOptions, handler: H) -> Result<TcpConsumer<H>>
    where
        H: MessageHandler,
    {
        options.validate()?;
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
    response_driver: tokio::sync::Mutex<Option<tokio::task::JoinHandle<()>>>,
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

    /// Remove every subscription on this TCP consumer session.
    ///
    /// The EventMesh TCP runtime ignores topics in `UNSUBSCRIBE_REQUEST` and
    /// always clears the entire session, matching Java's no-argument
    /// `unsubscribe()` API.
    pub async fn unsubscribe_all(&self) -> Result<()> {
        self.inner.unsubscribe_all().await.map(|_| ())
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
    ///
    /// # TCP CloudEvents compatibility
    ///
    /// When `message` is [`Message::CloudEvent`], its `datacontenttype` must be
    /// `application/cloudevents+json`. This is a non-standard compatibility
    /// requirement of EventMesh's Java TCP codec: it uses `datacontenttype` to
    /// select the serializer for the whole CloudEvent rather than only to
    /// describe the event's data. The SDK validates this before any network
    /// I/O and returns [`EventMeshError::InvalidMessage`] for other values,
    /// including the otherwise standard `application/json` and `text/plain`.
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

    /// Broadcast an event without waiting for a broker acknowledgement.
    ///
    /// [`Message::CloudEvent`] has the same TCP-specific `datacontenttype`
    /// requirement documented on [`publish`](Self::publish).
    pub async fn broadcast(&self, message: Message) -> Result<()> {
        match message {
            Message::EventMesh(message) => self.inner.broadcast(message).await,
            #[cfg(feature = "cloud_events")]
            Message::CloudEvent(event) => self.inner.broadcast_cloud_event(event).await,
        }
    }

    /// Send an event and await its reply.
    ///
    /// [`Message::CloudEvent`] has the same TCP-specific `datacontenttype`
    /// requirement documented on [`publish`](Self::publish).
    pub async fn request_reply(&self, message: Message) -> Result<Message> {
        self.request_reply_with_timeout(message, self.timeout).await
    }

    /// Send an event and await its reply with a per-operation timeout.
    ///
    /// [`Message::CloudEvent`] has the same TCP-specific `datacontenttype`
    /// requirement documented on [`publish`](Self::publish).
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

    /// Shut down the TCP connection.
    pub async fn shutdown(&self) {
        if let Some(driver) = self.response_driver.lock().await.take() {
            driver.abort();
        }
        self.inner.shutdown().await;
    }

    async fn start_response_handler<H>(&self, handler: H) -> Result<()>
    where
        H: MessageHandler,
    {
        let conn = self.inner.connection();
        conn.enable_orphan_response_delivery();
        let mut inbound = conn.take_inbound_rx().await.ok_or_else(|| {
            crate::error::EventMeshError::Tcp(
                "publisher response handler already registered".into(),
            )
        })?;
        let connection = self.inner.shared_connection();
        let driver = tokio::spawn(async move {
            while let Some(package) = inbound.recv().await {
                if package.header.cmd != crate::transport::tcp::frame::Command::ResponseToClient {
                    continue;
                }
                let Some(message) = Message::decode_tcp(&package) else {
                    warn!("failed to decode publisher-side response; closing without ACK");
                    connection.shutdown().await;
                    break;
                };
                match handler.handle(message).await {
                    Ok(Some(reply)) => match reply.encode_tcp_reply() {
                        Ok(reply) => {
                            if let Err(error) = connection.send(reply).await {
                                warn!(%error, "failed to send publisher-side reply; closing without ACK");
                                connection.shutdown().await;
                                break;
                            }
                        }
                        Err(error) => {
                            warn!(%error, "failed to encode publisher-side reply; closing without ACK");
                            connection.shutdown().await;
                            break;
                        }
                    },
                    Ok(None) => {}
                    Err(error) => {
                        warn!(%error, "publisher-side handler failed; closing without ACK");
                        connection.shutdown().await;
                        break;
                    }
                }
                if let Err(error) = connection
                    .send(crate::transport::tcp::message::response_to_client_ack(
                        &package,
                    ))
                    .await
                {
                    warn!(%error, "failed to send publisher-side ACK; closing connection");
                    connection.shutdown().await;
                    break;
                }
            }
        });
        *self.response_driver.lock().await = Some(driver);
        Ok(())
    }
}

impl Drop for TcpProducer {
    fn drop(&mut self) {
        if let Ok(mut driver) = self.response_driver.try_lock() {
            if let Some(driver) = driver.take() {
                driver.abort();
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use super::*;
    use crate::config::{Endpoint, ProducerOptions, TcpConfig};
    use crate::transport::tcp::codec::TcpCodec;
    use crate::transport::tcp::frame::RedirectInfo;
    use crate::transport::tcp::frame::{Command, Header, Package, PackageBody};
    use crate::transport::tcp::ShutdownReason;
    use futures::{SinkExt, StreamExt};
    use tokio::net::TcpListener;
    use tokio::sync::{mpsc, oneshot};
    use tokio_util::codec::Framed;

    struct ResponseHandler(mpsc::UnboundedSender<Message>);

    impl MessageHandler for ResponseHandler {
        async fn handle(&self, message: Message) -> Result<Option<Message>> {
            let _ = self.0.send(message);
            Ok(None)
        }
    }

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

    #[tokio::test]
    async fn producer_with_handler_receives_unsolicited_server_response() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (ack_tx, ack_rx) = oneshot::channel();
        tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());
            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            framed
                .send(Package::new(Header::new(Command::HelloResponse, "hello")))
                .await
                .unwrap();

            let mut header = Header::new(Command::ResponseToClient, "server-push");
            header.code = 0;
            framed
                .send(Package {
                    header,
                    body: PackageBody::Text(
                        serde_json::json!({"topic": "push-topic", "body": "push-body"}).to_string(),
                    ),
                })
                .await
                .unwrap();
            let ack = tokio::time::timeout(Duration::from_secs(2), framed.next())
                .await
                .ok()
                .flatten()
                .and_then(std::result::Result::ok)
                .filter(|package| package.header.cmd == Command::ResponseToClientAck);
            let _ = ack_tx.send(ack);
        });

        let (tx, mut rx) = mpsc::unbounded_channel();
        let client =
            TcpClient::new(TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())).unwrap();
        let producer = client
            .producer_with_handler(ProducerOptions::new("handler-test"), ResponseHandler(tx))
            .await
            .unwrap();
        let received = tokio::time::timeout(Duration::from_secs(2), rx.recv())
            .await
            .unwrap()
            .unwrap()
            .into_event_mesh()
            .unwrap();
        assert_eq!(received.topic.as_deref(), Some("push-topic"));
        assert_eq!(received.content.as_deref(), Some("push-body"));
        assert_eq!(
            ack_rx.await.unwrap().unwrap().header.seq.as_deref(),
            Some("server-push")
        );
        producer.shutdown().await;
    }
}
