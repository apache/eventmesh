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

//! Low-level HTTP client: reqwest wrapper with connection pooling and
//! load balancing across multiple EventMesh nodes.

use std::sync::Arc;
use std::time::Duration;

use reqwest::Client;

use crate::common::loadbalance::{LoadBalanceSelector, ServerNode};
use crate::config::{ConsumerOptions, HttpConfig, ProducerOptions};
use crate::error::{EventMeshError, Result};

/// Default connection pool size (mirrors the Java SDK).
const DEFAULT_POOL_SIZE: usize = 30;
/// Default idle connection eviction (seconds).
const DEFAULT_IDLE_TIMEOUT_SECS: u64 = 10;

/// The resolved transport parameters shared by every HTTP role.
#[derive(Clone)]
pub(crate) struct HttpRole {
    config: Arc<HttpConfig>,
    selector: Arc<LoadBalanceSelector>,
    /// Producer group stamped on publish bodies; empty for consumers.
    producer_group: String,
    /// Consumer group stamped on subscribe/heartbeat bodies; empty for
    /// producers.
    consumer_group: String,
}

impl HttpRole {
    /// Resolve a producer role's transport parameters.
    pub(crate) fn producer(config: HttpConfig, options: &ProducerOptions) -> Result<Self> {
        Self::build(config, options.group().to_string(), None)
    }

    /// Resolve a consumer role's transport parameters.
    pub(crate) fn consumer(config: HttpConfig, options: &ConsumerOptions) -> Result<Self> {
        Self::build(config, String::new(), Some(options.group().to_string()))
    }

    fn build(
        config: HttpConfig,
        producer_group: impl Into<String>,
        consumer_group: Option<String>,
    ) -> Result<Self> {
        let selector = LoadBalanceSelector::new(
            config
                .endpoints()
                .endpoints()
                .iter()
                .map(|endpoint| ServerNode {
                    host: endpoint.authority_host(),
                    port: endpoint.port(),
                    weight: endpoint.weight() as i32,
                })
                .collect(),
            config.load_balance().to_wire(),
        )?;
        Ok(Self {
            config: Arc::new(config),
            selector: Arc::new(selector),
            producer_group: producer_group.into(),
            consumer_group: consumer_group.unwrap_or_default(),
        })
    }

    /// The public configuration.
    pub(crate) fn config(&self) -> &HttpConfig {
        &self.config
    }

    /// The producer group of this role, if any.
    pub(crate) fn producer_group(&self) -> &str {
        &self.producer_group
    }

    /// The consumer group of this role, if any.
    pub(crate) fn consumer_group(&self) -> &str {
        &self.consumer_group
    }

    /// The request timeout for unary operations.
    pub(crate) fn timeout(&self) -> Duration {
        self.config.request_timeout()
    }

    /// Pick the next server node via the configured load-balance strategy.
    pub(crate) fn select_node(&self) -> &ServerNode {
        self.selector.select()
    }
}

/// A pooled, load-balanced HTTP client connected to one or more EventMesh
/// runtime nodes.
///
/// Cheaply cloneable (wraps `Arc<reqwest::Client>`).
#[derive(Clone)]
pub struct EventMeshHttpClient {
    inner: Client,
    role: HttpRole,
}

impl EventMeshHttpClient {
    /// Validate the endpoint set and request client construction without
    /// issuing network I/O.
    pub(crate) fn validate(config: &HttpConfig) -> Result<Self> {
        let client = Self::new(HttpRole::build(config.clone(), String::new(), None)?)?;
        Ok(client)
    }

    /// Build the request client for a resolved role.
    pub(crate) fn new(role: HttpRole) -> Result<Self> {
        let config = &role.config;
        let mut builder = Client::builder()
            .pool_max_idle_per_host(DEFAULT_POOL_SIZE)
            .pool_idle_timeout(Some(Duration::from_secs(DEFAULT_IDLE_TIMEOUT_SECS)))
            .tcp_nodelay(true);

        // EventMesh nodes are explicit SDK endpoints. Default to the Java
        // SDK's direct connection behavior, while allowing applications to
        // opt into reqwest's HTTP_PROXY/HTTPS_PROXY/NO_PROXY handling.
        if !config.proxy_from_env() {
            builder = builder.no_proxy();
        }

        if config.tls_enabled() {
            builder = builder.https_only(true);
        }

        let inner = builder
            .build()
            .map_err(|e| EventMeshError::Config(format!("reqwest client build error: {e}")))?;

        Ok(Self { inner, role })
    }

    /// Pick the next server node via the configured load-balance strategy.
    pub fn select_node(&self) -> &ServerNode {
        self.role.select_node()
    }

