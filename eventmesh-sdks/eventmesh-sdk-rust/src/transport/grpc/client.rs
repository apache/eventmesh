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

//! Low-level gRPC client: one tonic [`Channel`] shared by the three service
//! stubs.

use std::time::Duration;

use tonic::transport::{Channel, Endpoint};
use tonic::{Request, Streaming};

use crate::config::GrpcClientConfig;
use crate::error::{EventMeshError, Result};
use crate::proto_gen::{
    ConsumerServiceClient, HeartbeatServiceClient, PbCloudEvent, PbCloudEventBatch,
    PublisherServiceClient,
};

/// A connection to the EventMesh gRPC server.
///
/// Cheaply cloneable (wraps a multiplexed tonic channel).
#[derive(Clone)]
pub struct GrpcClient {
    publisher: PublisherServiceClient<Channel>,
    consumer: ConsumerServiceClient<Channel>,
    heartbeat: HeartbeatServiceClient<Channel>,
}

impl GrpcClient {
    /// Build a lazy channel from a config. Does **not** block on connection.
    pub fn new(config: &GrpcClientConfig) -> Result<Self> {
        let scheme = if config.use_tls { "https" } else { "http" };
        let uri = format!("{}://{}", scheme, config.authority());
        let endpoint = Endpoint::from_shared(uri.clone())
            .map_err(|e| EventMeshError::Config(format!("bad endpoint {uri:?}: {e}")))?
            .connect_timeout(Duration::from_secs(10))
            .timeout(config.timeout)
            .keep_alive_while_idle(true)
            .tcp_nodelay(true)
            .tcp_keepalive(Some(Duration::from_secs(100)));

        let channel = endpoint.connect_lazy();
        Ok(Self::from_channel(channel))
    }

    fn from_channel(channel: Channel) -> Self {
        Self {
            publisher: PublisherServiceClient::new(channel.clone()),
            consumer: ConsumerServiceClient::new(channel.clone()),
            heartbeat: HeartbeatServiceClient::new(channel),
        }
    }

    pub async fn publish(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(self.publisher.clone().publish(event).await?.into_inner())
    }

    pub async fn batch_publish(&self, events: PbCloudEventBatch) -> Result<PbCloudEvent> {
        Ok(self
            .publisher
            .clone()
            .batch_publish(events)
            .await?
            .into_inner())
    }

    pub async fn request_reply(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(self
            .publisher
            .clone()
            .request_reply(event)
            .await?
            .into_inner())
    }

    /// Subscribe via webhook (server POSTs events to the URL). Returns the
    /// broker's ack CloudEvent.
    pub async fn subscribe_webhook(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(self.consumer.clone().subscribe(event).await?.into_inner())
    }

    /// Open a bidirectional stream subscription. The first message on the
    /// request stream should be the subscription CloudEvent.
    pub async fn subscribe_stream(
        &self,
        first: PbCloudEvent,
    ) -> Result<(
        tokio::sync::mpsc::Sender<PbCloudEvent>,
        Streaming<PbCloudEvent>,
    )> {
        use tonic::codegen::tokio_stream::wrappers::ReceiverStream;

        let (tx, rx) = tokio::sync::mpsc::channel::<PbCloudEvent>(32);
        tx.send(first)
            .await
            .map_err(|e| EventMeshError::ChannelClosed(format!("stream open send: {e}")))?;
        let mut stream_client = self.consumer.clone();
        let response = stream_client
            .subscribe_stream(Request::new(ReceiverStream::new(rx)))
            .await?;
        Ok((tx, response.into_inner()))
    }

    pub async fn unsubscribe(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(self.consumer.clone().unsubscribe(event).await?.into_inner())
    }

    pub async fn heartbeat(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(self
            .heartbeat
            .clone()
            .heartbeat(Request::new(event))
            .await?
            .into_inner())
    }
}
