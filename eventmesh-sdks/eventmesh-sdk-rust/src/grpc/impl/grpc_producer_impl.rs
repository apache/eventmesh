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
use std::any::Any;
use std::fmt::Debug;
use std::marker::PhantomData;

use tonic::transport::Uri;

use crate::common::constants::{DataContentType, DEFAULT_EVENTMESH_MESSAGE_TTL};
use crate::common::grpc_eventmesh_message_utils::EventMeshCloudEventUtils;
use crate::config::EventMeshGrpcClientConfig;
use crate::error::EventMeshError;
use crate::grpc::grpc_producer::EventMeshGrpcProducer;
use crate::model::message::EventMeshMessage;
use crate::model::response::EventMeshResponse;
use crate::model::EventMeshProtocolType;
use crate::net::GrpcClient;
use crate::proto_cloud_event::{
    EventMeshCloudEventBuilder, PbCloudEvent, PbCloudEventBatch, PbData,
};

/// gRPC EventMesh message producer.
pub struct GrpcEventMeshProducer<M> {
    /// gRPC client.
    inner: GrpcClient,

    /// gRPC configuration.
    grpc_config: EventMeshGrpcClientConfig,

    _mark: PhantomData<M>,
}

impl<M> GrpcEventMeshProducer<M>
where
    M: Any,
{
    pub fn new(grpc_config: EventMeshGrpcClientConfig) -> Self {
        let client = GrpcClient::new(&grpc_config).unwrap();
        Self {
            inner: client,
            grpc_config,
            _mark: PhantomData::<M>,
        }
    }

    #[allow(dead_code)]
    fn build_event_mesh_cloud_event(&mut self, message: EventMeshMessage) -> PbCloudEvent {
        let mut event = EventMeshCloudEventBuilder::default()
            .with_env(self.grpc_config.env.clone())
            .with_idc(self.grpc_config.idc.clone())
            .with_ip(crate::common::local_ip::get_local_ip_v4())
            .with_pid(std::process::id().to_string())
            .with_sys(self.grpc_config.sys.clone())
            .with_user_name(self.grpc_config.user_name.clone())
            .with_password(self.grpc_config.password.clone())
            .with_language("Rust")
            .with_protocol_type(EventMeshProtocolType::CloudEvents.protocol_type_name())
            .with_ttl(DEFAULT_EVENTMESH_MESSAGE_TTL.to_string())
            .with_subject(message.biz_seq_no.clone().unwrap())
            .with_producergroup(
                self.grpc_config
                    .producer_group
                    .clone()
                    .map_or_else(|| String::from("Default_Producer_Group"), |val| val)
                    .clone(),
            )
            .with_uniqueid(message.unique_id.unwrap())
            .with_data_content_type(DataContentType::TEXT_PLAIN)
            .build();
        event.id = message.biz_seq_no.clone().unwrap();
        event.source = Uri::builder()
            .path_and_query("/")
            .build()
            .unwrap()
            .to_string();
        event.spec_version = "1.0".to_string();
        event.r#type = "Rust".to_string();
        event.data = Some(PbData::TextData(message.content.unwrap().into()));
        event
    }

    fn build_event_mesh_cloud_event_batch(
        &mut self,
        messages: Vec<M>,
    ) -> Option<PbCloudEventBatch> {
        if messages.is_empty() {
            return None;
        }

        let events = messages
            .into_iter()
            .map(|msg| {
                EventMeshCloudEventUtils::build_event_mesh_cloud_event(msg, &self.grpc_config)
                    .unwrap()
            })
            .collect();

        let mut cloud_event_batch = PbCloudEventBatch::default();
        cloud_event_batch.events = events;

        Some(cloud_event_batch)
    }
}

/// gRPC EventMesh message producer implementation.
#[allow(unused_variables)]
impl<M> EventMeshGrpcProducer<M> for GrpcEventMeshProducer<M>
where
    M: Any + Debug + From<PbCloudEvent>,
{
    /// Publish a message.
    async fn publish(&mut self, message: M) -> crate::Result<EventMeshResponse> {
        let event =
            EventMeshCloudEventUtils::build_event_mesh_cloud_event(message, &self.grpc_config);
        if event.is_none() {
            return Err(EventMeshError::EventMeshLocal(
                "Create Event Mesh cloud event Error".to_string(),
            )
            .into());
        }
        let result = self.inner.publish_inner(event.unwrap()).await?;
        Ok(EventMeshCloudEventUtils::get_response(&result))
    }

    /// Publish a batch of messages.
    async fn publish_batch(&mut self, messages: Vec<M>) -> crate::Result<EventMeshResponse> {
        let events = self.build_event_mesh_cloud_event_batch(messages);
        if events.is_none() {
            return Err(EventMeshError::EventMeshLocal("Vec is empty".to_string()).into());
        }
        let result = self.inner.batch_publish_inner(events.unwrap()).await?;
        Ok(EventMeshCloudEventUtils::get_response(&result))
    }

    /// Request reply for a message.
    async fn request_reply(&mut self, message: M, time_out: u64) -> crate::Result<M> {
        let event =
            EventMeshCloudEventUtils::build_event_mesh_cloud_event(message, &self.grpc_config);
        let result = self
            .inner
            .request_reply_inner(event.unwrap(), time_out)
            .await?;
        Ok(EventMeshCloudEventUtils::build_message_from_event_mesh_cloud_event(&result).unwrap())
    }
}
