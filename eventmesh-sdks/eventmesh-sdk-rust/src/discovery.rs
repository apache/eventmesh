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

//! Pluggable service discovery for Catalog and Workflow clients.

use std::collections::HashMap;
use std::future::Future;

use crate::Result;

/// A service endpoint returned by a [`ServiceDiscovery`] implementation.
///
/// Discovery implementations should return `None` when no healthy instance is
/// available. Clients defensively reject an instance whose [`healthy`](Self::healthy)
/// flag is `false`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ServiceInstance {
    /// Host or IP address of the selected service instance.
    pub host: String,
    /// gRPC port of the selected service instance.
    pub port: u16,
    /// Whether the registry considers the instance healthy.
    pub healthy: bool,
    /// Registry-specific instance metadata.
    pub metadata: HashMap<String, String>,
}

impl ServiceInstance {
    /// Construct a healthy service instance with no metadata.
    pub fn new(host: impl Into<String>, port: u16) -> Self {
        Self {
            host: host.into(),
            port,
            healthy: true,
            metadata: HashMap::new(),
        }
    }
}

/// Selects one service instance by logical service name.
///
/// This intentionally mirrors the Java SDK's `Selector#selectOne`, while
/// using constructor injection instead of a process-global selector factory.
/// Implement this trait for Nacos, Consul, Kubernetes, or an application's
/// existing service-registry client.
pub trait ServiceDiscovery: Send + Sync {
    /// Resolve one instance for `service_name`, or `None` when none is
    /// available. The returned future must not borrow `service_name`.
    fn select_one(
        &self,
        service_name: String,
    ) -> impl Future<Output = Result<Option<ServiceInstance>>> + Send;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_instance_defaults_to_healthy_without_metadata() {
        let instance = ServiceInstance::new("catalog.internal", 9000);
        assert!(instance.healthy);
        assert!(instance.metadata.is_empty());
        assert_eq!(instance.host, "catalog.internal");
        assert_eq!(instance.port, 9000);
    }
}