    /// Build the base URL for the next request: `http(s)://host:port`.
    pub fn base_url(&self) -> String {
        let node = self.select_node();
        let scheme = if self.role.config.tls_enabled() {
            "https"
        } else {
            "http"
        };
        format!("{}://{}", scheme, node.addr())
    }

    /// Build a full URL for the given path.
    pub fn url_for(&self, path: &str) -> String {
        format!("{}{}", self.base_url(), path)
    }

    /// Send a POST with form-urlencoded body and extra headers. Returns the
    /// response body text.
    pub async fn post_form(
        &self,
        path: &str,
        body: &[(String, String)],
        headers: &[(&str, String)],
        timeout: Duration,
    ) -> Result<String> {
        let url = self.url_for(path);
        tracing::debug!("HTTP POST {} (timeout={:?})", url, timeout);

        let mut req = self.inner.post(&url).form(body).timeout(timeout);
        for (k, v) in headers {
            req = req.header(*k, v);
        }

        let resp = req.send().await.map_err(|e| {
            if e.is_timeout() {
                EventMeshError::Timeout(timeout)
            } else {
                EventMeshError::Http {
                    status: 0,
                    message: format!("request failed: {e}"),
                }
            }
        })?;

        let status = resp.status().as_u16();
        let text = resp.text().await.map_err(|e| {
            if e.is_timeout() {
                EventMeshError::Timeout(timeout)
            } else {
                EventMeshError::Http {
                    status,
                    message: format!("failed to read response body: {e}"),
                }
            }
        })?;

        if !(200..300).contains(&status) {
            return Err(EventMeshError::Http {
                status,
                message: text,
            });
        }

        Ok(text)
    }

    /// The resolved role parameters.
    pub(crate) fn role(&self) -> &HttpRole {
        &self.role
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;
    use std::time::Duration;

    use axum::{extract::State, routing::post, Router};
    use tokio::net::TcpListener;
    use tokio::sync::oneshot;

    use super::*;
    use crate::config::{Endpoint, EndpointSet};

    async fn start_node(
        name: &'static str,
        hits: Arc<tokio::sync::Mutex<Vec<&'static str>>>,
    ) -> (u16, oneshot::Sender<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (shutdown_tx, shutdown_rx) = oneshot::channel();
        let app = Router::new()
            .route(
                "/",
                post(move |State(hits): State<Arc<tokio::sync::Mutex<Vec<&'static str>>>>| async move {
                    hits.lock().await.push(name);
                    r#"{"retCode":0}"#
                }),
            )
            .with_state(hits);
        tokio::spawn(async move {
            axum::serve(listener, app)
                .with_graceful_shutdown(async move {
                    let _ = shutdown_rx.await;
                })
                .await
                .unwrap();
        });
        (port, shutdown_tx)
    }

    #[tokio::test]
    async fn weighted_round_robin_sends_requests_to_each_http_node() {
        let hits = Arc::new(tokio::sync::Mutex::new(Vec::new()));
        let (first_port, first_shutdown) = start_node("first", Arc::clone(&hits)).await;
        let (second_port, second_shutdown) = start_node("second", Arc::clone(&hits)).await;
        let endpoints = EndpointSet::new([
            Endpoint::new("127.0.0.1", first_port)
                .unwrap()
                .with_weight(1)
                .unwrap(),
            Endpoint::new("127.0.0.1", second_port)
                .unwrap()
                .with_weight(1)
                .unwrap(),
        ])
        .unwrap();
        let config = HttpConfig::new(endpoints)
            .with_load_balance(crate::config::LoadBalance::WeightedRoundRobin);
        let client = EventMeshHttpClient::new(
            HttpRole::producer(config, &ProducerOptions::new("g")).unwrap(),
        )
        .unwrap();

        for _ in 0..4 {
            assert_eq!(
                client
                    .post_form("/", &[], &[], Duration::from_secs(2))
                    .await
                    .unwrap(),
                r#"{"retCode":0}"#
            );
        }
        assert_eq!(*hits.lock().await, ["first", "second", "first", "second"]);
        let _ = first_shutdown.send(());
        let _ = second_shutdown.send(());
    }

    #[tokio::test]
    async fn request_timeout_uses_the_transport_independent_error_variant() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = tokio::spawn(async move {
            let _connection = listener.accept().await.unwrap();
            std::future::pending::<()>().await;
        });
        let endpoints = EndpointSet::new([Endpoint::new("127.0.0.1", port).unwrap()]).unwrap();
        let client = EventMeshHttpClient::new(
            HttpRole::producer(HttpConfig::new(endpoints), &ProducerOptions::new("g")).unwrap(),
        )
        .unwrap();
        let timeout = Duration::from_millis(25);

        let error = client.post_form("/", &[], &[], timeout).await.unwrap_err();

        assert!(matches!(error, EventMeshError::Timeout(value) if value == timeout));
        server.abort();
    }
}
