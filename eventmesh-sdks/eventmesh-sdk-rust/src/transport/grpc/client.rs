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
            // No channel-wide request timeout: it would wrongly cap the
            // long-lived subscribe_stream and caller-controlled request_reply
            // RPCs. Per-call timeouts are applied by the producer/consumer
            // wrappers instead.
            .keep_alive_while_idle(true)
            .tcp_nodelay(true)
            .tcp_keepalive(Some(Duration::from_secs(100)));

        // Apply TLS settings (CA cert, client identity, SNI) when enabled.
        // Gated behind the `tls` cargo feature; without it, `use_tls=true`
        // still produces an `https://` URI but tonic falls back to its
        // default TLS settings.
        #[cfg(feature = "tls")]
        let endpoint = if config.use_tls {
            Self::apply_tls(endpoint, config)?
        } else {
            endpoint
        };

        let channel = endpoint.connect_lazy();
        Ok(Self::from_channel(channel))
    }

    /// Configure the tonic [`Endpoint`] with [`tonic::transport::ClientTlsConfig`]
    /// derived from [`GrpcClientConfig`].
    ///
    /// When `tls_config` is `None`, only the SNI domain is set (to
    /// `server_addr`); tonic uses its built-in trust store. When present, the
    /// CA certificate, native roots flag, and mTLS client identity are applied.
    #[cfg(feature = "tls")]
    fn apply_tls(endpoint: Endpoint, config: &GrpcClientConfig) -> Result<Endpoint> {
        use tonic::transport::{Certificate, ClientTlsConfig, Identity};

        let tls = config.tls_config.as_ref();
        let domain = tls
            .and_then(|t| t.domain.clone())
            .unwrap_or_else(|| config.server_addr.clone());

        let mut tls_config = ClientTlsConfig::new().domain_name(domain);

        if let Some(tls) = tls {
            // CA certificate — inline PEM takes precedence over file path.
            match tls.ca_cert_pem_bytes() {
                Some(Ok(pem)) => {
                    tls_config = tls_config.ca_certificate(Certificate::from_pem(pem));
                }
                Some(Err(e)) => {
                    return Err(EventMeshError::Config(format!(
                        "failed to read CA certificate: {e}"
                    )));
                }
                None => {}
            }

            // OS-native trust roots.
            if tls.use_native_roots {
                tls_config = tls_config.with_native_roots();
            }

            // mTLS client identity.
            if let Some(id) = &tls.client_identity {
                tls_config = tls_config
                    .identity(Identity::from_pem(id.cert_pem.clone(), id.key_pem.clone()));
            }
        }

        endpoint
            .tls_config(tls_config)
            .map_err(|e| EventMeshError::Config(format!("TLS config error: {e}")))
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

    /// Fire-and-forget publish via the `publishOneWay` RPC. The server returns
    /// an empty response (no per-message ack), so callers cannot inspect the
    /// broker's status code — this is intentional fire-and-forget semantics.
    pub async fn publish_one_way(&self, event: PbCloudEvent) -> Result<()> {
        self.publisher.clone().publish_one_way(event).await?;
        Ok(())
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
