/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
use std::time::Duration;

use tokio::sync::mpsc;
use tokio::sync::mpsc::Sender;
use tonic::codegen::tokio_stream::wrappers::ReceiverStream;
use tonic::transport::{Channel, Endpoint, Uri};
use tonic::{Request, Streaming};

use crate::common::ProtocolKey;
use crate::config::EventMeshGrpcClientConfig;
use crate::error::EventMeshError;
use crate::error::EventMeshError::EventMeshRemote;
use crate::grpc::pb::cloud_events::cloud_event::cloud_event_attribute_value::Attr;
use crate::grpc::pb::cloud_events::consumer_service_client::ConsumerServiceClient;
use crate::grpc::pb::cloud_events::heartbeat_service_client::HeartbeatServiceClient;
use crate::grpc::pb::cloud_events::publisher_service_client::PublisherServiceClient;
use crate::proto_cloud_event::{PbCloudEvent, PbCloudEventBatch};

pub struct SubscribeStreamKeeper {
    pub(crate) sender: Sender<PbCloudEvent>,
}

impl SubscribeStreamKeeper {
    pub(crate) fn new(sender: Sender<PbCloudEvent>) -> Self {
        Self { sender }
    }
}

#[derive(Clone)]
pub struct GrpcClient {
    publisher_inner: PublisherServiceClient<Channel>,
    consumer_inner: ConsumerServiceClient<Channel>,
    heartbeat_inner: HeartbeatServiceClient<Channel>,
}

impl GrpcClient {
    pub fn new(grpc_config: &EventMeshGrpcClientConfig) -> crate::Result<Self> {
        #[cfg(feature = "tls")]
        let scheme = { "https" };

        #[cfg(not(feature = "tls"))]
        let scheme = {
            if let Some(tls) = grpc_config.use_tls {
                if tls {
                    "https"
                } else {
                    "http"
                }
            } else {
                "http"
            }
        };
        let url = format!("{}:{}", grpc_config.server_addr, grpc_config.server_port);
        let endpoint_uri = Uri::builder()
            .scheme(scheme)
            .authority(url)
            .path_and_query("/")
            .build()?;
        let endpoint = Endpoint::from(endpoint_uri)
            .connect_timeout(Duration::from_millis(10000))
            .keep_alive_while_idle(true)
            .tcp_nodelay(true)
            .tcp_keepalive(Some(Duration::from_secs(100)));

        let channel = endpoint.connect_lazy();
        let publisher_service_client = PublisherServiceClient::new(channel.clone());
        let consumer_service_client = ConsumerServiceClient::new(channel.clone());
        let heartbeat_inner_client = HeartbeatServiceClient::new(channel);
        Ok(Self {
            publisher_inner: publisher_service_client,
            consumer_inner: consumer_service_client,
            heartbeat_inner: heartbeat_inner_client,
        })
    }

    pub(crate) async fn publish_inner(
        &mut self,
        cloud_event: PbCloudEvent,
    ) -> crate::Result<PbCloudEvent> {
        let result = self
            .publisher_inner
            .publish(cloud_event)
            .await
            .map_err(|e| EventMeshError::GRpcStatus(e))?
            .into_inner();
        Ok(result)
    }

    pub(crate) async fn batch_publish_inner(
        &mut self,
        cloud_events: PbCloudEventBatch,
    ) -> crate::Result<PbCloudEvent> {
        let result = self
            .publisher_inner
            .batch_publish(cloud_events)
            .await
            .map_err(|e| EventMeshError::GRpcStatus(e))?
            .into_inner();
        Ok(result)
    }

    pub(crate) async fn request_reply_inner(
        &mut self,
        cloud_event: PbCloudEvent,
        time_out: u64,
    ) -> crate::Result<PbCloudEvent> {
        let future_task = self.publisher_inner.request_reply(cloud_event);
        let result = tokio::time::timeout(Duration::from_millis(time_out), future_task).await;
        match result {
            Ok(Ok(value)) => {
                let event = value.into_inner();
                if let Some(code) = event.attributes.get(ProtocolKey::GRPC_RESPONSE_CODE) {
                    if let Some(code_num) = &code.attr {
                        match code_num {
                            Attr::CeString(cd) if cd != "0" => {
                                if let Some(msg) =
                                    event.attributes.get(ProtocolKey::GRPC_RESPONSE_MESSAGE)
                                {
                                    if let Some(msg_inner) = &msg.attr {
                                        match msg_inner {
                                            Attr::CeString(msg) => {
                                                return Err(EventMeshRemote(msg.to_string()).into());
                                            }
                                            _ => {}
                                        }
                                    }
                                }
                                return Err(
                                    EventMeshRemote("EventMesh remote error".to_string()).into()
                                );
                            }
                            _ => {}
                        }
                    }
                }
                Ok(event)
            }
            Ok(Err(err)) => Err(EventMeshError::GRpcStatus(err).into()),
            Err(_) => Err(EventMeshError::EventMeshLocal("Request reply error".to_string()).into()),
        }
    }

    pub(crate) async fn subscribe_webhook_inner(
        &mut self,
        cloud_event: PbCloudEvent,
    ) -> crate::Result<PbCloudEvent> {
        let result = self
            .consumer_inner
            .subscribe(cloud_event)
            .await
            .map_err(|e| EventMeshError::GRpcStatus(e))?
            .into_inner();
        Ok(result)
    }

    pub(crate) async fn subscribe_bi_inner(
        &mut self,
        cloud_event: PbCloudEvent,
    ) -> crate::Result<(SubscribeStreamKeeper, Streaming<PbCloudEvent>)> {
        let (sender, receiver) = mpsc::channel::<PbCloudEvent>(16);
        sender.send(cloud_event).await?;
        let streaming = self
            .consumer_inner
            .subscribe_stream(Request::new(ReceiverStream::new(receiver)))
            .await
            .map_err(|e| EventMeshError::GRpcStatus(e))?
            .into_inner();
        Ok((SubscribeStreamKeeper::new(sender), streaming))
    }

    pub(crate) async fn unsubscribe_inner(
        &mut self,
        cloud_event: PbCloudEvent,
    ) -> crate::Result<PbCloudEvent> {
        let result = self
            .consumer_inner
            .unsubscribe(cloud_event)
            .await
            .map_err(|e| EventMeshError::GRpcStatus(e))?
            .into_inner();
        Ok(result)
    }

    pub(crate) async fn heartbeat_inner(
        &mut self,
        cloud_event: PbCloudEvent,
    ) -> crate::Result<PbCloudEvent> {
        let result = self
            .heartbeat_inner
            .heartbeat(Request::new(cloud_event))
            .await
            .map_err(|e| EventMeshError::GRpcStatus(e))?
            .into_inner();
        Ok(result)
    }
}
