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

//! Workflow client configuration.

use std::time::Duration;

use crate::config::TlsConfig;

/// Default logical Workflow service name, matching the Java SDK.
pub const DEFAULT_WORKFLOW_SERVER_NAME: &str = "eventmesh-workflow";
/// Default Workflow RPC timeout.
pub const DEFAULT_WORKFLOW_TIMEOUT: Duration = Duration::from_secs(5);

/// Configuration for [`crate::workflow::WorkflowClient`].
#[derive(Debug, Clone)]
pub struct WorkflowClientConfig {
    /// Logical name resolved through [`crate::discovery::ServiceDiscovery`].
    pub server_name: String,
    /// Timeout for the `Execute` RPC.
    pub timeout: Duration,
    /// Use TLS for the resolved gRPC endpoint.
    pub use_tls: bool,
    /// Optional TLS details for the resolved gRPC endpoint.
    pub tls_config: Option<TlsConfig>,
}

impl Default for WorkflowClientConfig {
    fn default() -> Self {
        Self {
            server_name: DEFAULT_WORKFLOW_SERVER_NAME.into(),
            timeout: DEFAULT_WORKFLOW_TIMEOUT,
            use_tls: false,
            tls_config: None,
        }
    }
}

impl WorkflowClientConfig {
    /// Start a fluent builder.
    pub fn builder() -> WorkflowClientConfigBuilder {
        WorkflowClientConfigBuilder::default()
    }
}

/// Fluent builder for [`WorkflowClientConfig`].
#[derive(Debug, Clone, Default)]
pub struct WorkflowClientConfigBuilder {
    server_name: Option<String>,
    timeout: Option<Duration>,
    use_tls: Option<bool>,
    tls_config: Option<TlsConfig>,
}

impl WorkflowClientConfigBuilder {
    pub fn server_name(mut self, v: impl Into<String>) -> Self {
        self.server_name = Some(v.into());
        self
    }
    pub fn timeout(mut self, v: Duration) -> Self {
        self.timeout = Some(v);
        self
    }
    pub fn use_tls(mut self, v: bool) -> Self {
        self.use_tls = Some(v);
        self
    }
    pub fn tls_config(mut self, v: TlsConfig) -> Self {
        self.tls_config = Some(v);
        self
    }
    pub fn build(self) -> WorkflowClientConfig {
        WorkflowClientConfig {
            server_name: self
                .server_name
                .filter(|name| !name.trim().is_empty())
                .unwrap_or_else(|| DEFAULT_WORKFLOW_SERVER_NAME.into()),
            timeout: self.timeout.unwrap_or(DEFAULT_WORKFLOW_TIMEOUT),
            use_tls: self.use_tls.unwrap_or(false),
            tls_config: self.tls_config,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_match_java_sdk() {
        let config = WorkflowClientConfig::builder().build();
        assert_eq!(config.server_name, DEFAULT_WORKFLOW_SERVER_NAME);
        assert_eq!(config.timeout, DEFAULT_WORKFLOW_TIMEOUT);
        assert!(!config.use_tls);
    }
}
