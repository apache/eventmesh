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

//! Catalog-driven subscriptions for the gRPC stream consumer.

use tokio::sync::Mutex;

use crate::config::CatalogClientConfig;
use crate::discovery::ServiceDiscovery;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, SubscriptionItem};
use crate::proto_gen::catalog::catalog_client::CatalogClient as CatalogGrpcClient;
use crate::proto_gen::catalog::{Operation, QueryOperationsRequest};
use crate::service::resolved_grpc_config;
use crate::transport::grpc::{GrpcClient, GrpcStreamConsumer};
use crate::MessageListener;

#[derive(Default)]
struct CatalogState {
    initialized: bool,
    subscriptions: Vec<SubscriptionItem>,
}

/// Synchronizes Catalog `subscribe` operations to an existing gRPC stream
/// consumer.
///
/// This mirrors the Java SDK's `EventMeshCatalogClient`: initialization queries
/// the application's operations and subscribes only to operations whose type
/// is exactly `"subscribe"`. The client owns only the subscriptions it created;
/// [`destroy`](Self::destroy) never removes caller-managed subscriptions.
pub struct CatalogClient<D> {
    config: CatalogClientConfig,
    discovery: D,
    state: Mutex<CatalogState>,
    lifecycle: Mutex<()>,
}

impl<D: ServiceDiscovery> CatalogClient<D> {
    /// Create a Catalog client using `discovery` to resolve
    /// [`CatalogClientConfig::server_name`].
    pub fn new(config: CatalogClientConfig, discovery: D) -> Self {
        Self {
            config,
            discovery,
            state: Mutex::new(CatalogState::default()),
            lifecycle: Mutex::new(()),
        }
    }

    /// Query Catalog and subscribe the provided stream consumer to the
    /// returned `subscribe` channels. A successful call is idempotent until
    /// [`destroy`](Self::destroy) succeeds.
    pub async fn init<L>(&self, consumer: &GrpcStreamConsumer<L>) -> Result<()>
    where
        L: MessageListener<Message = EventMeshMessage>,
    {
        let _lifecycle = self.lifecycle.lock().await;
        if self.state.lock().await.initialized {
            return Ok(());
        }

        let items = self.query_subscription_items().await?;
        let mut subscriptions = Vec::new();
        for item in items {
            if !consumer.has_stream_subscription(&item.topic).await {
                subscriptions.push(item);
            }
        }
        if !subscriptions.is_empty() {
            consumer.subscribe(subscriptions.clone()).await?;
        }

        let mut state = self.state.lock().await;
        state.subscriptions = subscriptions;
        state.initialized = true;
        Ok(())
    }

    /// Unsubscribe only the channels previously created by [`init`](Self::init).
    ///
    /// If the consumer rejects the unsubscribe, local state is retained so a
    /// subsequent `destroy` can retry it.
    pub async fn destroy<L>(&self, consumer: &GrpcStreamConsumer<L>) -> Result<()>
    where
        L: MessageListener<Message = EventMeshMessage>,
    {
        let _lifecycle = self.lifecycle.lock().await;
        let subscriptions = {
            let state = self.state.lock().await;
            if !state.initialized {
                return Ok(());
            }
            state.subscriptions.clone()
        };

        if !subscriptions.is_empty() {
            consumer.unsubscribe_stream(subscriptions).await?;
        }

        *self.state.lock().await = CatalogState::default();
        Ok(())
    }

    async fn query_subscription_items(&self) -> Result<Vec<SubscriptionItem>> {
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
        let mut client = CatalogGrpcClient::new(channel);
        let response = tokio::time::timeout(
            self.config.timeout,
            client.query_operations(QueryOperationsRequest {
                service_name: self.config.app_server_name.clone(),
                operation_id: String::new(),
            }),
        )
        .await
        .map_err(|_| EventMeshError::Timeout(self.config.timeout))??;
        Ok(subscription_items(
            &response.into_inner().operations,
            &self.config,
        ))
    }
}

fn subscription_items(
    operations: &[Operation],
    config: &CatalogClientConfig,
) -> Vec<SubscriptionItem> {
    let mut items = Vec::new();
    for operation in operations {
        if operation.r#type == "subscribe" {
            let item = SubscriptionItem::new(
                operation.channel_name.clone(),
                config.subscription_mode,
                config.subscription_type,
            );
            if !items.contains(&item) {
                items.push(item);
            }
        }
    }
    items
}

