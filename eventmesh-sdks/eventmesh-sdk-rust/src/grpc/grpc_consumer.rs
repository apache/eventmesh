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
use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use futures::lock::Mutex;
use tonic::codegen::tokio_stream::StreamExt;
use tracing::error;

use crate::common::constants::{DataContentType, SDK_STREAM_URL};
use crate::common::grpc_eventmesh_message_utils::EventMeshCloudEventUtils;
use crate::common::{ProtocolKey, ReceiveMessageListener};
use crate::config::EventMeshGrpcClientConfig;
use crate::error::EventMeshError::{EventMeshLocal, InvalidArgs};
use crate::model::message::EventMeshMessage;
use crate::model::response::EventMeshResponse;
use crate::model::subscription::{
    HeartbeatItem, SubscriptionItem, SubscriptionItemWrapper, SubscriptionReply,
};
use crate::model::EventMeshProtocolType;
use crate::net::GrpcClient;
use crate::proto_cloud_event::{PbAttr, PbCloudEvent, PbCloudEventAttributeValue, PbData};

pub struct EventMeshGrpcConsumer {
    inner: GrpcClient,
    grpc_config: EventMeshGrpcClientConfig,
    subscription_map: Arc<Mutex<HashMap<String, SubscriptionItemWrapper>>>,
    listener: Arc<Box<dyn ReceiveMessageListener<Message = EventMeshMessage>>>,
}

impl EventMeshGrpcConsumer {
    pub fn new(
        grpc_config: EventMeshGrpcClientConfig,
        listener: Box<dyn ReceiveMessageListener<Message = EventMeshMessage>>,
    ) -> Self {
        let client = GrpcClient::new(&grpc_config).unwrap();
        let subscription_map = Arc::new(Mutex::new(HashMap::with_capacity(16)));
        let listener = Arc::new(listener);
        let _ = EventMeshGrpcConsumer::heartbeat(
            client.clone(),
            grpc_config.clone(),
            Arc::clone(&subscription_map),
        );
        Self {
            inner: client,
            grpc_config,
            subscription_map: Arc::clone(&subscription_map),
            listener: Arc::clone(&listener),
        }
    }

    pub async fn subscribe_webhook(
        &mut self,
        subscription_items: Vec<SubscriptionItem>,
        url: impl Into<String>,
    ) -> crate::Result<EventMeshResponse> {
        if subscription_items.is_empty() {
            return Err(InvalidArgs("subscription_items is empty".to_string()).into());
        }
        let cloud_event = EventMeshCloudEventUtils::build_event_subscription(
            &self.grpc_config,
            EventMeshProtocolType::EventMeshMessage,
            url.into().as_str(),
            &subscription_items,
        );
        if cloud_event.is_none() {
            return Err(
                EventMeshLocal("SubscriptionItem switch to CloudEvent error".to_string()).into(),
            );
        }
        let result = self
            .inner
            .subscribe_webhook_inner(cloud_event.unwrap())
            .await?;
        Ok(EventMeshCloudEventUtils::get_response(&result))
    }

