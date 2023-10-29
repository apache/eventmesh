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
use std::collections::{HashMap, HashSet};
use std::fmt::Debug;
use std::time::{SystemTime, UNIX_EPOCH};

use cloudevents::Data::String as EventString;
use cloudevents::{AttributesReader, Data, Event};
use tonic::transport::Uri;

use crate::common::constants::{DataContentType, SpecVersion, DEFAULT_EVENTMESH_MESSAGE_TTL};
use crate::common::{ProtocolKey, RandomStringUtils};
use crate::config::EventMeshGrpcClientConfig;
use crate::model::message::EventMeshMessage;
use crate::model::response::EventMeshResponse;
use crate::model::subscription::SubscriptionItem;
use crate::model::EventMeshProtocolType;
use crate::proto_cloud_event::{PbAttr, PbCloudEvent, PbCloudEventAttributeValue, PbData};

pub struct ProtoSupport;

impl ProtoSupport {
    pub fn is_text_content(content_type: &str) -> bool {
        if content_type.is_empty() {
            return false;
        }

        content_type.starts_with("text/")
            || content_type == "application/json"
            || content_type == "application/xml"
            || content_type.ends_with("+json")
            || content_type.ends_with("+xml")
    }

    pub fn is_proto_content(content_type: &str) -> bool {
        content_type == "application/protobuf"
    }
}

pub struct EventMeshCloudEventUtils;

impl EventMeshCloudEventUtils {
    const CLOUD_EVENT_TYPE: &'static str = "org.apache.eventmesh";

