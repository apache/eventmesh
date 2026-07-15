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

//! Catalog client configuration.

use std::time::Duration;

use crate::config::TlsConfig;
use crate::error::{EventMeshError, Result};
use crate::model::{SubscriptionMode, SubscriptionType};

/// Default logical Catalog service name, matching the Java SDK.
pub const DEFAULT_CATALOG_SERVER_NAME: &str = "eventmesh-catalog";
/// Default Catalog RPC timeout.
pub const DEFAULT_CATALOG_TIMEOUT: Duration = Duration::from_secs(5);

/// Configuration for [`crate::catalog::CatalogClient`].
#[derive(Debug, Clone)]
pub struct CatalogClientConfig {
    /// Logical name resolved through [`crate::discovery::ServiceDiscovery`].
    pub server_name: String,
    /// Name of the application whose operations are queried from Catalog.
    pub app_server_name: String,
    /// Delivery mode applied to Catalog-provided subscribe operations.
    pub subscription_mode: SubscriptionMode,
    /// Delivery type applied to Catalog-provided subscribe operations.
    pub subscription_type: SubscriptionType,
    /// Timeout for short Catalog RPCs.
    pub timeout: Duration,
    /// Use TLS for the resolved gRPC endpoint.
    pub use_tls: bool,
    /// Optional TLS details for the resolved gRPC endpoint.
    pub tls_config: Option<TlsConfig>,
}

impl CatalogClientConfig {
    /// Start a fluent builder.
    pub fn builder() -> CatalogClientConfigBuilder {
        CatalogClientConfigBuilder::default()
    }
}

/// Fluent builder for [`CatalogClientConfig`].
#[derive(Debug, Clone, Default)]
pub struct CatalogClientConfigBuilder {
    server_name: Option<String>,
    app_server_name: Option<String>,
    subscription_mode: Option<SubscriptionMode>,
    subscription_type: Option<SubscriptionType>,
    timeout: Option<Duration>,
    use_tls: Option<bool>,
    tls_config: Option<TlsConfig>,
}

impl CatalogClientConfigBuilder {
    pub fn server_name(mut self, v: impl Into<String>) -> Self {
        self.server_name = Some(v.into());
        self
    }
    pub fn app_server_name(mut self, v: impl Into<String>) -> Self {
        self.app_server_name = Some(v.into());
        self
    }
    pub fn subscription_mode(mut self, v: SubscriptionMode) -> Self {
        self.subscription_mode = Some(v);
        self
    }
    pub fn subscription_type(mut self, v: SubscriptionType) -> Self {
        self.subscription_type = Some(v);
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

    /// Build a Catalog configuration. `app_server_name` is required by
    /// Catalog's `QueryOperations` protocol.
    pub fn build(self) -> Result<CatalogClientConfig> {
        let app_server_name = self
            .app_server_name
            .filter(|name| !name.trim().is_empty())
            .ok_or_else(|| EventMeshError::Config("catalog app_server_name is required".into()))?;
        Ok(CatalogClientConfig {
            server_name: self
                .server_name
                .filter(|name| !name.trim().is_empty())
                .unwrap_or_else(|| DEFAULT_CATALOG_SERVER_NAME.into()),
            app_server_name,
            subscription_mode: self
                .subscription_mode
                .unwrap_or(SubscriptionMode::CLUSTERING),
            subscription_type: self.subscription_type.unwrap_or(SubscriptionType::ASYNC),
            timeout: self.timeout.unwrap_or(DEFAULT_CATALOG_TIMEOUT),
            use_tls: self.use_tls.unwrap_or(false),
            tls_config: self.tls_config,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_match_java_sdk() {
        let config = CatalogClientConfig::builder()
            .app_server_name("payment")
            .build()
            .unwrap();
        assert_eq!(config.server_name, DEFAULT_CATALOG_SERVER_NAME);
        assert_eq!(config.subscription_mode, SubscriptionMode::CLUSTERING);
        assert_eq!(config.subscription_type, SubscriptionType::ASYNC);
        assert_eq!(config.timeout, DEFAULT_CATALOG_TIMEOUT);
        assert!(!config.use_tls);
    }

    #[test]
    fn app_server_name_is_required() {
        assert!(CatalogClientConfig::builder().build().is_err());
    }
}
