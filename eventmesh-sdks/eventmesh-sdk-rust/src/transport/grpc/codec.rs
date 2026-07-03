//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  The ASF
// licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//

//! Conversions between user-facing message types and the CloudEvents protobuf
//! wire format.
//!
//! This is a clean rewrite of the old
//! `grpc_eventmesh_message_utils.rs`: no `unsafe`, no `todo!()`, no `.unwrap()`
//! on network data, and the duplicate `seqnum` insert bug is gone.

use std::collections::HashMap;

use prost_types::Any as PbAny;

use crate::common::constants::{DataContentType, DEFAULT_MESSAGE_TTL, SDK_STREAM_URL};
use crate::common::{ProtocolKey, RandomStringUtils};
use crate::config::GrpcClientConfig;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, EventMeshProtocolType, PublishResponse, SubscriptionItem};
use crate::proto_gen::{
    attr_as_str, attr_int, attr_str, PbAttr, PbCloudEvent, PbCloudEventAttributeValue,
    PbCloudEventBatch, PbData,
};

/// The CloudEvent `type` for EventMesh-internal events.
const CLOUD_EVENT_TYPE: &str = "org.apache.eventmesh";
/// Default CloudEvent `source` (URI-reference `/`).
const DEFAULT_SOURCE: &str = "/";

/// Helper: does this content-type imply text data on the wire?
pub struct ProtoSupport;
impl ProtoSupport {
    pub fn is_text_content(content_type: &str) -> bool {
        content_type.starts_with("text/")
            || content_type == DataContentType::JSON
            || content_type == DataContentType::XML
            || content_type.ends_with("+json")
            || content_type.ends_with("+xml")
    }

    pub fn is_proto_content(content_type: &str) -> bool {
        content_type == DataContentType::PROTOBUF
    }
}

/// The public conversion façade.
pub struct CloudEventCodec;

impl CloudEventCodec {
    /// Build the common identity attributes (`env/idc/ip/pid/sys/language/...`)
    /// that every request must carry.
    pub fn common_attributes(
        config: &GrpcClientConfig,
        protocol_type: EventMeshProtocolType,
    ) -> HashMap<String, PbCloudEventAttributeValue> {
        let id = &config.identity;
        let mut m = HashMap::with_capacity(16);
        m.insert(ProtocolKey::ENV.into(), attr_str(&id.env));
        m.insert(ProtocolKey::IDC.into(), attr_str(&id.idc));
        m.insert(ProtocolKey::IP.into(), attr_str(&id.ip));
        m.insert(ProtocolKey::PID.into(), attr_str(&id.pid));
        m.insert(ProtocolKey::SYS.into(), attr_str(&id.sys));
        m.insert(ProtocolKey::LANGUAGE.into(), attr_str(&id.language));
        m.insert(ProtocolKey::USERNAME.into(), attr_str(&id.username));
        m.insert(ProtocolKey::PASSWD.into(), attr_str(&id.password));
        m.insert(
            ProtocolKey::PROTOCOL_TYPE.into(),
            attr_str(protocol_type.as_str()),
        );
        m.insert(ProtocolKey::PROTOCOL_VERSION.into(), attr_str("1.0"));
        if let Some(token) = &id.token {
            if !token.is_empty() {
                m.insert("token".into(), attr_str(token));
            }
        }
        m
    }

