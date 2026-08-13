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

//! Low-level gRPC client backed by a connected tonic [`Channel`].

use std::sync::Arc;
use std::time::Duration;

use tonic::codegen::tokio_stream::wrappers::ReceiverStream;
use tonic::transport::{Channel, Endpoint};
use tonic::{Request, Streaming};

use crate::config::GrpcConfig;
use crate::error::{EventMeshError, Result};
use crate::proto_gen::{
    ConsumerServiceClient, HeartbeatServiceClient, PbCloudEvent, PbCloudEventBatch,
    PublisherServiceClient,
};

/// A connection to the EventMesh gRPC server.
///
/// Cheaply cloneable (wraps a multiplexed tonic channel).
#[derive(Clone)]
pub struct ChannelClient {
    channel: Arc<Channel>,
}

impl ChannelClient {
    /// Connect a channel on the current Tokio runtime.
    pub async fn connect(config: &GrpcConfig) -> Result<Self> {
        config.validate()?;
        let channel = Self::endpoint(config)?.connect().await?;
        Ok(Self {
            channel: Arc::new(channel),
        })
    }

    fn endpoint(config: &GrpcConfig) -> Result<Endpoint> {
        let uri = format!("http://{}", config.endpoint().authority());
        let endpoint = Endpoint::from_shared(uri.clone())
            .map_err(|e| EventMeshError::Config(format!("bad endpoint {uri:?}: {e}")))?
            .connect_timeout(Duration::from_secs(10))
            // No channel-wide request timeout: it would wrongly cap the
            // long-lived subscribe_stream and caller-controlled request_reply
            // RPCs. Per-call timeouts are applied by the producer/consumer
            // wrappers instead.
            .keep_alive_while_idle(true)
            .tcp_nodelay(true)
            .tcp_keepalive(Some(Duration::from_secs(100)));
        Ok(endpoint)
    }

    fn channel(&self) -> Channel {
        self.channel.as_ref().clone()
    }

    #[cfg(test)]
    pub(crate) fn shares_channel_with(&self, other: &Self) -> bool {
        Arc::ptr_eq(&self.channel, &other.channel)
    }

    #[cfg(test)]
    pub(crate) fn connect_lazy(config: &GrpcConfig) -> Result<Self> {
        config.validate()?;
        Ok(Self {
            channel: Arc::new(Self::endpoint(config)?.connect_lazy()),
        })
    }

    pub async fn publish(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(PublisherServiceClient::new(self.channel())
            .publish(event)
            .await?
            .into_inner())
    }

    pub async fn batch_publish(&self, events: PbCloudEventBatch) -> Result<PbCloudEvent> {
        Ok(PublisherServiceClient::new(self.channel())
            .batch_publish(events)
            .await?
            .into_inner())
    }

    pub async fn request_reply(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(PublisherServiceClient::new(self.channel())
            .request_reply(event)
            .await?
            .into_inner())
    }

    /// Subscribe via webhook (server POSTs events to the URL). Returns the
    /// broker's ack CloudEvent.
    pub async fn subscribe_webhook(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(ConsumerServiceClient::new(self.channel())
            .subscribe(event)
            .await?
            .into_inner())
    }

    /// Open a bidirectional stream subscription. The first message on the
    /// request stream should be the subscription CloudEvent.
    ///
    /// The stream-open future is wrapped in a timeout to surface a helpful
    /// diagnostic when the caller is running on a **current-thread** tokio
    /// runtime. On a current-thread runtime tonic's background connection
    /// tasks cannot make progress while the caller awaits the server's
    /// response headers, producing an indefinite hang; the timeout converts
    /// that hang into a clear error message instead.
    pub async fn subscribe_stream(
        &self,
        first: PbCloudEvent,
    ) -> Result<(
        tokio::sync::mpsc::Sender<PbCloudEvent>,
        Streaming<PbCloudEvent>,
    )> {
        let (tx, rx) = tokio::sync::mpsc::channel::<PbCloudEvent>(32);
        tx.send(first)
            .await
            .map_err(|e| EventMeshError::ChannelClosed(format!("stream open send: {e}")))?;
        let mut stream_client = ConsumerServiceClient::new(self.channel());

        // The stream-open `.await` resolves once the server sends response
        // headers. On a single-threaded runtime this never completes because
        // tonic's internal connection driver task is starved. Wrap it in a
        // timeout so the caller gets an actionable error instead of a hang.
        const STREAM_OPEN_TIMEOUT: Duration = Duration::from_secs(15);
        let response = tokio::time::timeout(
            STREAM_OPEN_TIMEOUT,
            stream_client.subscribe_stream(Request::new(ReceiverStream::new(rx))),
        )
        .await
        .map_err(|_| EventMeshError::Protocol {
            transport: "grpc",
            message: format!(
                "subscribe_stream did not receive server headers within \
                 {STREAM_OPEN_TIMEOUT:?}. This is almost always caused by a \
                 current-thread tokio runtime (the default for \
                 #[tokio::test]); tonic's background connection tasks \
                 cannot progress. Fix: use #[tokio::test(flavor = \
                 \"multi_thread\")] or \
                 tokio::runtime::Builder::new_multi_thread()"
            ),
        })??;
        Ok((tx, response.into_inner()))
    }

    pub async fn unsubscribe(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(ConsumerServiceClient::new(self.channel())
            .unsubscribe(event)
            .await?
            .into_inner())
    }

    pub async fn heartbeat(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(HeartbeatServiceClient::new(self.channel())
            .heartbeat(Request::new(event))
            .await?
            .into_inner())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::{Endpoint as EventMeshEndpoint, GrpcConfig};

    #[test]
    fn plain_http_endpoint_builds_without_a_tokio_runtime() {
        let config = GrpcConfig::new(EventMeshEndpoint::new("127.0.0.1", 10_205).unwrap());
        let _ = ChannelClient::endpoint(&config).unwrap();
    }

    #[test]
    fn ipv6_endpoint_builds_from_the_new_config() {
        let config = GrpcConfig::new(EventMeshEndpoint::new("::1", 10_205).unwrap());
        let _ = ChannelClient::endpoint(&config).unwrap();
    }
}
