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

//! gRPC producer.

use std::time::Duration;

use tracing::debug;

use crate::config::{GrpcConfig, ProducerOptions};
use crate::error::{Error, Result};
use crate::grpc::GrpcChannel;
use crate::message::{Message, MessageKind, PublishReceipt};
use crate::model::PublishResponse;
use crate::proto_gen::{PbCloudEvent, PbCloudEventBatch};
use crate::transport::grpc::client::ChannelClient;
use crate::transport::grpc::codec;
use crate::transport::validation::validate_grpc_publish;

/// gRPC publishing capability over a shared channel.
pub struct GrpcProducer {
    client: ChannelClient,
    config: GrpcConfig,
    options: ProducerOptions,
}

impl GrpcProducer {
    /// Create a publishing role over `channel`.
    pub fn new(channel: GrpcChannel, options: ProducerOptions) -> Result<Self> {
        options.validate()?;
        let (client, config) = channel.into_parts();
        Ok(Self {
            client,
            config,
            options,
        })
    }

    /// Publish one event and wait for the EventMesh acknowledgement.
    pub async fn publish(&self, message: Message) -> Result<PublishReceipt> {
        let event = self.encode_publish(&message)?;
        let response = self
            .client
            .publish(event, self.config.request_timeout())
            .await?;
        let receipt = publish_receipt(&response, "publish failed")?;
        debug!(kind = ?message.kind(), "published event");
        Ok(receipt)
    }

    /// Publish a non-empty, homogeneous batch through gRPC's batch RPC.
    ///
    /// EventMesh and CloudEvents cannot be mixed in one batch because the
    /// Runtime selects one protocol adaptor for the entire batch.
    pub async fn publish_batch(&self, messages: Vec<Message>) -> Result<PublishReceipt> {
        let kind = messages
            .first()
            .ok_or_else(|| {
                Error::InvalidArgument("batch publish requires at least one message".into())
            })?
            .kind();
        if messages.iter().any(|message| message.kind() != kind) {
            return Err(Error::Unsupported(
                "mixed EventMesh and CloudEvents gRPC batches".into(),
            ));
        }
        let events = messages
            .iter()
            .map(|message| self.encode_publish(message))
            .collect::<Result<Vec<_>>>()?;
        let response = self
            .client
            .batch_publish(PbCloudEventBatch { events }, self.config.request_timeout())
            .await?;
        publish_receipt(&response, "batch publish failed")
    }

    /// Send an event and await a reply in the same message dialect.
    pub async fn request_reply(&self, message: Message) -> Result<Message> {
        self.request_reply_with_timeout(message, self.config.request_timeout())
            .await
    }

    /// Send an event and await its reply with a per-operation gRPC deadline.
    ///
    /// Expiry is reported as [`Error::Timeout`](crate::Error::Timeout).
    pub async fn request_reply_with_timeout(
        &self,
        message: Message,
        timeout: Duration,
    ) -> Result<Message> {
        if timeout.is_zero() {
            return Err(Error::InvalidArgument(
                "request/reply timeout must be greater than zero".into(),
            ));
        }
        let kind = message.kind();
        let event = self.encode_publish(&message)?;
        let response = self.client.request_reply(event, timeout).await?;
        ensure_request_reply_success(codec::to_response(&response), "request/reply failed")?;
        match kind {
            MessageKind::EventMesh => {
                codec::to_event_mesh_message(&response).map(Message::EventMesh)
            }
            #[cfg(feature = "cloud_events")]
            MessageKind::CloudEvent => codec::to_cloudevent(response).map(Message::CloudEvent),
        }
    }

    fn encode_publish(&self, message: &Message) -> Result<PbCloudEvent> {
        match message {
            Message::EventMesh(message) => {
                validate_grpc_publish(message)?;
                codec::from_event_mesh_message(message, &self.config, self.options.group())
            }
            #[cfg(feature = "cloud_events")]
            Message::CloudEvent(event) => {
                codec::from_cloudevent(event, &self.config, self.options.group())
            }
        }
    }
}

fn publish_receipt(event: &PbCloudEvent, fallback: &str) -> Result<PublishReceipt> {
    let response = codec::to_response(event);
    if response.is_success() {
        Ok(PublishReceipt::from_response(response))
    } else {
        Err(Error::Server {
            code: response.code.unwrap_or(-1) as i32,
            message: response.message.unwrap_or_else(|| fallback.into()),
        })
    }
}

/// Successful request/reply RPCs return the business CloudEvent directly and
/// do not attach a `statuscode`. Error replies carry a non-zero status.
fn ensure_request_reply_success(response: PublishResponse, fallback: &str) -> Result<()> {
    match response.code {
        None | Some(0) => Ok(()),
        Some(code) => Err(Error::Server {
            code: code as i32,
            message: response.message.unwrap_or_else(|| fallback.into()),
        }),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::EventMeshMessage;

    #[test]
    fn publish_validation_rejects_missing_topic_or_empty_content() {
        assert!(EventMeshMessage::new("", "body").is_err());
        assert!(validate_grpc_publish(&EventMeshMessage::new("topic", "").unwrap()).is_err());
        assert!(validate_grpc_publish(&EventMeshMessage::new("topic", " ").unwrap()).is_ok());
    }

    #[tokio::test]
    async fn publishing_operations_reject_invalid_payloads_before_rpc() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let config = GrpcConfig::new(
            crate::config::Endpoint::new("127.0.0.1", listener.local_addr().unwrap().port())
                .unwrap(),
        )
        .with_options(
            crate::config::ClientOptions::default().with_request_timeout(Duration::from_millis(10)),
        );
        let producer = GrpcProducer::new(
            GrpcChannel::connect_lazy(config).unwrap(),
            ProducerOptions::new("validation-test"),
        )
        .unwrap();
        for message in [
            EventMeshMessage::new("orders", "").unwrap(),
            EventMeshMessage::builder()
                .topic("orders")
                .content("payload")
                .ttl_millis(0)
                .build()
                .unwrap(),
            EventMeshMessage::builder()
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
                producer.publish_batch(vec![message.clone().into()]).await,
                Err(Error::InvalidMessage(_))
            ));
            assert!(matches!(
                producer
                    .request_reply_with_timeout(message.into(), Duration::from_millis(10))
                    .await,
                Err(Error::InvalidMessage(_))
            ));
        }
        assert!(
            tokio::time::timeout(Duration::from_millis(10), listener.accept())
                .await
                .is_err(),
            "invalid operations must not initiate an RPC"
        );
    }

    #[test]
    fn request_reply_accepts_statusless_business_reply() {
        assert!(
            ensure_request_reply_success(PublishResponse::new(None, None, None), "failed").is_ok()
        );
    }

    #[test]
    fn request_reply_rejects_explicit_error_status() {
        let error = ensure_request_reply_success(
            PublishResponse::new(Some(17), Some("broker rejected".into()), None),
            "failed",
        )
        .unwrap_err();
        assert!(matches!(error, Error::Server { code: 17, .. }));
    }
}
