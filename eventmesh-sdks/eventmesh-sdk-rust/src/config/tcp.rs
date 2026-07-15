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

//! TCP client configuration.

use std::time::Duration;

use crate::config::ClientIdentity;

/// Default TCP port of an EventMesh runtime.
pub const DEFAULT_TCP_PORT: u16 = 10_000;

/// Default request timeout (mirrors the Java SDK `DEFAULT_TIME_OUT_MILLS`).
pub const DEFAULT_TIMEOUT: Duration = Duration::from_secs(20);

/// Heartbeat interval (mirrors the Java SDK `HEARTBEAT = 30_000 ms`).
pub const DEFAULT_HEARTBEAT: Duration = Duration::from_secs(30);

/// Default initial backoff before the first reconnect attempt.
pub const DEFAULT_RECONNECT_INITIAL_BACKOFF: Duration = Duration::from_secs(1);

/// Default maximum backoff between reconnect attempts.
pub const DEFAULT_RECONNECT_MAX_BACKOFF: Duration = Duration::from_secs(30);

/// Reconnect policy for the TCP transport.
///
/// When enabled (the default), the background I/O task automatically
/// re-establishes the TCP connection + HELLO handshake after an I/O error or
/// server-side close. For consumers, subscriptions are replayed automatically
/// after a successful reconnect.
///
/// This mirrors the Java SDK's heartbeat-driven reconnect (`TcpClient.heartbeat`
/// checks `channel.isActive()` every 30 s and calls `reconnect()` when false),
/// but with exponential backoff and a configurable retry cap instead of a flat
/// 30 s cadence.
#[derive(Debug, Clone)]
pub struct ReconnectConfig {
    /// Whether automatic reconnect is enabled (default `true`).
    pub enabled: bool,
    /// Maximum reconnect attempts before giving up. `usize::MAX` = infinite
    /// (default, matching the Java SDK).
    pub max_retries: usize,
    /// Initial backoff duration before the first retry (default `1 s`).
    pub initial_backoff: Duration,
    /// Cap for the exponential backoff (default `30 s`).
    pub max_backoff: Duration,
}

impl Default for ReconnectConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            max_retries: usize::MAX,
            initial_backoff: DEFAULT_RECONNECT_INITIAL_BACKOFF,
            max_backoff: DEFAULT_RECONNECT_MAX_BACKOFF,
        }
    }
}

impl ReconnectConfig {
    /// Start a fluent builder.
    pub fn builder() -> ReconnectConfigBuilder {
        ReconnectConfigBuilder::default()
    }
}

/// Fluent builder for [`ReconnectConfig`].
#[derive(Debug, Clone, Default)]
pub struct ReconnectConfigBuilder {
    enabled: Option<bool>,
    max_retries: Option<usize>,
    initial_backoff: Option<Duration>,
    max_backoff: Option<Duration>,
}

impl ReconnectConfigBuilder {
    pub fn enabled(mut self, v: bool) -> Self {
        self.enabled = Some(v);
        self
    }
    pub fn max_retries(mut self, v: usize) -> Self {
        self.max_retries = Some(v);
        self
    }
    pub fn initial_backoff(mut self, v: Duration) -> Self {
        self.initial_backoff = Some(v);
        self
    }
    pub fn max_backoff(mut self, v: Duration) -> Self {
        self.max_backoff = Some(v);
        self
    }
    pub fn build(self) -> ReconnectConfig {
        let ReconnectConfigBuilder {
            enabled,
            max_retries,
            initial_backoff,
            max_backoff,
        } = self;
        let mut cfg = ReconnectConfig::default();
        if let Some(v) = enabled {
            cfg.enabled = v;
        }
        if let Some(v) = max_retries {
            cfg.max_retries = v;
        }
        if let Some(v) = initial_backoff {
            cfg.initial_backoff = v;
        }
        if let Some(v) = max_backoff {
            cfg.max_backoff = v;
        }
        cfg
    }
}

/// Configuration for the TCP transport.
#[derive(Debug, Clone)]
pub struct TcpClientConfig {
    /// Server host (no scheme, no port), e.g. `"127.0.0.1"`.
    pub server_addr: String,
    /// Server TCP port (default `10000`).
    pub server_port: u16,
    /// Default request timeout applied when none is given to a call.
    pub timeout: Duration,
    /// Heartbeat interval.
    pub heartbeat_interval: Duration,
    /// Automatic reconnect policy (default: enabled, infinite retries, 1–30 s
    /// exponential backoff).
    pub reconnect: ReconnectConfig,
    /// Client identity sent with every request.
    pub identity: ClientIdentity,
}

impl Default for TcpClientConfig {
    fn default() -> Self {
        Self {
            server_addr: "localhost".into(),
            server_port: DEFAULT_TCP_PORT,
            timeout: DEFAULT_TIMEOUT,
            heartbeat_interval: DEFAULT_HEARTBEAT,
            reconnect: ReconnectConfig::default(),
            identity: ClientIdentity::detect(),
        }
    }
}

impl TcpClientConfig {
    /// Start a fluent builder.
    pub fn builder() -> TcpClientConfigBuilder {
        TcpClientConfigBuilder::default()
    }

    /// `host:port` string.
    pub fn authority(&self) -> String {
        format!("{}:{}", self.server_addr, self.server_port)
    }
}

/// Fluent builder for [`TcpClientConfig`].
#[derive(Clone, Default)]
pub struct TcpClientConfigBuilder {
    server_addr: Option<String>,
    server_port: Option<u16>,
    timeout: Option<Duration>,
    heartbeat_interval: Option<Duration>,
    reconnect: Option<ReconnectConfig>,
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

impl TcpClientConfigBuilder {
    pub fn server_addr(mut self, v: impl Into<String>) -> Self {
        self.server_addr = Some(v.into());
        self
    }
    pub fn server_port(mut self, v: u16) -> Self {
        self.server_port = Some(v);
        self
    }
    pub fn timeout(mut self, v: Duration) -> Self {
        self.timeout = Some(v);
        self
    }
    pub fn heartbeat_interval(mut self, v: Duration) -> Self {
        self.heartbeat_interval = Some(v);
        self
    }
    pub fn reconnect(mut self, v: ReconnectConfig) -> Self {
        self.reconnect = Some(v);
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

    pub fn build(self) -> TcpClientConfig {
        let TcpClientConfigBuilder {
            server_addr,
            server_port,
            timeout,
            heartbeat_interval,
            reconnect,
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

        TcpClientConfig {
            server_addr: server_addr.unwrap_or_else(|| "localhost".into()),
            server_port: server_port.unwrap_or(DEFAULT_TCP_PORT),
            timeout: timeout.unwrap_or(DEFAULT_TIMEOUT),
            heartbeat_interval: heartbeat_interval.unwrap_or(DEFAULT_HEARTBEAT),
            reconnect: reconnect.unwrap_or_default(),
            identity,
        }
    }
}

impl std::fmt::Debug for TcpClientConfigBuilder {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("TcpClientConfigBuilder")
            .field("server_addr", &self.server_addr)
            .field("server_port", &self.server_port)
            .field("timeout", &self.timeout)
            .field("heartbeat_interval", &self.heartbeat_interval)
            .field("reconnect", &self.reconnect)
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