    pub async fn subscribe(
        &mut self,
        subscription_items: Vec<SubscriptionItem>,
    ) -> crate::Result<()> {
        if subscription_items.is_empty() {
            return Err(InvalidArgs("subscription_items is empty".to_string()).into());
        }

        let map = self.subscription_map.clone();
        let mut guard = map.lock().await;
        subscription_items.iter().for_each(|item| {
            guard.insert(
                item.topic.clone(),
                SubscriptionItemWrapper {
                    subscription_item: item.clone(),
                    url: SDK_STREAM_URL.to_string(),
                },
            );
        });
        let cloud_event = EventMeshCloudEventUtils::build_event_subscription(
            &self.grpc_config,
            EventMeshProtocolType::EventMeshMessage,
            String::new().as_str(),
            &subscription_items,
        );
        if cloud_event.is_none() {
            return Err(
                EventMeshLocal("SubscriptionItem switch to CloudEvent error".to_string()).into(),
            );
        }
        let (keeper, mut resp_stream) = self.inner.subscribe_bi_inner(cloud_event.unwrap()).await?;
        let listener_inner = Arc::clone(&self.listener);
        tokio::spawn(async move {
            while let Some(received) = resp_stream.next().await {
                if let Err(status) = received {
                    error!("Subscribe receive error, status {}", status);
                    continue;
                }
                let mut received = received.unwrap();
                let eventmesh_message =
                    EventMeshCloudEventUtils::build_message_from_event_mesh_cloud_event::<
                        EventMeshMessage,
                    >(&received);
                if eventmesh_message.is_none() {
                    continue;
                }
                received.attributes.insert(
                    ProtocolKey::DATA_CONTENT_TYPE.to_string(),
                    PbCloudEventAttributeValue {
                        attr: Some(PbAttr::CeString(DataContentType::JSON.to_string())),
                    },
                );

                let handled_msg = listener_inner.handle(eventmesh_message.unwrap());
                if let Ok(msg_option) = handled_msg {
                    if let Some(_msg) = msg_option {
                        let properties = HashMap::<String, String>::new();
                        let reply = SubscriptionReply::new(
                            EventMeshCloudEventUtils::get_subject(&received),
                            EventMeshCloudEventUtils::get_subject(&received),
                            EventMeshCloudEventUtils::get_data_content(&received),
                            EventMeshCloudEventUtils::get_seq_num(&received),
                            EventMeshCloudEventUtils::get_unique_id(&received),
                            EventMeshCloudEventUtils::get_ttl(&received),
                            None,
                            properties,
                        );
                        received.data =
                            Some(PbData::TextData(serde_json::to_string(&reply).unwrap()));
                        let _ = keeper.sender.send(received).await;
                    }
                } else {
                    error!("Handle Receive error:{}", handled_msg.unwrap_err())
                }
            }
        });
        Ok(())
    }

    pub async fn unsubscribe(
        &mut self,
        unsubscription_items: Vec<SubscriptionItem>,
    ) -> crate::Result<EventMeshResponse> {
        if unsubscription_items.is_empty() {
            return Err(InvalidArgs("unsubscription_items is empty".to_string()).into());
        }
        let map = self.subscription_map.clone();
        let mut guard = map.lock().await;
        unsubscription_items.iter().for_each(|item| {
            guard.remove(item.topic.as_str());
        });
        let cloud_event = EventMeshCloudEventUtils::build_event_subscription(
            &self.grpc_config,
            EventMeshProtocolType::EventMeshMessage,
            String::new().as_str(),
            &unsubscription_items,
        );
        if cloud_event.is_none() {
            return Err(
                EventMeshLocal("SubscriptionItem switch to CloudEvent error".to_string()).into(),
            );
        }
        let result = self.inner.unsubscribe_inner(cloud_event.unwrap()).await?;
        Ok(EventMeshCloudEventUtils::get_response(&result))
    }

    fn heartbeat(
        mut client: GrpcClient,
        grpc_config: EventMeshGrpcClientConfig,
        subscription_map: Arc<Mutex<HashMap<String, SubscriptionItemWrapper>>>,
    ) -> crate::Result<()> {
        tokio::spawn(async move {
            loop {
                tokio::time::sleep(Duration::from_secs(20)).await;
                let mut attributes = EventMeshCloudEventUtils::build_common_cloud_event_attributes(
                    &grpc_config,
                    EventMeshProtocolType::EventMeshMessage,
                );
                attributes.insert(
                    ProtocolKey::CONSUMERGROUP.to_string(),
                    PbCloudEventAttributeValue {
                        attr: Some(PbAttr::CeString(
                            grpc_config.consumer_group.clone().unwrap(),
                        )),
                    },
                );
                attributes.insert(
                    ProtocolKey::CLIENT_TYPE.to_string(),
                    PbCloudEventAttributeValue {
                        attr: Some(PbAttr::CeInteger(2)),
                    },
                );
                attributes.insert(
                    ProtocolKey::DATA_CONTENT_TYPE.to_string(),
                    PbCloudEventAttributeValue {
                        attr: Some(PbAttr::CeString(DataContentType::JSON.to_string())),
                    },
                );

                let map = subscription_map.lock().await;
                let heartbeat_items = map
                    .iter()
                    .filter_map(|(key, value)| {
                        Some(HeartbeatItem {
                            topic: key.to_string(),
                            url: value.url.clone(),
                        })
                    })
                    .collect::<Vec<HeartbeatItem>>();
                let mut cloud_event = PbCloudEvent::default();
                cloud_event.attributes = attributes;
                cloud_event.data = Some(PbData::TextData(
                    serde_json::to_string(&heartbeat_items).unwrap(),
                ));
                let _result = client.heartbeat_inner(cloud_event).await;
            }
        });
        Ok(())
    }
}