    /// Build the subscription CloudEvent (carries the `SubscriptionItem` JSON
    /// list in `text_data`, plus the optional webhook `url`).
    pub fn build_subscription_event(
        config: &GrpcClientConfig,
        protocol_type: EventMeshProtocolType,
        url: Option<&str>,
        items: &[SubscriptionItem],
    ) -> Result<PbCloudEvent> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }
        let mut attrs = Self::common_attributes(config, protocol_type);
        attrs.insert(
            ProtocolKey::CONSUMERGROUP.into(),
            attr_str(&config.identity.consumer_group),
        );
        attrs.insert(
            ProtocolKey::DATA_CONTENT_TYPE.into(),
            attr_str(DataContentType::JSON),
        );
        if let Some(u) = url {
            let trimmed = u.trim();
            if !trimmed.is_empty() {
                attrs.insert(ProtocolKey::URL.into(), attr_str(trimmed));
            }
        }
        let text = serde_json::to_string(items)?;
        Ok(Self::base_event(attrs, Some(PbData::TextData(text))))
    }

    /// Convert an [`EventMeshMessage`] into the wire CloudEvent for publishing.
    pub fn from_event_mesh_message(
        message: &EventMeshMessage,
        config: &GrpcClientConfig,
    ) -> Result<PbCloudEvent> {
        let protocol_type = EventMeshProtocolType::EventMeshMessage;
        let mut attrs = Self::common_attributes(config, protocol_type);

        let ttl = message
            .ttl
            .map(|t| t.to_string())
            .or_else(|| message.get_prop(ProtocolKey::TTL).map(str::to_string))
            .unwrap_or_else(|| DEFAULT_MESSAGE_TTL.to_string());
        let seq_num = message
            .biz_seq_no
            .clone()
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| RandomStringUtils::generate_num(30));
        let unique_id = message
            .unique_id
            .clone()
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| RandomStringUtils::generate_num(30));

        attrs.insert(ProtocolKey::TTL.into(), attr_str(ttl));
        attrs.insert(ProtocolKey::SEQ_NUM.into(), attr_str(&seq_num));
        attrs.insert(ProtocolKey::UNIQUE_ID.into(), attr_str(&unique_id));
        attrs.insert(
            ProtocolKey::PRODUCERGROUP.into(),
            attr_str(&config.identity.producer_group),
        );
        attrs.insert(
            ProtocolKey::PROTOCOL_DESC.into(),
            attr_str(ProtocolKey::PROTOCOL_DESC_GRPC_CLOUD_EVENT),
        );

        if let Some(topic) = &message.topic {
            if !topic.is_empty() {
                attrs.insert(ProtocolKey::SUBJECT.into(), attr_str(topic));
            }
        }

        // Resolve the content type from props (default text/plain).
        let data_content_type = message
            .get_prop(ProtocolKey::DATA_CONTENT_TYPE)
            .unwrap_or(DataContentType::TEXT_PLAIN)
            .to_string();
        attrs.insert(
            ProtocolKey::DATA_CONTENT_TYPE.into(),
            attr_str(data_content_type.as_str()),
        );

        // Fold remaining user props into attributes (excluding ones we already set).
        for (k, v) in &message.props {
            attrs.entry(k.clone()).or_insert_with(|| attr_str(v));
        }

        let data = match &message.content {
            Some(content) if ProtoSupport::is_text_content(&data_content_type) => {
                Some(PbData::TextData(content.clone()))
            }
            Some(content) if ProtoSupport::is_proto_content(&data_content_type) => {
                Some(PbData::ProtoData(PbAny {
                    type_url: String::new(),
                    value: content.as_bytes().to_vec(),
                }))
            }
            Some(content) => Some(PbData::BinaryData(content.as_bytes().to_vec())),
            None => None,
        };

        Ok(Self::base_event(attrs, data))
    }

    /// Build a `CloudEventBatch` from many messages (one RPC, many events).
    pub fn from_event_mesh_messages(
        messages: &[EventMeshMessage],
        config: &GrpcClientConfig,
    ) -> Result<PbCloudEventBatch> {
        let mut events = Vec::with_capacity(messages.len());
        for m in messages {
            events.push(Self::from_event_mesh_message(m, config)?);
        }
        Ok(PbCloudEventBatch { events })
    }

    /// Decode a delivered CloudEvent back into an [`EventMeshMessage`].
    pub fn to_event_mesh_message(cloud_event: &PbCloudEvent) -> EventMeshMessage {
        let mut props = HashMap::with_capacity(cloud_event.attributes.len());
        for (key, value) in &cloud_event.attributes {
            props.insert(key.clone(), attr_as_str(value));
        }
        let topic = Self::get_subject(cloud_event);
        let biz_seq_no = Self::get_seq_num(cloud_event);
        let unique_id = Self::get_unique_id(cloud_event);
        let content = Self::get_text_data(cloud_event);
        let ttl = Self::get_ttl(cloud_event).parse::<i64>().ok();

        EventMeshMessage {
            biz_seq_no: (!biz_seq_no.is_empty()).then_some(biz_seq_no),
            unique_id: (!unique_id.is_empty()).then_some(unique_id),
            topic: (!topic.is_empty()).then_some(topic),
            content: (!content.is_empty()).then_some(content),
            props,
            create_time: crate::common::util::now_millis(),
            ttl,
        }
    }

    /// Extract the broker [`PublishResponse`] (status_code / message / time).
    pub fn to_response(cloud_event: &PbCloudEvent) -> PublishResponse {
        let code = cloud_event
            .attributes
            .get(ProtocolKey::GRPC_RESPONSE_CODE)
            .and_then(|v| v.attr.as_ref())
            .and_then(|a| match a {
                PbAttr::CeString(s) => s.parse::<i64>().ok(),
                PbAttr::CeInteger(i) => Some(*i as i64),
                _ => None,
            });
        let message = cloud_event
            .attributes
            .get(ProtocolKey::GRPC_RESPONSE_MESSAGE)
            .map(attr_as_str)
            .filter(|s| !s.is_empty());
        let time = cloud_event
            .attributes
            .get(ProtocolKey::GRPC_RESPONSE_TIME)
            .and_then(|v| attr_as_str(v).parse::<i64>().ok());
        PublishResponse::new(code, message, time)
    }

    pub fn get_seq_num(cloud_event: &PbCloudEvent) -> String {
        cloud_event
            .attributes
            .get(ProtocolKey::SEQ_NUM)
            .map(attr_as_str)
            .unwrap_or_default()
    }

    pub fn get_unique_id(cloud_event: &PbCloudEvent) -> String {
        cloud_event
            .attributes
            .get(ProtocolKey::UNIQUE_ID)
            .map(attr_as_str)
            .unwrap_or_default()
    }

    pub fn get_subject(cloud_event: &PbCloudEvent) -> String {
        // Only read the `subject` attribute — do NOT fall back to `source`.
        // Internally-built events set `source` to the default "/", so a fallback
        // would yield a topic of "/" instead of an empty topic. This mirrors
        // EventMeshCloudEventUtils.getSubject in the Java SDK.
        cloud_event
            .attributes
            .get(ProtocolKey::SUBJECT)
            .map(attr_as_str)
            .unwrap_or_default()
    }

    pub fn get_ttl(cloud_event: &PbCloudEvent) -> String {
        cloud_event
            .attributes
            .get(ProtocolKey::TTL)
            .map(attr_as_str)
            .unwrap_or_default()
    }

    pub fn get_text_data(cloud_event: &PbCloudEvent) -> String {
        match &cloud_event.data {
            Some(PbData::TextData(s)) => s.clone(),
            Some(PbData::BinaryData(b)) => String::from_utf8_lossy(b).into_owned(),
            Some(PbData::ProtoData(any)) => String::from_utf8_lossy(&any.value).into_owned(),
            None => String::new(),
        }
    }

    /// Assemble a base CloudEvent with a fresh id and the common envelope
    /// fields.
    fn base_event(
        attributes: HashMap<String, PbCloudEventAttributeValue>,
        data: Option<PbData>,
    ) -> PbCloudEvent {
        PbCloudEvent {
            id: RandomStringUtils::generate_uuid(),
            source: DEFAULT_SOURCE.into(),
            spec_version: "1.0".into(),
            r#type: CLOUD_EVENT_TYPE.into(),
            attributes,
            data,
        }
    }

    /// Mark a CloudEvent as a subscription reply (sent back over the stream).
    ///
    /// Only tags the message with `SUB_MESSAGE_TYPE = SUBSCRIPTION_REPLY`. The
    /// reply's data is left intact: EventMesh's `ReplyMessageProcessor` runs
    /// `ServiceUtils.validateCloudEventData`, which for text content requires a
    /// non-empty `textData` — clearing the data here would make the reply fail
    /// validation and never reach `producer.reply()`, breaking request/reply.
    /// This mirrors the Java SDK's `SubStreamHandler.buildReplyMessage`, which
    /// does not strip the reply payload.
    pub fn mark_as_reply(cloud_event: &mut PbCloudEvent) {
        cloud_event.attributes.insert(
            ProtocolKey::SUB_MESSAGE_TYPE.into(),
            attr_str(ProtocolKey::SUBSCRIPTION_REPLY),
        );
    }
}

