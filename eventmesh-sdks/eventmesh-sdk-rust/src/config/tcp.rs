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

//! TCP client configuration.

use std::time::Duration;

use crate::config::ClientIdentity;

/// Default TCP port of an EventMesh runtime.
pub const DEFAULT_TCP_PORT: u16 = 10_000;

/// Default request timeout (mirrors the Java SDK `DEFAULT_TIME_OUT_MILLS`).
pub const DEFAULT_TIMEOUT: Duration = Duration::from_secs(20);

/// Heartbeat interval (mirrors the Java SDK `HEARTBEAT = 30_000 ms`).
pub const DEFAULT_HEARTBEAT: Duration = Duration::from_secs(30);

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
#[derive(Debug, Clone, Default)]
pub struct TcpClientConfigBuilder {
    server_addr: Option<String>,
    server_port: Option<u16>,
    timeout: Option<Duration>,
    heartbeat_interval: Option<Duration>,
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
            identity,
        }
    }
}
