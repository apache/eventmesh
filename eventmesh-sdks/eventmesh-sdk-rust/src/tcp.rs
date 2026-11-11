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
use crate::MessageHandler;

pub use crate::transport::tcp::{TcpConsumer, TcpProducer};

/// A configured EventMesh TCP client.
#[derive(Clone)]
pub struct TcpClient {
    config: TcpConfig,
}

impl TcpClient {
    /// Validate a TCP client configuration. Role factories open connections.
    pub fn new(config: TcpConfig) -> Result<Self> {
        config.validate()?;
        Ok(Self { config })
    }

    /// Connect a producer role to the TCP endpoint.
    pub async fn producer(&self, options: ProducerOptions) -> Result<TcpProducer> {
        options.validate()?;
        TcpProducer::connect(self.config.clone(), &options).await
    }

    /// Connect a producer that handles unmatched server response pushes.
    ///
    /// Normal request/reply responses remain owned by their originating futures.
    /// This corresponds to Java TCP's `registerPubBusiHandler`.
    pub async fn producer_with_handler<H: MessageHandler>(
        &self,
        options: ProducerOptions,
        handler: H,
    ) -> Result<TcpProducer> {
        let producer = self.producer(options).await?;
        producer.start_response_handler(handler).await?;
        Ok(producer)
    }

    /// Connect a long-lived TCP consumer role.
    pub async fn consumer<H: MessageHandler>(
        &self,
        options: ConsumerOptions,
        handler: H,
    ) -> Result<TcpConsumer> {
        options.validate()?;
        TcpConsumer::connect(self.config.clone(), &options, handler).await
    }
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use super::*;
    use crate::config::{Endpoint, ProducerOptions, TcpConfig};
    use crate::transport::tcp::codec::TcpCodec;
    use crate::transport::tcp::delivery::decode_message;
    use crate::transport::tcp::frame::{Command, Header, Package, PackageBody};
    use crate::{Error, Message};
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

