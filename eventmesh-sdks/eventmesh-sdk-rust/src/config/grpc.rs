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

//! gRPC client configuration.

use std::time::Duration;

use crate::config::ClientIdentity;

/// Default gRPC port of an EventMesh runtime.
pub const DEFAULT_GRPC_PORT: u16 = 10_205;
/// Default request timeout.
pub const DEFAULT_TIMEOUT: Duration = Duration::from_secs(5);

/// Configuration for the gRPC transport.
#[derive(Debug, Clone)]
pub struct GrpcClientConfig {
    /// Server host (no scheme, no port), e.g. `"127.0.0.1"`.
    pub server_addr: String,
    /// Server gRPC port (default `10205`).
    pub server_port: u16,
    /// Whether to use TLS (`https`).
    pub use_tls: bool,
    /// Default RPC timeout applied when none is given to a call.
    pub timeout: Duration,
    /// Client identity sent with every request.
    pub identity: ClientIdentity,
}

impl Default for GrpcClientConfig {
    fn default() -> Self {
        Self {
            server_addr: "localhost".into(),
            server_port: DEFAULT_GRPC_PORT,
            use_tls: false,
            timeout: DEFAULT_TIMEOUT,
            identity: ClientIdentity::detect(),
        }
    }
}

impl GrpcClientConfig {
    /// Start a fluent builder.
    pub fn builder() -> GrpcClientConfigBuilder {
        GrpcClientConfigBuilder::default()
    }

    /// `host:port` authority string.
    pub fn authority(&self) -> String {
        format!("{}:{}", self.server_addr, self.server_port)
    }
}

/// Fluent builder for [`GrpcClientConfig`].
#[derive(Debug, Clone, Default)]
pub struct GrpcClientConfigBuilder {
    server_addr: Option<String>,
    server_port: Option<u16>,
    use_tls: Option<bool>,
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

impl GrpcClientConfigBuilder {
    pub fn server_addr(mut self, v: impl Into<String>) -> Self {
        self.server_addr = Some(v.into());
        self
    }
    pub fn server_port(mut self, v: u16) -> Self {
        self.server_port = Some(v);
        self
    }
    pub fn use_tls(mut self, v: bool) -> Self {
        self.use_tls = Some(v);
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

    pub fn build(self) -> GrpcClientConfig {
        let GrpcClientConfigBuilder {
            server_addr,
            server_port,
            use_tls,
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

        GrpcClientConfig {
            server_addr: server_addr.unwrap_or_else(|| "localhost".into()),
            server_port: server_port.unwrap_or(DEFAULT_GRPC_PORT),
            use_tls: use_tls.unwrap_or(false),
            timeout: timeout.unwrap_or(DEFAULT_TIMEOUT),
            identity,
        }
    }
}