    pub fn build_common_cloud_event_attributes(
        client_config: &EventMeshGrpcClientConfig,
        protocol_type: EventMeshProtocolType,
    ) -> HashMap<String, PbCloudEventAttributeValue> {
        let mut attribute_value_map = HashMap::with_capacity(64);
        attribute_value_map.insert(
            ProtocolKey::ENV.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(client_config.env.clone())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::IDC.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(client_config.idc.clone())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::IP.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(crate::common::local_ip::get_local_ip_v4())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::PID.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(std::process::id().to_string())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::SYS.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(client_config.sys.clone())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::LANGUAGE.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(client_config.language.clone())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::USERNAME.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(client_config.user_name.clone())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::PASSWD.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(client_config.password.clone())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::PROTOCOL_TYPE.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(
                    protocol_type.protocol_type_name().to_string(),
                )),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::PROTOCOL_VERSION.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(SpecVersion::V1.to_string())),
            },
        );
        attribute_value_map
    }

    pub fn build_event_subscription(
        client_config: &EventMeshGrpcClientConfig,
        protocol_type: EventMeshProtocolType,
        url: &str,
        subscription_items: &[SubscriptionItem],
    ) -> Option<PbCloudEvent> {
        if subscription_items.is_empty() {
            return None;
        }
        let subscription_item_set: HashSet<SubscriptionItem> =
            subscription_items.iter().cloned().collect();
        let mut attribute_value_map =
            Self::build_common_cloud_event_attributes(client_config, protocol_type);
        attribute_value_map.insert(
            ProtocolKey::CONSUMERGROUP.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(client_config.consumer_group.clone()?)),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::DATA_CONTENT_TYPE.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(DataContentType::JSON.to_string())),
            },
        );
        if !url.trim().is_empty() {
            attribute_value_map.insert(
                ProtocolKey::URL.to_string(),
                PbCloudEventAttributeValue {
                    attr: Some(PbAttr::CeString(url.to_string())),
                },
            );
        }
        let subscription_item_json = serde_json::to_string(&subscription_item_set);
        if subscription_item_json.is_err() {
            return None;
        }
        Some(PbCloudEvent {
            id: RandomStringUtils::generate_uuid(),
            source: Uri::builder()
                .path_and_query("/")
                .build()
                .unwrap()
                .to_string(),
            spec_version: SpecVersion::V1.to_string(),
            r#type: Self::CLOUD_EVENT_TYPE.to_string(),
            attributes: attribute_value_map,
            data: Some(PbData::TextData(subscription_item_json.unwrap())),
        })
    }

    pub fn build_event_mesh_cloud_event<T>(
        message: T,
        client_config: &EventMeshGrpcClientConfig,
    ) -> Option<PbCloudEvent>
    where
        T: Any,
    {
        let message_any = &message as &dyn Any;

        if let Some(em_message) = message_any.downcast_ref::<EventMeshMessage>() {
            return Some(Self::switch_event_mesh_message_2_event_mesh_cloud_event(
                em_message,
                client_config,
                EventMeshProtocolType::EventMeshMessage,
            ));
        }
        if let Some(cloud_event) = message_any.downcast_ref::<Event>() {
            return Some(Self::switch_cloud_event_2_event_mesh_cloud_event(
                cloud_event,
                client_config,
                EventMeshProtocolType::CloudEvents,
            ));
        }

        None
    }

    pub fn switch_event_mesh_message_2_event_mesh_cloud_event(
        message: &EventMeshMessage,
        client_config: &EventMeshGrpcClientConfig,
        protocol_type: EventMeshProtocolType,
    ) -> PbCloudEvent {
        let mut attribute_value_map =
            Self::build_common_cloud_event_attributes(client_config, protocol_type);
        let ttl = message
            .get_prop(ProtocolKey::TTL)
            .cloned()
            .unwrap_or_else(|| DEFAULT_EVENTMESH_MESSAGE_TTL.to_string());
        let seq_num = message
            .biz_seq_no
            .clone()
            .unwrap_or_else(|| RandomStringUtils::generate_num(30));
        let unique_id = message
            .unique_id
            .clone()
            .unwrap_or_else(|| RandomStringUtils::generate_num(30));
        attribute_value_map
            .entry(ProtocolKey::DATA_CONTENT_TYPE.to_string())
            .or_insert_with(|| PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(DataContentType::TEXT_PLAIN.to_string())),
            });

        attribute_value_map.insert(
            ProtocolKey::TTL.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(ttl.to_string())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::SEQ_NUM.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(seq_num.to_string())),
            },
        );

        attribute_value_map.insert(
            ProtocolKey::SEQ_NUM.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(seq_num.to_string())),
            },
        );

        attribute_value_map.insert(
            ProtocolKey::PROTOCOL_DESC.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(
                    ProtocolKey::PROTOCOL_DESC_GRPC_CLOUD_EVENT.to_string(),
                )),
            },
        );

        attribute_value_map.insert(
            ProtocolKey::UNIQUE_ID.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(unique_id.to_string())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::PRODUCERGROUP.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(
                    client_config.producer_group.clone().unwrap(),
                )),
            },
        );
        if let Some(topic) = &message.topic {
            attribute_value_map.insert(
                ProtocolKey::SUBJECT.to_string(),
                PbCloudEventAttributeValue {
                    attr: Some(PbAttr::CeString(topic.to_string())),
                },
            );
        }
        attribute_value_map.insert(
            ProtocolKey::DATA_CONTENT_TYPE.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(DataContentType::TEXT_PLAIN.to_string())),
            },
        );
        let props = &message.prop;
        let text_plain = DataContentType::TEXT_PLAIN.to_string();
        let data_content_type = props
            .get(ProtocolKey::DATA_CONTENT_TYPE)
            .unwrap_or(&text_plain);
        props.iter().for_each(|(key, value)| {
            attribute_value_map.insert(
                key.to_string(),
                PbCloudEventAttributeValue {
                    attr: Some(PbAttr::CeString(value.to_string())),
                },
            );
        });

        let data = {
            if let Some(content) = &message.content {
                if ProtoSupport::is_text_content(data_content_type) {
                    Some(PbData::TextData(content.to_string()))
                } else if ProtoSupport::is_proto_content(data_content_type) {
                    Some(PbData::ProtoData(prost_types::Any {
                        type_url: String::from(""),
                        value: content.clone().into_bytes(),
                    }))
                } else {
                    Some(PbData::BinaryData(content.clone().into_bytes()))
                }
            } else {
                None
            }
        };
        PbCloudEvent {
            id: RandomStringUtils::generate_uuid(),
            source: Uri::builder()
                .path_and_query("/")
                .build()
                .unwrap()
                .to_string(),
            spec_version: SpecVersion::V1.to_string(),
            r#type: Self::CLOUD_EVENT_TYPE.to_string(),
            attributes: attribute_value_map,
            data,
        }
    }

    pub fn switch_cloud_event_2_event_mesh_cloud_event(
        message: &Event,
        client_config: &EventMeshGrpcClientConfig,
        protocol_type: EventMeshProtocolType,
    ) -> PbCloudEvent {
        let mut attribute_value_map =
            Self::build_common_cloud_event_attributes(client_config, protocol_type);
        let ttl = message
            .extension(ProtocolKey::TTL)
            .map_or(DEFAULT_EVENTMESH_MESSAGE_TTL.to_string(), |value| {
                value.to_string()
            });
        let seq_num = message
            .extension(ProtocolKey::SEQ_NUM)
            .map_or(RandomStringUtils::generate_num(30), |value| {
                value.to_string()
            });
        let unique_id = message.id().to_string();
        attribute_value_map
            .entry(ProtocolKey::DATA_CONTENT_TYPE.to_string())
            .or_insert_with(|| PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(DataContentType::TEXT_PLAIN.to_string())),
            });

        attribute_value_map.insert(
            ProtocolKey::TTL.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(ttl.to_string())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::SEQ_NUM.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(seq_num.to_string())),
            },
        );

        attribute_value_map.insert(
            ProtocolKey::SEQ_NUM.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(seq_num.to_string())),
            },
        );

        attribute_value_map.insert(
            ProtocolKey::PROTOCOL_DESC.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(
                    ProtocolKey::PROTOCOL_DESC_GRPC_CLOUD_EVENT.to_string(),
                )),
            },
        );

        attribute_value_map.insert(
            ProtocolKey::UNIQUE_ID.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(unique_id.to_string())),
            },
        );
        attribute_value_map.insert(
            ProtocolKey::PRODUCERGROUP.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(
                    client_config.producer_group.clone().unwrap(),
                )),
            },
        );

        attribute_value_map.insert(
            ProtocolKey::SUBJECT.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(
                    message
                        .subject()
                        .map_or(String::from(""), |value| value.to_string()),
                )),
            },
        );

        attribute_value_map.insert(
            ProtocolKey::DATA_CONTENT_TYPE.to_string(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeString(DataContentType::TEXT_PLAIN.to_string())),
            },
        );
        message.iter_extensions().for_each(|(key, value)| {
            attribute_value_map.insert(
                key.to_string(),
                PbCloudEventAttributeValue {
                    attr: Some(PbAttr::CeString(value.to_string())),
                },
            );
        });

        let data = {
            if let Some(content) = message.data() {
                match content {
                    Data::Binary(bytes) => Some(PbData::ProtoData(prost_types::Any {
                        type_url: String::from(""),
                        value: bytes.clone(),
                    })),
                    EventString(string) => Some(PbData::TextData(string.clone())),
                    Data::Json(_json) => None,
                }
            } else {
                None
            }
        };
        PbCloudEvent {
            id: RandomStringUtils::generate_uuid(),
            source: Uri::builder()
                .path_and_query("/")
                .build()
                .unwrap()
                .to_string(),
            spec_version: SpecVersion::V1.to_string(),
            r#type: Self::CLOUD_EVENT_TYPE.to_string(),
            attributes: attribute_value_map,
            data,
        }
    }

    pub fn build_message_from_event_mesh_cloud_event<T>(cloud_event: &PbCloudEvent) -> Option<T>
    where
        T: Any + Debug + From<PbCloudEvent>,
    {
        let seq = EventMeshCloudEventUtils::get_seq_num(cloud_event);
        let unique_id = EventMeshCloudEventUtils::get_unique_id(cloud_event);

        if seq.is_empty() || unique_id.is_empty() {
            return None;
        }
        Some(T::from(cloud_event.clone()))
    }

    pub(crate) fn switch_event_mesh_cloud_event_2_event_mesh_message(
        cloud_event: &PbCloudEvent,
    ) -> EventMeshMessage {
        let mut prop = HashMap::new();
        cloud_event.attributes.iter().for_each(|(key, value)| {
            prop.insert(
                key.to_string(),
                (&(value.attr)).clone().unwrap().to_string(),
            );
        });
        let topic = EventMeshCloudEventUtils::get_subject(cloud_event);
        let biz_seq_no = EventMeshCloudEventUtils::get_seq_num(cloud_event);
        let unique_id = EventMeshCloudEventUtils::get_unique_id(cloud_event);
        let content = EventMeshCloudEventUtils::get_text_data(cloud_event);
        EventMeshMessage {
            biz_seq_no: Some(biz_seq_no),
            unique_id: Some(unique_id),
            topic: Some(topic),
            content: Some(content),
            prop,
            create_time: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map_or_else(|_err| 0u64, |time| time.as_millis() as u64),
        }
    }

    #[allow(dead_code)]
    pub(crate) fn switch_cloud_event_2_event_mesh_message(cloud_event: Event) -> EventMeshMessage {
        let mut prop = HashMap::new();
        cloud_event.iter_attributes().for_each(|(key, value)| {
            prop.insert(key.to_string(), value.to_string());
        });
        let topic = cloud_event.subject().unwrap().to_string();
        let biz_seq_no = cloud_event
            .extension(ProtocolKey::SEQ_NUM)
            .unwrap()
            .to_string();
        let unique_id = cloud_event.id().to_string();
        let content = cloud_event.data().unwrap().to_string();
        EventMeshMessage {
            biz_seq_no: Some(biz_seq_no),
            unique_id: Some(unique_id),
            topic: Some(topic),
            content: Some(content),
            prop,
            create_time: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map_or_else(|_err| 0u64, |time| time.as_millis() as u64),
        }
    }

    pub fn get_seq_num(cloud_event: &PbCloudEvent) -> String {
        cloud_event
            .attributes
            .get(ProtocolKey::SEQ_NUM)
            .map_or_else(
                || String::new(),
                |ce| {
                    ce.attr
                        .clone()
                        .unwrap_or(PbAttr::CeString(String::new()))
                        .to_string()
                },
            )
    }

    pub fn get_unique_id(cloud_event: &PbCloudEvent) -> String {
        cloud_event
            .attributes
            .get(ProtocolKey::UNIQUE_ID)
            .map_or_else(
                || String::new(),
                |ce| {
                    ce.attr
                        .clone()
                        .unwrap_or(PbAttr::CeString(String::new()))
                        .to_string()
                },
            )
    }

    pub fn get_data_content(cloud_event: &PbCloudEvent) -> String {
        cloud_event
            .attributes
            .get(ProtocolKey::DATA_CONTENT_TYPE)
            .map_or_else(
                || String::new(),
                |ce| {
                    ce.attr
                        .clone()
                        .unwrap_or(PbAttr::CeString(String::new()))
                        .to_string()
                },
            )
    }

    pub fn get_subject(cloud_event: &PbCloudEvent) -> String {
        cloud_event
            .attributes
            .get(ProtocolKey::SUBJECT)
            .map_or_else(
                || String::new(),
                |ce| {
                    ce.attr
                        .clone()
                        .unwrap_or(PbAttr::CeString(String::new()))
                        .to_string()
                },
            )
    }

    pub fn get_text_data(cloud_event: &PbCloudEvent) -> String {
        cloud_event
            .data
            .clone()
            .unwrap_or(PbData::TextData(String::new()))
            .to_string()
    }

    pub fn get_response(cloud_event: &PbCloudEvent) -> EventMeshResponse {
        let code = cloud_event
            .attributes
            .get(ProtocolKey::GRPC_RESPONSE_CODE)
            .map_or_else(
                || None,
                |val| {
                    if let Some(ref value) = val.attr {
                        Some(value.to_string())
                    } else {
                        None
                    }
                },
            );
        let msg = cloud_event
            .attributes
            .get(ProtocolKey::GRPC_RESPONSE_MESSAGE)
            .map_or_else(
                || None,
                |val| {
                    if let Some(ref value) = val.attr {
                        Some(value.to_string())
                    } else {
                        None
                    }
                },
            );
        let time = cloud_event
            .attributes
            .get(ProtocolKey::GRPC_RESPONSE_TIME)
            .map_or_else(
                || None,
                |val| {
                    if let Some(ref value) = val.attr {
                        Some(value.to_string().parse::<i64>().unwrap_or(0))
                    } else {
                        None
                    }
                },
            );
        EventMeshResponse::new(code, msg, time)
    }

    pub fn get_ttl(cloud_event: &PbCloudEvent) -> String {
        cloud_event.attributes.get(ProtocolKey::TTL).map_or_else(
            || String::new(),
            |ce| {
                ce.attr
                    .clone()
                    .unwrap_or(PbAttr::CeString(String::new()))
                    .to_string()
            },
        )
    }
}
