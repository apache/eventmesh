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

//! Internal helpers shared by discovered gRPC service clients.

use std::time::Duration;

use crate::config::{GrpcClientConfig, TlsConfig};
use crate::discovery::ServiceInstance;
use crate::error::{EventMeshError, Result};

pub(crate) fn resolved_grpc_config(
    service_name: &str,
    instance: ServiceInstance,
    timeout: Duration,
    use_tls: bool,
    tls_config: Option<TlsConfig>,
) -> Result<GrpcClientConfig> {
    if !instance.healthy {
        return Err(EventMeshError::ServiceUnavailable(service_name.into()));
    }
    if instance.host.trim().is_empty() || instance.port == 0 {
        return Err(EventMeshError::ServiceDiscovery(format!(
            "service {service_name:?} returned invalid endpoint {:?}:{}",
            instance.host, instance.port
        )));
    }

    let mut builder = GrpcClientConfig::builder()
        .server_addr(instance.host)
        .server_port(instance.port)
        .timeout(timeout)
        .use_tls(use_tls);
    if let Some(tls_config) = tls_config {
        builder = builder.tls_config(tls_config);
    }
    Ok(builder.build())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_unhealthy_or_invalid_instances() {
        let mut unhealthy = ServiceInstance::new("workflow", 9000);
        unhealthy.healthy = false;
        assert!(matches!(
            resolved_grpc_config("workflow", unhealthy, Duration::from_secs(1), false, None),
            Err(EventMeshError::ServiceUnavailable(_))
        ));

        assert!(matches!(
            resolved_grpc_config(
                "workflow",
                ServiceInstance::new("", 0),
                Duration::from_secs(1),
                false,
                None,
            ),
            Err(EventMeshError::ServiceDiscovery(_))
        ));
    }
}
