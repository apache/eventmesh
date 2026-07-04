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

//! Low-level HTTP client: reqwest wrapper with connection pooling and
//! load balancing across multiple EventMesh nodes.

use std::sync::Arc;
use std::time::Duration;

use reqwest::Client;

use crate::common::loadbalance::{LoadBalanceSelector, ServerNode};
use crate::config::HttpClientConfig;
use crate::error::{EventMeshError, Result};

/// A pooled, load-balanced HTTP client connected to one or more EventMesh
/// runtime nodes.
///
/// Cheaply cloneable (wraps `Arc<reqwest::Client>`).
#[derive(Clone)]
pub struct EventMeshHttpClient {
    inner: Client,
    selector: Arc<LoadBalanceSelector>,
    config: Arc<HttpClientConfig>,
}

impl EventMeshHttpClient {
    /// Build from a config.
    pub fn new(config: HttpClientConfig) -> Result<Self> {
        let selector = LoadBalanceSelector::new(config.nodes.clone(), config.load_balance)?;

        let mut builder = Client::builder()
            .pool_max_idle_per_host(config.pool_size)
            .pool_idle_timeout(Some(config.pool_idle_timeout))
            .tcp_nodelay(true);

        if config.use_tls {
            builder = builder.https_only(true);
        }

        let inner = builder
            .build()
            .map_err(|e| EventMeshError::Config(format!("reqwest client build error: {e}")))?;

        Ok(Self {
            inner,
            selector: Arc::new(selector),
            config: Arc::new(config),
        })
    }

    /// Pick the next server node via the configured load-balance strategy.
    pub fn select_node(&self) -> &ServerNode {
        self.selector.select()
    }

    /// Build the base URL for the next request: `http(s)://host:port`.
    pub fn base_url(&self) -> String {
        let node = self.select_node();
        let scheme = if self.config.use_tls { "https" } else { "http" };
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

        let resp = req.send().await.map_err(|e| EventMeshError::Http {
            status: 0,
            message: format!("request failed: {e}"),
        })?;

        let status = resp.status().as_u16();
        let text = resp.text().await.map_err(|e| EventMeshError::Http {
            status,
            message: format!("failed to read response body: {e}"),
        })?;

        if !(200..300).contains(&status) {
            return Err(EventMeshError::Http {
                status,
                message: text,
            });
        }

        Ok(text)
    }

    /// Reference to the config.
    pub fn config(&self) -> &HttpClientConfig {
        &self.config
    }
}