/// (Optional) CloudEvents interop: convert a native [`cloudevents::Event`]
/// to the wire CloudEvent.
#[cfg(feature = "cloud_events")]
pub struct CloudEventMessage;

#[cfg(feature = "cloud_events")]
impl CloudEventMessage {
    pub fn from_event(
        event: &cloudevents::Event,
        config: &GrpcClientConfig,
    ) -> Result<PbCloudEvent> {
        use cloudevents::AttributesReader;

        let protocol_type = EventMeshProtocolType::CloudEvents;
        let mut attrs = CloudEventCodec::common_attributes(config, protocol_type);

        let ttl = event
            .extension(ProtocolKey::TTL)
            .map(|v| v.to_string())
            .unwrap_or_else(|| DEFAULT_MESSAGE_TTL.to_string());
        let seq_num = event
            .extension(ProtocolKey::SEQ_NUM)
            .map(|v| v.to_string())
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| RandomStringUtils::generate_num(30));

        attrs.insert(ProtocolKey::TTL.into(), attr_str(ttl));
        attrs.insert(ProtocolKey::SEQ_NUM.into(), attr_str(&seq_num));
        attrs.insert(
            ProtocolKey::UNIQUE_ID.into(),
            attr_str(event.id().to_string()),
        );
        attrs.insert(
            ProtocolKey::PRODUCERGROUP.into(),
            attr_str(&config.identity.producer_group),
        );
        attrs.insert(
            ProtocolKey::PROTOCOL_DESC.into(),
            attr_str(ProtocolKey::PROTOCOL_DESC_GRPC_CLOUD_EVENT),
        );
        if let Some(subject) = event.subject() {
            attrs.insert(ProtocolKey::SUBJECT.into(), attr_str(subject));
        }
        if let Some(dct) = event.datacontenttype() {
            attrs.insert(ProtocolKey::DATA_CONTENT_TYPE.into(), attr_str(dct));
        } else {
            attrs.insert(
                ProtocolKey::DATA_CONTENT_TYPE.into(),
                attr_str(DataContentType::TEXT_PLAIN),
            );
        }
        for (k, v) in event.iter_extensions() {
            attrs
                .entry(k.to_string())
                .or_insert_with(|| attr_str(v.to_string()));
        }

