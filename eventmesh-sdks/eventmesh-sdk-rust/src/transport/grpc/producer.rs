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

//! gRPC producer.

use std::time::Duration;

use tracing::debug;

use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, PublishResponse};
use crate::transport::grpc::client::GrpcClient;
use crate::transport::grpc::codec::CloudEventCodec;
use crate::transport::Publisher;

/// gRPC-based producer.
pub struct GrpcProducer {
    client: GrpcClient,
    config: crate::config::GrpcClientConfig,
}

impl GrpcProducer {
    /// Connect (lazily) using the given config.
    pub fn connect(config: crate::config::GrpcClientConfig) -> Result<Self> {
        let client = GrpcClient::new(&config)?;
        Ok(Self { client, config })
    }

    /// Publish fire-and-forget via `publishOneWay` (no reply expected from the
    /// broker beyond the RPC ack). Useful when you don't care about per-message
    /// broker ack codes.
    pub async fn publish_one_way(&self, message: EventMeshMessage) -> Result<()> {
        let event = CloudEventCodec::from_event_mesh_message(&message, &self.config)?;
        let _ = self.client.publish(event).await?;
        Ok(())
    }

    #[cfg(feature = "cloud_events")]
    /// Publish a native CloudEvent.
    pub async fn publish_cloud_event(&self, event: cloudevents::Event) -> Result<PublishResponse> {
        let ce =
            crate::transport::grpc::codec::CloudEventMessage::from_event(&event, &self.config)?;
        let resp = self.client.publish(ce).await?;
        Ok(CloudEventCodec::to_response(&resp))
    }
}

impl Publisher for GrpcProducer {
    async fn publish(&self, message: EventMeshMessage) -> Result<PublishResponse> {
        validate_publish(&message)?;
        let event = CloudEventCodec::from_event_mesh_message(&message, &self.config)?;
        let resp = self.client.publish(event).await?;
        let response = CloudEventCodec::to_response(&resp);
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
            validate_publish(m)?;
        }
        let batch = CloudEventCodec::from_event_mesh_messages(&messages, &self.config)?;
        let resp = self.client.batch_publish(batch).await?;
        let response = CloudEventCodec::to_response(&resp);
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

    async fn request_reply(
        &self,
        message: EventMeshMessage,
        timeout: Duration,
    ) -> Result<EventMeshMessage> {
        validate_publish(&message)?;
        let event = CloudEventCodec::from_event_mesh_message(&message, &self.config)?;
        let fut = self.client.request_reply(event);
        let resp = tokio::time::timeout(timeout, fut)
            .await
            .map_err(|_| EventMeshError::Timeout(timeout))??;
        Ok(CloudEventCodec::to_event_mesh_message(&resp))
    }
}

fn validate_publish(message: &EventMeshMessage) -> Result<()> {
    if message
        .topic
        .as_deref()
        .map(|t| t.trim().is_empty())
        .unwrap_or(true)
    {
        return Err(EventMeshError::InvalidMessage("topic is required".into()));
    }
    if message
        .content
        .as_deref()
        .map(|c| c.is_empty())
        .unwrap_or(true)
    {
        return Err(EventMeshError::InvalidMessage("content is required".into()));
    }
    Ok(())
}