    #[tokio::test]
    async fn publishing_operations_reject_invalid_payloads_before_sending() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());
            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            framed
                .send(Package::new(Header::new(
                    Command::HelloResponse,
                    hello.header.seq.unwrap(),
                )))
                .await
                .unwrap();
            while let Some(frame) = framed.next().await {
                assert_eq!(
                    frame.unwrap().header.cmd,
                    Command::ClientGoodbyeRequest,
                    "invalid publishes, broadcasts, and requests must not reach the socket"
                );
            }
        });
        let producer = TcpClient::new(TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap()))
            .unwrap()
            .producer(ProducerOptions::new("validation-test"))
            .await
            .unwrap();
        for message in [
            crate::EventMeshMessage::new("orders", " \t\n").unwrap(),
            crate::EventMeshMessage::builder()
                .topic("orders")
                .content("payload")
                .ttl_millis(0)
                .build()
                .unwrap(),
            crate::EventMeshMessage::builder()
                .topic("orders")
                .content("payload")
                .ttl_millis(i64::from(i32::MAX) + 1)
                .build()
                .unwrap(),
        ] {
            assert!(matches!(
                producer.publish(message.clone().into()).await,
                Err(Error::InvalidMessage(_))
            ));
            assert!(matches!(
                producer.broadcast(message.clone().into()).await,
                Err(Error::InvalidMessage(_))
            ));
            assert!(matches!(
                producer.request_reply(message.into()).await,
                Err(Error::InvalidMessage(_))
            ));
        }
        producer.shutdown().await;
        server.await.unwrap();
    }

    async fn broadcast_before_shutdown(message: Message) -> Package {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());
            let hello = framed.next().await.unwrap().unwrap();
            assert_eq!(hello.header.cmd, Command::HelloRequest);
            framed
                .send(Package::new(Header::new(Command::HelloResponse, "hello")))
                .await
                .unwrap();
            // No broadcast ACK: the caller must only wait for its local write.
            framed.next().await
        });
        let client = TcpClient::new(
            TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
                .with_reconnect(crate::config::ReconnectPolicy::default().with_enabled(false)),
        )
        .unwrap();
        let producer = client
            .producer(ProducerOptions::new("broadcast-test"))
            .await
            .unwrap();

        tokio::time::timeout(Duration::from_secs(2), producer.broadcast(message))
            .await
            .expect("broadcast must not wait for a server ACK")
            .unwrap();
        producer.shutdown().await;

        let package = tokio::time::timeout(Duration::from_secs(2), server)
            .await
            .unwrap()
            .unwrap()
            .expect("broadcast must reach the socket before shutdown")
            .unwrap();
        assert_eq!(package.header.cmd, Command::BroadcastMessageToServer);
        package
    }

    #[tokio::test]
    async fn broadcast_is_written_before_shutdown() {
        let message = crate::EventMeshMessage::new("orders", "created").unwrap();
        let package = broadcast_before_shutdown(Message::EventMesh(message.clone())).await;
        let received = decode_message(&package).unwrap().into_event_mesh().unwrap();
        assert_eq!(received.topic(), message.topic());
        assert_eq!(received.content(), message.content());
    }

    #[cfg(feature = "cloud_events")]
    #[tokio::test]
    async fn cloud_event_broadcast_is_written_before_shutdown() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("broadcast")
            .source("urn:test")
            .ty("created")
            .subject("orders")
            .data(
                "application/cloudevents+json",
                serde_json::json!({"id": 42}),
            )
            .build()
            .unwrap();
        let package = broadcast_before_shutdown(Message::CloudEvent(event.clone())).await;
        let received = decode_message(&package).unwrap();
        assert!(matches!(received, Message::CloudEvent(received) if received == event));
    }

    #[tokio::test]
    async fn broadcast_reports_frame_encoding_failure() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = tokio::spawn(async move {
            let (stream, _) = listener.accept().await.unwrap();
            let mut framed = Framed::new(stream, TcpCodec::new());
            let _hello = framed.next().await.unwrap().unwrap();
            framed
                .send(Package::new(Header::new(Command::HelloResponse, "hello")))
                .await
                .unwrap();
            framed.next().await
        });
        let client = TcpClient::new(
            TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
                .with_reconnect(crate::config::ReconnectPolicy::default().with_enabled(false)),
        )
        .unwrap();
        let producer = client
            .producer(ProducerOptions::new("broadcast-test"))
            .await
            .unwrap();
        // Construction accepts this content, but the TCP codec rejects frames
        // larger than 4 MiB. That driver-side error must reach the caller.
        let message = crate::EventMeshMessage::new("orders", "x".repeat(4 * 1024 * 1024)).unwrap();
        let result = producer.broadcast(Message::EventMesh(message)).await;
        producer.shutdown().await;

        assert!(matches!(result, Err(Error::InvalidArgument(message))
            if message.contains("exceeds limit")));
        assert!(tokio::time::timeout(Duration::from_secs(2), server)
            .await
            .unwrap()
            .unwrap()
            .is_none());
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
            header.set_property("protocoltype", "eventmeshmessage");
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
        assert_eq!(received.topic(), "push-topic");
        assert_eq!(received.content(), "push-body");
        assert_eq!(
            ack_rx.await.unwrap().unwrap().header.seq.as_deref(),
            Some("server-push")
        );
        producer.shutdown().await;
    }
    #[cfg(feature = "cloud_events")]
    #[tokio::test]
    async fn request_reply_returns_the_reply_dialect_and_preserves_binary_data() {
        use crate::transport::tcp::message;
        use cloudevents::{EventBuilder, EventBuilderV10};

        for cloud_request in [false, true] {
            let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
            let port = listener.local_addr().unwrap().port();
            let native = crate::EventMeshMessage::new("orders", "native payload").unwrap();
            let event = EventBuilderV10::new()
                .id("event")
                .source("urn:test")
                .ty("test")
                .subject("orders")
                .data("application/cloudevents+json", vec![0, 255, 128])
                .build()
                .unwrap();
            let request = if cloud_request {
                Message::CloudEvent(event.clone())
            } else {
                native.clone().into()
            };
            let expected_reply = if cloud_request {
                Message::EventMesh(native.clone())
            } else {
                Message::CloudEvent(event.clone())
            };
            let server = tokio::spawn(async move {
                let (stream, _) = listener.accept().await.unwrap();
                let mut framed = Framed::new(stream, TcpCodec::new());
                let hello = framed.next().await.unwrap().unwrap();
                framed
                    .send(Package::new(Header::new(
                        Command::HelloResponse,
                        hello.header.seq.unwrap(),
                    )))
                    .await
                    .unwrap();
                let request = framed.next().await.unwrap().unwrap();
                assert_eq!(request.header.cmd, Command::RequestToServer);
                let mut response = if cloud_request {
                    message::build_message_package(&native, Command::ResponseToClient).unwrap()
                } else {
                    message::build_cloud_event_package(&event, Command::ResponseToClient).unwrap()
                };
                response.header.seq = request.header.seq;
                framed.send(response).await.unwrap();
                let ack = framed.next().await.unwrap().unwrap();
                assert_eq!(ack.header.cmd, Command::ResponseToClientAck);
                while framed.next().await.is_some() {}
            });
            let producer =
                TcpClient::new(TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap()))
                    .unwrap()
                    .producer(ProducerOptions::new("reply-dialect"))
                    .await
                    .unwrap();
            let reply = producer.request_reply(request).await.unwrap();
            match (reply, expected_reply) {
                (Message::EventMesh(actual), Message::EventMesh(expected)) => {
                    assert_eq!(actual.topic(), expected.topic());
                    assert_eq!(actual.content(), expected.content());
                }
                (Message::CloudEvent(actual), Message::CloudEvent(expected)) => {
                    assert_eq!(actual, expected)
                }
                (actual, expected) => {
                    panic!("reply dialect mismatch: {actual:?}, expected {expected:?}")
                }
            }
            producer.shutdown().await;
            server.await.unwrap();
        }
    }
}
