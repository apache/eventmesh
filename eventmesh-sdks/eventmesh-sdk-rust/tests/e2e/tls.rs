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

//! Self-contained TLS e2e with a per-test CA and a real tonic gRPC server.

use std::future::Future;
use std::time::Duration;

use eventmesh::{
    config::{TlsConfig, WorkflowClientConfig},
    discovery::{ServiceDiscovery, ServiceInstance},
    proto_gen::workflow::{
        workflow_server::{Workflow, WorkflowServer},
        ExecuteRequest, ExecuteResponse,
    },
    workflow::WorkflowClient,
    Result,
};
use rcgen::{BasicConstraints, Certificate, CertificateParams, IsCa};
use tokio::sync::oneshot;
use tokio_stream::wrappers::TcpListenerStream;
use tonic::{
    transport::{Identity, Server, ServerTlsConfig},
    Request, Response, Status,
};

struct StaticDiscovery(ServiceInstance);

impl ServiceDiscovery for StaticDiscovery {
    fn select_one(
        &self,
        _: String,
    ) -> impl Future<Output = Result<Option<ServiceInstance>>> + Send {
        let instance = self.0.clone();
        async move { Ok(Some(instance)) }
    }
}

struct TlsWorkflow;

#[tonic::async_trait]
impl Workflow for TlsWorkflow {
    async fn execute(
        &self,
        request: Request<ExecuteRequest>,
    ) -> std::result::Result<Response<ExecuteResponse>, Status> {
        assert_eq!(request.into_inner().id, "tls-e2e-workflow");
        Ok(Response::new(ExecuteResponse {
            instance_id: "tls-instance".into(),
        }))
    }
}

fn certificates() -> (String, String, String) {
    let mut ca_params = CertificateParams::default();
    ca_params.is_ca = IsCa::Ca(BasicConstraints::Unconstrained);
    let ca = Certificate::from_params(ca_params).expect("generate CA certificate");
    let server = Certificate::from_params(CertificateParams::new(vec!["localhost".into()]))
        .expect("generate server certificate");
    (
        ca.serialize_pem().expect("serialize CA certificate"),
        server
            .serialize_pem_with_signer(&ca)
            .expect("sign server certificate"),
        server.serialize_private_key_pem(),
    )
}

#[tokio::test(flavor = "multi_thread")]
async fn workflow_execute_over_self_signed_tls() {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
        .await
        .expect("bind TLS test server");
    let port = listener.local_addr().expect("TLS server address").port();
    let (ca_pem, cert_pem, key_pem) = certificates();
    let (shutdown_tx, shutdown_rx) = oneshot::channel();
    tokio::spawn(async move {
        Server::builder()
            .tls_config(ServerTlsConfig::new().identity(Identity::from_pem(cert_pem, key_pem)))
            .expect("configure TLS server")
            .add_service(WorkflowServer::new(TlsWorkflow))
            .serve_with_incoming_shutdown(TcpListenerStream::new(listener), async move {
                let _ = shutdown_rx.await;
            })
            .await
            .expect("serve TLS workflow");
    });

    let client = WorkflowClient::new(
        WorkflowClientConfig::builder()
            .timeout(Duration::from_secs(3))
            .use_tls(true)
            .tls_config(
                TlsConfig::builder()
                    .domain("localhost")
                    .ca_cert_pem(ca_pem.into_bytes())
                    .build(),
            )
            .build()
            .expect("build TLS workflow config"),
        StaticDiscovery(ServiceInstance::new("127.0.0.1", port)),
    );
    let response = client
        .execute(ExecuteRequest {
            id: "tls-e2e-workflow".into(),
            ..ExecuteRequest::default()
        })
        .await
        .expect("execute over TLS");
    assert_eq!(response.instance_id, "tls-instance");
    let _ = shutdown_tx.send(());
}