#[cfg(test)]
mod tests {
    use std::future::Future;

    use super::*;
    use crate::discovery::ServiceInstance;
    use crate::proto_gen::catalog::catalog_server::{Catalog, CatalogServer};
    use crate::proto_gen::catalog::{QueryOperationsResponse, RegistryRequest, RegistryResponse};
    use tokio::sync::oneshot;
    use tokio_stream::wrappers::TcpListenerStream;
    use tonic::{Request, Response, Status};

    struct StaticDiscovery(ServiceInstance);

    impl ServiceDiscovery for StaticDiscovery {
        #[allow(clippy::manual_async_fn)]
        fn select_one(
            &self,
            service_name: String,
        ) -> impl Future<Output = Result<Option<ServiceInstance>>> + Send {
            assert_eq!(service_name, "eventmesh-catalog");
            let instance = self.0.clone();
            async move { Ok(Some(instance)) }
        }
    }

    struct TestCatalog;

    #[tonic::async_trait]
    impl Catalog for TestCatalog {
        async fn registry(
            &self,
            _: Request<RegistryRequest>,
        ) -> std::result::Result<Response<RegistryResponse>, Status> {
            Ok(Response::new(RegistryResponse {}))
        }

        async fn query_operations(
            &self,
            request: Request<QueryOperationsRequest>,
        ) -> std::result::Result<Response<QueryOperationsResponse>, Status> {
            let request = request.into_inner();
            assert_eq!(request.service_name, "payment");
            assert!(request.operation_id.is_empty());
            Ok(Response::new(QueryOperationsResponse {
                operations: vec![
                    Operation {
                        channel_name: "payments.received".into(),
                        schema: String::new(),
                        r#type: "subscribe".into(),
                    },
                    Operation {
                        channel_name: "payments.done".into(),
                        schema: String::new(),
                        r#type: "publish".into(),
                    },
                ],
            }))
        }
    }

    fn config() -> CatalogClientConfig {
        CatalogClientConfig::builder()
            .app_server_name("payment")
            .build()
            .unwrap()
    }

    #[test]
    fn maps_only_subscribe_operations_and_deduplicates_topics() {
        let operations = vec![
            Operation {
                channel_name: "orders.created".into(),
                schema: "schema".into(),
                r#type: "subscribe".into(),
            },
            Operation {
                channel_name: "orders.created".into(),
                schema: "schema".into(),
                r#type: "subscribe".into(),
            },
            Operation {
                channel_name: "orders.completed".into(),
                schema: "schema".into(),
                r#type: "publish".into(),
            },
        ];

        let items = subscription_items(&operations, &config());
        assert_eq!(items.len(), 1);
        assert_eq!(items[0].topic, "orders.created");
    }

    #[test]
    fn applies_configured_subscription_mode_and_type() {
        let config = CatalogClientConfig::builder()
            .app_server_name("payment")
            .subscription_mode(crate::model::SubscriptionMode::BROADCASTING)
            .subscription_type(crate::model::SubscriptionType::SYNC)
            .build()
            .unwrap();
        let items = subscription_items(
            &[Operation {
                channel_name: "events".into(),
                schema: String::new(),
                r#type: "subscribe".into(),
            }],
            &config,
        );
        assert_eq!(items[0].mode, crate::model::SubscriptionMode::BROADCASTING);
        assert_eq!(items[0].r#type, crate::model::SubscriptionType::SYNC);
    }

    #[tokio::test]
    async fn query_uses_discovered_endpoint_and_catalog_wire_contract() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (shutdown_tx, shutdown_rx) = oneshot::channel();
        tokio::spawn(async move {
            tonic::transport::Server::builder()
                .add_service(CatalogServer::new(TestCatalog))
                .serve_with_incoming_shutdown(TcpListenerStream::new(listener), async {
                    let _ = shutdown_rx.await;
                })
                .await
                .unwrap();
        });

        let client = CatalogClient::new(
            CatalogClientConfig::builder()
                .app_server_name("payment")
                .timeout(std::time::Duration::from_secs(1))
                .build()
                .unwrap(),
            StaticDiscovery(ServiceInstance::new("127.0.0.1", port)),
        );
        let items = client.query_subscription_items().await.unwrap();
        assert_eq!(items.len(), 1);
        assert_eq!(items[0].topic, "payments.received");
        let _ = shutdown_tx.send(());
    }
}
