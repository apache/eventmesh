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

//! HTTP client configuration.

use std::time::Duration;

use crate::common::loadbalance::LoadBalance;
use crate::config::ClientIdentity;

/// Default HTTP port of an EventMesh runtime.
pub const DEFAULT_HTTP_PORT: u16 = 10_105;
/// Default request timeout (mirrors the Java SDK's `Constants.DEFAULT_HTTP_TIME_OUT`).
pub const DEFAULT_TIMEOUT: Duration = Duration::from_secs(15);
/// Default connection pool size (mirrors the Java SDK).
pub const DEFAULT_POOL_SIZE: usize = 30;
/// Default idle connection eviction (seconds).
pub const DEFAULT_IDLE_TIMEOUT_SECS: u64 = 10;

/// Configuration for the HTTP transport.
///
/// `servers` is a list of `host:port` (or `host:port:weight`) strings,
/// semicolon- or comma-separated in the builder, mirroring the Java SDK's
/// `liteEventMeshAddr` field.
#[derive(Debug, Clone)]
pub struct HttpClientConfig {
    /// Parsed server nodes for load balancing.
    pub nodes: Vec<crate::common::loadbalance::ServerNode>,
    /// Load-balance strategy.
    pub load_balance: LoadBalance,
    /// Use TLS (`https`).
    pub use_tls: bool,
    /// Read HTTP proxy settings from process environment variables.
    pub proxy_from_env: bool,
    /// Connection pool max size.
    pub pool_size: usize,
    /// Idle connection eviction timeout.
    pub pool_idle_timeout: Duration,
    /// Default request timeout.
    pub timeout: Duration,
    /// Client identity sent with every request.
    pub identity: ClientIdentity,
}

impl Default for HttpClientConfig {
    fn default() -> Self {
        Self {
            nodes: vec![
                crate::common::loadbalance::ServerNode::parse("localhost:10105")
                    .expect("default node"),
            ],
            load_balance: LoadBalance::Random,
            use_tls: false,
            proxy_from_env: false,
            pool_size: DEFAULT_POOL_SIZE,
            pool_idle_timeout: Duration::from_secs(DEFAULT_IDLE_TIMEOUT_SECS),
            timeout: DEFAULT_TIMEOUT,
            identity: ClientIdentity::detect(),
        }
    }
}

impl HttpClientConfig {
    /// Start a fluent builder.
    pub fn builder() -> HttpClientConfigBuilder {
        HttpClientConfigBuilder::default()
    }
}

/// Fluent builder for [`HttpClientConfig`].
#[derive(Clone, Default)]
pub struct HttpClientConfigBuilder {
    servers: Option<String>,
    load_balance: Option<LoadBalance>,
    use_tls: Option<bool>,
    proxy_from_env: Option<bool>,
    pool_size: Option<usize>,
    pool_idle_timeout: Option<Duration>,
    timeout: Option<Duration>,
    identity: Option<ClientIdentity>,
    // identity convenience setters:
    env: Option<String>,
    idc: Option<String>,
    sys: Option<String>,
    producer_group: Option<String>,
    consumer_group: Option<String>,
    username: Option<String>,
    password: Option<String>,
    token: Option<String>,
}

impl HttpClientConfigBuilder {
    /// Set server addresses. Accepts `;` or `,` separated `host:port[:weight]` strings.
    pub fn servers(mut self, v: impl Into<String>) -> Self {
        self.servers = Some(v.into());
        self
    }

    pub fn load_balance(mut self, v: LoadBalance) -> Self {
        self.load_balance = Some(v);
        self
    }

    pub fn use_tls(mut self, v: bool) -> Self {
        self.use_tls = Some(v);
        self
    }

    pub fn proxy_from_env(mut self, v: bool) -> Self {
        self.proxy_from_env = Some(v);
        self
    }

    pub fn pool_size(mut self, v: usize) -> Self {
        self.pool_size = Some(v);
        self
    }

    pub fn pool_idle_timeout(mut self, v: Duration) -> Self {
        self.pool_idle_timeout = Some(v);
        self
    }

    pub fn timeout(mut self, v: Duration) -> Self {
        self.timeout = Some(v);
        self
    }