        let data = match event.data() {
            Some(cloudevents::Data::String(s)) => Some(PbData::TextData(s.clone())),
            Some(cloudevents::Data::Binary(b)) => Some(PbData::BinaryData(b.clone())),
            Some(cloudevents::Data::Json(j)) => Some(PbData::TextData(j.to_string())),
            None => None,
        };

        Ok(PbCloudEvent {
            id: RandomStringUtils::generate_uuid(),
            source: event.source().to_string(),
            spec_version: event.specversion().to_string(),
            r#type: event.ty().to_string(),
            attributes: attrs,
            data,
        })
    }

    pub fn to_event(cloud_event: PbCloudEvent) -> Result<cloudevents::Event> {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let topic = CloudEventCodec::get_subject(&cloud_event);
        let unique_id = CloudEventCodec::get_unique_id(&cloud_event);
        let content = CloudEventCodec::get_text_data(&cloud_event);
        let source = if cloud_event.source.is_empty() {
            DEFAULT_SOURCE.to_string()
        } else {
            cloud_event.source.clone()
        };
        let content_type = cloud_event
            .attributes
            .get(ProtocolKey::DATA_CONTENT_TYPE)
            .map(attr_as_str)
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| DataContentType::JSON.to_string());

        let mut builder = EventBuilderV10::new()
            .id(unique_id)
            .source(source)
            .ty(ProtocolKey::CLOUD_EVENTS_PROTOCOL_NAME)
            .data(content_type, content);
        if !topic.is_empty() {
            builder = builder.subject(topic);
        }
        for (k, v) in cloud_event.attributes {
            // Skip response / envelope fields that don't belong as extensions.
            // Use the canonical ProtocolKey spellings so the skip actually matches
            // the keys written by the server ("statuscode"/"responsemessage"/"time").
            if matches!(
                k.as_str(),
                ProtocolKey::GRPC_RESPONSE_CODE
                    | ProtocolKey::GRPC_RESPONSE_MESSAGE
                    | ProtocolKey::GRPC_RESPONSE_TIME
                    | "datacontenttype"
            ) {
                continue;
            }
            builder = builder.extension(k.as_str(), attr_as_str(&v));
        }
        builder
            .build()
            .map_err(|e| EventMeshError::Other(format!("cloudevents build error: {e}")))
    }
}

