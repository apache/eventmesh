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
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, PublishResponse};
use crate::transport::grpc::client::GrpcClient;
use crate::transport::grpc::codec;
use crate::transport::{Publisher, RequestReply};

/// gRPC-based producer.
pub struct GrpcProducer {
    client: GrpcClient,
    config: GrpcConfig,
    options: ProducerOptions,
}

impl GrpcProducer {
    /// Connect (lazily) using the given config.
    pub fn connect(config: GrpcConfig, options: ProducerOptions) -> Result<Self> {
        options.validate()?;
        let client = GrpcClient::new(&config)?;
        Ok(Self {
            client,
            config,
            options,
        })
    }

    /// Publish a batch of native EventMesh messages.
    pub(crate) async fn publish_message_batch(
        &self,
        messages: Vec<crate::message::Message>,
    ) -> Result<PublishResponse> {
        let mut events = Vec::with_capacity(messages.len());
        for message in messages {
            events.push(match message {
                crate::message::Message::EventMesh(message) => {
                    message.validate_for_grpc_publish()?;
                    codec::from_event_mesh_message(&message, &self.config, self.options.group())?
                }
                #[cfg(feature = "cloud_events")]
                crate::message::Message::CloudEvent(_) => {
                    return Err(EventMeshError::Unsupported(
                        "CloudEvents must use the CloudEvents batch path".into(),
                    ));
                }
            });
        }
        let response = codec::to_response(
            &timed(
                self.config.request_timeout(),
                self.client
                    .batch_publish(crate::proto_gen::PbCloudEventBatch { events }),
            )
            .await?,
        );
        ensure_success(response, "batch publish failed")
    }

    #[cfg(feature = "cloud_events")]
    /// Publish a native CloudEvent.
    pub async fn publish_cloud_event(&self, event: cloudevents::Event) -> Result<PublishResponse> {
        use cloudevents::AttributesReader;

        let ce = codec::from_cloudevent(&event, &self.config, self.options.group())?;
        let resp = timed(self.config.request_timeout(), self.client.publish(ce)).await?;
        let response = codec::to_response(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response.message.unwrap_or_else(|| "publish failed".into()),
            });
        }
        debug!("published CloudEvent id={:?}", event.id());
        Ok(response)
    }

    /// Publish several native CloudEvents in a single gRPC batch RPC.
    #[cfg(feature = "cloud_events")]
    pub async fn publish_cloud_event_batch(
        &self,
        events: Vec<cloudevents::Event>,
    ) -> Result<PublishResponse> {
        if events.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "batch publish requires at least one CloudEvent".into(),
            ));
        }
        let mut wire_events = Vec::with_capacity(events.len());
        for event in &events {
            wire_events.push(codec::from_cloudevent(
                event,
                &self.config,
                self.options.group(),
            )?);
        }
        let resp = timed(
            self.config.request_timeout(),
            self.client
                .batch_publish(crate::proto_gen::PbCloudEventBatch {
                    events: wire_events,
                }),
        )
        .await?;
        let response = codec::to_response(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response
                    .message
                    .unwrap_or_else(|| "CloudEvents batch publish failed".into()),
            });
        }
        Ok(response)
    }

    /// Send a native CloudEvent and wait for a native CloudEvent reply.
    #[cfg(feature = "cloud_events")]
    pub async fn request_reply_cloud_event(
        &self,
        event: cloudevents::Event,
        timeout: Duration,
    ) -> Result<cloudevents::Event> {
        let event = codec::from_cloudevent(&event, &self.config, self.options.group())?;
        let response = tokio::time::timeout(timeout, self.client.request_reply(event))
            .await
            .map_err(|_| EventMeshError::Timeout(timeout))??;
        ensure_request_reply_success(
            codec::to_response(&response),
            "CloudEvents request/reply failed",
        )?;
        codec::to_cloudevent(response)
    }
}

fn ensure_success(response: PublishResponse, fallback: &str) -> Result<PublishResponse> {
    if response.is_success() {
        Ok(response)
    } else {
        Err(EventMeshError::Server {
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
        Some(code) => Err(EventMeshError::Server {
            code: code as i32,
            message: response.message.unwrap_or_else(|| fallback.into()),
        }),
    }
}

impl Publisher for GrpcProducer {
    async fn publish(&self, message: EventMeshMessage) -> Result<PublishResponse> {
        message.validate_for_grpc_publish()?;
        let event = codec::from_event_mesh_message(&message, &self.config, self.options.group())?;
        let resp = timed(self.config.request_timeout(), self.client.publish(event)).await?;
        let response = codec::to_response(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response.message.unwrap_or_else(|| "publish failed".into()),
            });
        }
        debug!("published topic={:?}", message.topic);
        Ok(response)
    }

    async fn publish_batch(&self, messages: Vec<EventMeshMessage>) -> Result<PublishResponse> {
        if messages.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "batch publish requires at least one message".into(),
            ));
        }
        for m in &messages {
            m.validate_for_grpc_publish()?;
        }
        let batch = codec::from_event_mesh_messages(&messages, &self.config, self.options.group())?;
        let resp = timed(
            self.config.request_timeout(),
            self.client.batch_publish(batch),
        )
        .await?;
        let response = codec::to_response(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response
                    .message
                    .unwrap_or_else(|| "batch publish failed".into()),
            });
        }
        Ok(response)
    }
}

impl RequestReply for GrpcProducer {
    async fn request_reply(
        &self,
        message: EventMeshMessage,
        timeout: Duration,
    ) -> Result<EventMeshMessage> {
        message.validate_for_grpc_publish()?;
        let event = codec::from_event_mesh_message(&message, &self.config, self.options.group())?;
        let fut = self.client.request_reply(event);
        let resp = tokio::time::timeout(timeout, fut)
            .await
            .map_err(|_| EventMeshError::Timeout(timeout))??;
        ensure_request_reply_success(codec::to_response(&resp), "request/reply failed")?;
        codec::to_event_mesh_message(&resp)
    }
}

/// Apply the config's default request timeout to a short unary RPC. Long-lived
/// RPCs (subscribe stream) and caller-controlled RPCs (request_reply) bypass
/// this and use their own timeouts.
async fn timed<T>(timeout: Duration, f: impl std::future::Future<Output = Result<T>>) -> Result<T> {
    tokio::time::timeout(timeout, f)
        .await
        .map_err(|_| EventMeshError::Timeout(timeout))?
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn publish_validation_rejects_missing_topic_or_empty_content() {
        assert!(EventMeshMessage::new("", "body").is_err());
        assert!(EventMeshMessage::new("topic", "")
            .unwrap()
            .validate_for_grpc_publish()
            .is_err());
        assert!(EventMeshMessage::new("topic", " ")
            .unwrap()
            .validate_for_grpc_publish()
            .is_ok());
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
        assert!(matches!(error, EventMeshError::Server { code: 17, .. }));
    }
}
