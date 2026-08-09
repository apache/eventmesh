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

//! Low-level gRPC client with a lazily initialized tonic [`Channel`].

use std::sync::Arc;
use std::time::Duration;

use tokio::sync::OnceCell;
use tonic::codegen::tokio_stream::wrappers::ReceiverStream;
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
    endpoint: Endpoint,
    channel: Arc<OnceCell<Channel>>,
}

impl GrpcClient {
    /// Validate and store the endpoint configuration without touching Tokio's
    /// reactor. The channel itself is created by the first async operation.
    pub fn new(config: &GrpcClientConfig) -> Result<Self> {
        Ok(Self {
            endpoint: Self::endpoint(config)?,
            channel: Arc::new(OnceCell::new()),
        })
    }

    fn endpoint(config: &GrpcClientConfig) -> Result<Endpoint> {
        let uri = format!("http://{}", config.authority());
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

    async fn channel(&self) -> Channel {
        let endpoint = self.endpoint.clone();
        self.channel
            .get_or_init(|| async move { endpoint.connect_lazy() })
            .await
            .clone()
    }

    pub async fn publish(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(PublisherServiceClient::new(self.channel().await)
            .publish(event)
            .await?
            .into_inner())
    }

    pub async fn batch_publish(&self, events: PbCloudEventBatch) -> Result<PbCloudEvent> {
        Ok(PublisherServiceClient::new(self.channel().await)
            .batch_publish(events)
            .await?
            .into_inner())
    }

    pub async fn request_reply(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(PublisherServiceClient::new(self.channel().await)
            .request_reply(event)
            .await?
            .into_inner())
    }

    /// Subscribe via webhook (server POSTs events to the URL). Returns the
    /// broker's ack CloudEvent.
    pub async fn subscribe_webhook(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(ConsumerServiceClient::new(self.channel().await)
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
        let mut stream_client = ConsumerServiceClient::new(self.channel().await);

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
        Ok(ConsumerServiceClient::new(self.channel().await)
            .unsubscribe(event)
            .await?
            .into_inner())
    }

    pub async fn heartbeat(&self, event: PbCloudEvent) -> Result<PbCloudEvent> {
        Ok(HeartbeatServiceClient::new(self.channel().await)
            .heartbeat(Request::new(event))
            .await?
            .into_inner())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::GrpcClientConfig;

    /// Smoke test: the EventMesh runtime gRPC endpoint is plain HTTP/2.
    #[test]
    fn plain_http_builds_without_a_tokio_runtime() {
        let config = GrpcClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(10205)
            .build();
        // Construction only validates the endpoint and must not spawn tonic's
        // connection driver.
        let _ = GrpcClient::new(&config).unwrap();
    }
}