/// Build a heartbeat CloudEvent.
pub(crate) fn build_heartbeat(
    config: &GrpcClientConfig,
    items: &[(String, String)],
) -> Result<PbCloudEvent> {
    let mut attrs =
        CloudEventCodec::common_attributes(config, EventMeshProtocolType::EventMeshMessage);
    attrs.insert(
        ProtocolKey::CONSUMERGROUP.into(),
        attr_str(&config.identity.consumer_group),
    );
    attrs.insert(ProtocolKey::CLIENT_TYPE.into(), attr_int(2)); // SUB
    attrs.insert(
        ProtocolKey::DATA_CONTENT_TYPE.into(),
        attr_str(DataContentType::JSON),
    );

    let heartbeat_items: Vec<crate::model::HeartbeatItem> = items
        .iter()
        .map(|(topic, url)| crate::model::HeartbeatItem {
            topic: topic.clone(),
            url: if url.is_empty() {
                SDK_STREAM_URL.to_string()
            } else {
                url.clone()
            },
        })
        .collect();
    let text = serde_json::to_string(&heartbeat_items)?;
    Ok(CloudEventCodec::base_event(
        attrs,
        Some(PbData::TextData(text)),
    ))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::GrpcClientConfig;

    fn cfg() -> GrpcClientConfig {
        GrpcClientConfig::builder()
            .server_addr("127.0.0.1")
            .server_port(10205)
            .env("env")
            .idc("idc")
            .producer_group("pg")
            .consumer_group("cg")
            .build()
    }

    #[test]
    fn round_trips_message_to_cloud_event() {
        let cfg = cfg();
        let msg = EventMeshMessage::builder()
            .topic("test-topic")
            .content("hello")
            .biz_seq_no("seq-1")
            .unique_id("uid-1")
            .prop("custom", "val")
            .build();
        let ce = CloudEventCodec::from_event_mesh_message(&msg, &cfg).unwrap();
        assert_eq!(CloudEventCodec::get_subject(&ce), "test-topic");
        assert_eq!(CloudEventCodec::get_seq_num(&ce), "seq-1");
        assert_eq!(CloudEventCodec::get_text_data(&ce), "hello");
        assert_eq!(ce.attributes.get("custom").map(attr_as_str).unwrap(), "val");

        let back = CloudEventCodec::to_event_mesh_message(&ce);
        assert_eq!(back.topic.as_deref(), Some("test-topic"));
        assert_eq!(back.content.as_deref(), Some("hello"));
    }

    #[test]
    fn builds_subscription_event_with_url() {
        let cfg = cfg();
        let items = vec![SubscriptionItem::new(
            "t",
            crate::model::SubscriptionMode::CLUSTERING,
            crate::model::SubscriptionType::ASYNC,
        )];
        let ce = CloudEventCodec::build_subscription_event(
            &cfg,
            EventMeshProtocolType::EventMeshMessage,
            Some("http://x/y"),
            &items,
        )
        .unwrap();
        assert_eq!(
            ce.attributes.get("url").map(attr_as_str).unwrap(),
            "http://x/y"
        );
        assert_eq!(
            ce.attributes.get("consumergroup").map(attr_as_str).unwrap(),
            "cg"
        );
        // The topic list is carried as JSON in text_data.
        assert!(CloudEventCodec::get_text_data(&ce).contains("\"topic\":\"t\""));
    }

    #[test]
    fn parses_response_code() {
        let mut ce = PbCloudEvent::default();
        ce.attributes
            .insert(ProtocolKey::GRPC_RESPONSE_CODE.into(), attr_str("0"));
        ce.attributes
            .insert(ProtocolKey::GRPC_RESPONSE_MESSAGE.into(), attr_str("ok"));
        let resp = CloudEventCodec::to_response(&ce);
        assert!(resp.is_success());
        assert_eq!(resp.message.as_deref(), Some("ok"));
    }
}
