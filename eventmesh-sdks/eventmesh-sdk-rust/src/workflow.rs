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

//! Workflow service client.

use crate::config::WorkflowClientConfig;
use crate::discovery::ServiceDiscovery;
use crate::error::{EventMeshError, Result};
use crate::proto_gen::workflow::workflow_client::WorkflowClient as WorkflowGrpcClient;
use crate::service::resolved_grpc_config;
use crate::transport::grpc::GrpcClient;

pub use crate::proto_gen::workflow::{ExecuteRequest, ExecuteResponse};

/// A Workflow client backed by an injected service-discovery implementation.
///
/// An instance is resolved for every [`execute`](Self::execute) call, matching
/// the Java SDK's `getWorkflowClient()` behavior while avoiding global
/// registration state.
pub struct WorkflowClient<D> {
    config: WorkflowClientConfig,
    discovery: D,
}

impl<D: ServiceDiscovery> WorkflowClient<D> {
    /// Create a Workflow client using `discovery` to resolve
    /// [`WorkflowClientConfig::server_name`].
    pub fn new(config: WorkflowClientConfig, discovery: D) -> Self {
        Self { config, discovery }
    }

    /// Resolve the Workflow service and execute a workflow request.
    pub async fn execute(&self, request: ExecuteRequest) -> Result<ExecuteResponse> {
        let instance = self
            .discovery
            .select_one(self.config.server_name.clone())
            .await?
            .ok_or_else(|| EventMeshError::ServiceUnavailable(self.config.server_name.clone()))?;
        let config = resolved_grpc_config(
            &self.config.server_name,
            instance,
            self.config.timeout,
            self.config.use_tls,
            self.config.tls_config.clone(),
        )?;
        let channel = GrpcClient::channel(&config)?;
        let mut client = WorkflowGrpcClient::new(channel);
        let response = tokio::time::timeout(self.config.timeout, client.execute(request))
            .await
            .map_err(|_| EventMeshError::Timeout(self.config.timeout))??;
        Ok(response.into_inner())
    }
}

#[cfg(test)]
mod tests {
    use std::future::Future;

    use super::*;
    use crate::discovery::ServiceInstance;
    use crate::proto_gen::workflow::workflow_server::{Workflow, WorkflowServer};
    use tokio::sync::oneshot;
    use tokio_stream::wrappers::TcpListenerStream;
    use tonic::{Request, Response, Status};

    struct MissingDiscovery;

    impl ServiceDiscovery for MissingDiscovery {
        #[allow(clippy::manual_async_fn)]
        fn select_one(
            &self,
            _: String,
        ) -> impl Future<Output = Result<Option<ServiceInstance>>> + Send {
            async { Ok(None) }
        }
    }

    struct StaticDiscovery(ServiceInstance);

    impl ServiceDiscovery for StaticDiscovery {
        #[allow(clippy::manual_async_fn)]
        fn select_one(
            &self,
            _: String,
        ) -> impl Future<Output = Result<Option<ServiceInstance>>> + Send {
            let instance = self.0.clone();
            async move { Ok(Some(instance)) }
        }
    }

    struct TestWorkflow;

    #[tonic::async_trait]
    impl Workflow for TestWorkflow {
        async fn execute(
            &self,
            request: Request<ExecuteRequest>,
        ) -> std::result::Result<Response<ExecuteResponse>, Status> {
            let request = request.into_inner();
            assert_eq!(request.id, "order-flow");
            assert_eq!(request.task_instance_id, "task-7");
            Ok(Response::new(ExecuteResponse {
                instance_id: "instance-9".into(),
            }))
        }
    }

    #[tokio::test]
    async fn missing_instance_is_reported_before_connecting() {
        let client = WorkflowClient::new(WorkflowClientConfig::default(), MissingDiscovery);
        let err = client.execute(ExecuteRequest::default()).await.unwrap_err();
        assert!(
            matches!(err, EventMeshError::ServiceUnavailable(name) if name == "eventmesh-workflow")
        );
    }

    #[tokio::test]
    async fn execute_resolves_endpoint_and_uses_workflow_wire_contract() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (shutdown_tx, shutdown_rx) = oneshot::channel();
        tokio::spawn(async move {
            tonic::transport::Server::builder()
                .add_service(WorkflowServer::new(TestWorkflow))
                .serve_with_incoming_shutdown(TcpListenerStream::new(listener), async {
                    let _ = shutdown_rx.await;
                })
                .await
                .unwrap();
        });

        let client = WorkflowClient::new(
            WorkflowClientConfig::builder()
                .timeout(std::time::Duration::from_secs(1))
                .build(),
            StaticDiscovery(ServiceInstance::new("127.0.0.1", port)),
        );
        let response = client
            .execute(ExecuteRequest {
                id: "order-flow".into(),
                instance_id: "instance-1".into(),
                task_instance_id: "task-7".into(),
                input: "{}".into(),
            })
            .await
            .unwrap();
        assert_eq!(response.instance_id, "instance-9");
        let _ = shutdown_tx.send(());
    }
}