    pub fn identity(mut self, v: ClientIdentity) -> Self {
        self.identity = Some(v);
        self
    }

    pub fn env(mut self, v: impl Into<String>) -> Self {
        self.env = Some(v.into());
        self
    }

    pub fn idc(mut self, v: impl Into<String>) -> Self {
        self.idc = Some(v.into());
        self
    }

    pub fn sys(mut self, v: impl Into<String>) -> Self {
        self.sys = Some(v.into());
        self
    }

    pub fn producer_group(mut self, v: impl Into<String>) -> Self {
        self.producer_group = Some(v.into());
        self
    }

    pub fn consumer_group(mut self, v: impl Into<String>) -> Self {
        self.consumer_group = Some(v.into());
        self
    }

    pub fn username(mut self, v: impl Into<String>) -> Self {
        self.username = Some(v.into());
        self
    }

    pub fn password(mut self, v: impl Into<String>) -> Self {
        self.password = Some(v.into());
        self
    }

    pub fn token(mut self, v: impl Into<String>) -> Self {
        self.token = Some(v.into());
        self
    }

    /// Build the config, parsing the server address list.
    pub fn build(self) -> crate::error::Result<HttpClientConfig> {
        let HttpClientConfigBuilder {
            servers,
            load_balance,
            use_tls,
            proxy_from_env,
            pool_size,
            pool_idle_timeout,
            timeout,
            identity,
            env,
            idc,
            sys,
            producer_group,
            consumer_group,
            username,
            password,
            token,
        } = self;

        let mut identity = identity.unwrap_or_default();
        if let Some(v) = env {
            identity.env = v;
        }
        if let Some(v) = idc {
            identity.idc = v;
        }
        if let Some(v) = sys {
            identity.sys = v;
        }
        if let Some(v) = producer_group {
            identity.producer_group = v;
        }
        if let Some(v) = consumer_group {
            identity.consumer_group = v;
        }
        if let Some(v) = username {
            identity.username = v;
        }
        if let Some(v) = password {
            identity.password = v;
        }
        if let Some(v) = token {
            identity.token = Some(v);
        }

        // Parse server addresses — accept `;` or `,` separators.
        let raw = servers.unwrap_or_else(|| "localhost:10105".into());
        let nodes: Vec<crate::common::loadbalance::ServerNode> = raw
            .split([';', ','])
            .map(|s| s.trim())
            .filter(|s| !s.is_empty())
            .map(crate::common::loadbalance::ServerNode::parse)
            .collect::<crate::error::Result<Vec<_>>>()?;

        if nodes.is_empty() {
            return Err(crate::error::EventMeshError::Config(
                "at least one server address is required".into(),
            ));
        }

        Ok(HttpClientConfig {
            nodes,
            load_balance: load_balance.unwrap_or_default(),
            use_tls: use_tls.unwrap_or(false),
            proxy_from_env: proxy_from_env.unwrap_or(false),
            pool_size: pool_size.unwrap_or(DEFAULT_POOL_SIZE),
            pool_idle_timeout: pool_idle_timeout
                .unwrap_or(Duration::from_secs(DEFAULT_IDLE_TIMEOUT_SECS)),
            timeout: timeout.unwrap_or(DEFAULT_TIMEOUT),
            identity,
        })
    }
}

impl std::fmt::Debug for HttpClientConfigBuilder {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("HttpClientConfigBuilder")
            .field("servers", &self.servers)
            .field("load_balance", &self.load_balance)
            .field("use_tls", &self.use_tls)
            .field("proxy_from_env", &self.proxy_from_env)
            .field("pool_size", &self.pool_size)
            .field("pool_idle_timeout", &self.pool_idle_timeout)
            .field("timeout", &self.timeout)
            .field("identity", &self.identity)
            .field("env", &self.env)
            .field("idc", &self.idc)
            .field("sys", &self.sys)
            .field("producer_group", &self.producer_group)
            .field("consumer_group", &self.consumer_group)
            .field("username", &self.username)
            .field("password", &self.password.as_ref().map(|_| "***"))
            .field("token", &self.token.as_ref().map(|_| "***"))
            .finish()
    }
}
