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
        // When the `tls` cargo feature is OFF, `use_tls=true` is rejected
        // explicitly instead of silently producing an `https://` URI that
        // tonic cannot actually encrypt.
        #[cfg(not(feature = "tls"))]
        if config.use_tls {
            return Err(EventMeshError::Config(
                "use_tls=true but the 'tls' cargo feature is not enabled. \
                 Add the 'tls' (or 'full') feature to your dependency on \
                 eventmesh to enable TLS support."
                    .into(),
            ));
        }

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
    /// When `tls_config` is `None`, the SNI domain is set to `server_addr` and
    /// the OS-native trust roots are loaded so TLS verification succeeds against
    /// publicly-trusted certificates. When present, the CA certificate,
    /// native roots flag, and mTLS client identity are applied.
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
        } else {
            // No explicit TLS config — load the OS-native trust roots so
            // the system trust store is used for certificate verification,
            // matching the documented default.  Without this, tonic has no
            // trust anchor and every TLS handshake fails.
            tls_config = tls_config.with_native_roots();
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
        let mut stream_client = self.consumer.clone();

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
        .map_err(|_| {
            EventMeshError::Other(format!(
                "subscribe_stream did not receive server headers within \
                 {STREAM_OPEN_TIMEOUT:?}. This is almost always caused by a \
                 current-thread tokio runtime (the default for \
                 #[tokio::test]); tonic's background connection tasks \
                 cannot progress. Fix: use #[tokio::test(flavor = \
                 \"multi_thread\")] or \
                 tokio::runtime::Builder::new_multi_thread()"
            ))
        })??;
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::GrpcClientConfig;

    /// When the `tls` cargo feature is OFF, `use_tls=true` must return an
    /// explicit error instead of silently creating an `https://` endpoint
    /// that tonic cannot encrypt.
    #[cfg(not(feature = "tls"))]
    #[test]
    fn use_tls_without_feature_returns_error() {
        let config = GrpcClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(10205)
            .use_tls(true)
            .build();
        match GrpcClient::new(&config) {
            Err(e) => assert!(
                e.to_string().contains("tls"),
                "error should mention tls: {e}"
            ),
            Ok(_) => panic!("expected error when use_tls=true without tls feature"),
        }
    }

    /// Smoke test: `use_tls=false` (or default) always succeeds regardless
    /// of the `tls` feature — the endpoint is plain HTTP.
    #[tokio::test]
    async fn plain_http_always_builds() {
        let config = GrpcClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(10205)
            .build();
        // connect_lazy does not actually connect, so this should never fail.
        let _ = GrpcClient::new(&config).unwrap();
    }
}
