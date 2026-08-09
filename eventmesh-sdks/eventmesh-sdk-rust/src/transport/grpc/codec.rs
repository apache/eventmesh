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

//! Conversions between user-facing message types and the CloudEvents protobuf
//! wire format.
//!
//! All helpers here are free functions (mirroring the style of
//! [`crate::transport::http::codec`]). Encoding goes user message →
//! `PbCloudEvent`; decoding goes `PbCloudEvent` → user type.

use std::collections::HashMap;

use prost::Message as _;
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

/// Does this content-type imply text data on the wire?
pub fn is_text_content(content_type: &str) -> bool {
    content_type.starts_with("text/")
        || content_type == DataContentType::JSON
        || content_type == DataContentType::XML
        || content_type.ends_with("+json")
        || content_type.ends_with("+xml")
}

/// Does this content-type imply protobuf data on the wire?
pub fn is_proto_content(content_type: &str) -> bool {
    content_type == DataContentType::PROTOBUF
}

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
    let mut attrs = common_attributes(config, protocol_type);
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
    Ok(base_event(attrs, Some(PbData::TextData(text))))
}

/// Convert an [`EventMeshMessage`] into the wire CloudEvent for publishing.
pub fn from_event_mesh_message(
    message: &EventMeshMessage,
    config: &GrpcClientConfig,
) -> Result<PbCloudEvent> {
    let protocol_type = EventMeshProtocolType::EventMeshMessage;
    let mut attrs = common_attributes(config, protocol_type);

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

    attrs.insert(ProtocolKey::SUBJECT.into(), attr_str(&message.topic));

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
        content if is_text_content(&data_content_type) => Some(PbData::TextData(content.clone())),
        content if is_proto_content(&data_content_type) => {
            // Match the Java SDK: content bytes are a serialized
            // `google.protobuf.Any` message (produced by `Any.pack(...)` or
            // manual construction, then serialized). Java calls
            // `Any.parseFrom(content.getBytes(UTF_8))` on the producer side
            // and `any.toByteArray()` on the consumer side. We mirror both
            // directions so Rust↔Java `application/protobuf` messages are
            // wire-compatible.
            let any = PbAny::decode(content.as_bytes()).map_err(|e| EventMeshError::Protocol {
                transport: "grpc",
                message: format!(
                    "failed to decode application/protobuf content as google.protobuf.Any: {e}"
                ),
            })?;
            Some(PbData::ProtoData(any))
        }
        content => Some(PbData::BinaryData(content.as_bytes().to_vec())),
    };

    Ok(base_event(attrs, data))
}

/// Build a `CloudEventBatch` from many messages (one RPC, many events).
pub fn from_event_mesh_messages(
    messages: &[EventMeshMessage],
    config: &GrpcClientConfig,
) -> Result<PbCloudEventBatch> {
    let mut events = Vec::with_capacity(messages.len());
    for m in messages {
        events.push(from_event_mesh_message(m, config)?);
    }
    Ok(PbCloudEventBatch { events })
}

/// Decode a delivered CloudEvent back into an [`EventMeshMessage`].
pub fn to_event_mesh_message(cloud_event: &PbCloudEvent) -> Result<EventMeshMessage> {
    let mut props = HashMap::with_capacity(cloud_event.attributes.len());
    for (key, value) in &cloud_event.attributes {
        props.insert(key.clone(), attr_as_str(value));
    }
    let topic = get_subject(cloud_event);
    let biz_seq_no = get_seq_num(cloud_event);
    let unique_id = get_unique_id(cloud_event);
    let content = get_text_data(cloud_event);
    let ttl = get_ttl(cloud_event).parse::<i64>().ok();

    let mut builder = EventMeshMessage::builder()
        .topic(topic)
        .content(content)
        .props(props);
    if !biz_seq_no.is_empty() {
        builder = builder.biz_seq_no(biz_seq_no);
    }
    if !unique_id.is_empty() {
        builder = builder.unique_id(unique_id);
    }
    if let Some(ttl) = ttl {
        builder = builder.ttl_millis(ttl);
    }
    builder.build()
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
        Some(PbData::ProtoData(any)) => {
            // Match Java: `new String(protoData.toByteArray(), UTF_8)`.
            // Re-serializes the `Any` to bytes then decodes as UTF-8 string.
            let mut buf = Vec::with_capacity(any.encoded_len());
            let _ = any.encode(&mut buf);
            String::from_utf8_lossy(&buf).into_owned()
        }
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
/// Mirrors the Java SDK's `SubStreamHandler.buildReplyMessage`:
/// - Tags the message with `SUB_MESSAGE_TYPE = SUBSCRIPTION_REPLY`.
/// - Forces `datacontenttype` to `application/json` so cross-SDK consumers
///   that dispatch on content type decode the reply consistently.
///
/// The reply's data is left intact: EventMesh's `ReplyMessageProcessor`
/// runs `ServiceUtils.validateCloudEventData`, which for text content
/// requires a non-empty `textData` — clearing the data here would make the
/// reply fail validation and never reach `producer.reply()`, breaking
/// request/reply.
pub fn mark_as_reply(cloud_event: &mut PbCloudEvent) {
    cloud_event.attributes.insert(
        ProtocolKey::SUB_MESSAGE_TYPE.into(),
        attr_str(ProtocolKey::SUBSCRIPTION_REPLY),
    );
    cloud_event.attributes.insert(
        ProtocolKey::DATA_CONTENT_TYPE.into(),
        attr_str(DataContentType::JSON),
    );
}

/// (Optional) CloudEvents interop: convert a native [`cloudevents::Event`]
/// to the wire CloudEvent.
#[cfg(feature = "cloud_events")]
pub fn from_cloudevent(
    event: &cloudevents::Event,
    config: &GrpcClientConfig,
) -> Result<PbCloudEvent> {
    use cloudevents::AttributesReader;

    let protocol_type = EventMeshProtocolType::CloudEvents;
    let mut attrs = common_attributes(config, protocol_type);

    let ttl = event
        .extension(ProtocolKey::TTL)
        .map(|v| v.to_string())
        .unwrap_or_else(|| DEFAULT_MESSAGE_TTL.to_string());
    let seq_num = event
        .extension(ProtocolKey::SEQ_NUM)
        .map(|v| v.to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| RandomStringUtils::generate_num(30));
    // UNIQUE_ID: preserve an existing extension, otherwise generate one.
    // Do NOT clobber it with the CE id — the CE id travels in the top-level
    // `id` field (see below) and cross-language consumers dedup on that.
    let unique_id = event
        .extension(ProtocolKey::UNIQUE_ID)
        .map(|v| v.to_string())
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
    // Preserve standard CE attributes the previous code dropped.
    if let Some(t) = event.time() {
        attrs.insert(
            ProtocolKey::TIME.into(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeTimestamp(prost_types::Timestamp {
                    seconds: t.timestamp(),
                    nanos: t.timestamp_subsec_nanos() as i32,
                })),
            },
        );
    }
    if let Some(ds) = event.dataschema() {
        attrs.insert(
            ProtocolKey::DATA_SCHEMA.into(),
            PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeUri(ds.to_string())),
            },
        );
    }
    // Preserve typed extension values (Boolean / Integer) instead of
    // stringifying everything.
    for (k, v) in event.iter_extensions() {
        if attrs.contains_key(k) {
            continue;
        }
        let attr = match v {
            cloudevents::event::ExtensionValue::String(s) => attr_str(s),
            cloudevents::event::ExtensionValue::Boolean(b) => PbCloudEventAttributeValue {
                attr: Some(PbAttr::CeBoolean(*b)),
            },
            cloudevents::event::ExtensionValue::Integer(i) => {
                let value = i32::try_from(*i).map_err(|_| {
                    EventMeshError::InvalidMessage(format!(
                        "CloudEvent integer extension {k:?} value {i} is outside protobuf \
                         int32 range"
                    ))
                })?;
                PbCloudEventAttributeValue {
                    attr: Some(PbAttr::CeInteger(value)),
                }
            }
        };
        attrs.insert(k.to_string(), attr);
    }

    let data = match event.data() {
        Some(cloudevents::Data::String(s)) => Some(PbData::TextData(s.clone())),
        Some(cloudevents::Data::Binary(b)) => Some(PbData::BinaryData(b.clone())),
        Some(cloudevents::Data::Json(j)) => Some(PbData::TextData(j.to_string())),
        None => None,
    };

    Ok(PbCloudEvent {
        id: event.id().to_string(),
        source: event.source().to_string(),
        spec_version: event.specversion().to_string(),
        r#type: event.ty().to_string(),
        attributes: attrs,
        data,
    })
}

/// (Optional) CloudEvents interop: convert the wire CloudEvent back into a
/// native [`cloudevents::Event`].
#[cfg(feature = "cloud_events")]
pub fn to_cloudevent(cloud_event: PbCloudEvent) -> Result<cloudevents::Event> {
    use cloudevents::{Data, EventBuilder, EventBuilderV10};

    let topic = get_subject(&cloud_event);
    // Use the protobuf `id` field (the standard CE id) rather than the
    // EventMesh-specific UNIQUE_ID extension, so cross-language consumers
    // can dedup and correlate correctly.
    let ce_id = if cloud_event.id.is_empty() {
        get_unique_id(&cloud_event)
    } else {
        cloud_event.id.clone()
    };
    let source = if cloud_event.source.is_empty() {
        DEFAULT_SOURCE.to_string()
    } else {
        cloud_event.source.clone()
    };
    let ty = if cloud_event.r#type.is_empty() {
        ProtocolKey::CLOUD_EVENTS_PROTOCOL_NAME.to_string()
    } else {
        cloud_event.r#type.clone()
    };
    let content_type = cloud_event
        .attributes
        .get(ProtocolKey::DATA_CONTENT_TYPE)
        .map(attr_as_str)
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| DataContentType::JSON.to_string());

    let dataschema = cloud_event
        .attributes
        .get(ProtocolKey::DATA_SCHEMA)
        .map(attr_as_str)
        .filter(|s| !s.is_empty());

    let mut builder = EventBuilderV10::new().id(ce_id).source(source).ty(ty);

    // Preserve the native data variant. ProtoData becomes the complete encoded
    // `google.protobuf.Any` (including `type_url`), rather than lossy-decoding
    // only its value bytes as UTF-8.
    let data = match &cloud_event.data {
        Some(PbData::TextData(text)) if is_json_content_type(&content_type) => {
            Some(Data::Json(serde_json::from_str(text)?))
        }
        Some(PbData::TextData(text)) => Some(Data::String(text.clone())),
        Some(PbData::BinaryData(bytes)) => Some(Data::Binary(bytes.clone())),
        Some(PbData::ProtoData(any)) => Some(Data::Binary(any.encode_to_vec())),
        None => None,
    };
    if let Some(data) = data {
        builder = match &dataschema {
            Some(ds) => builder.data_with_schema(content_type.as_str(), ds.as_str(), data),
            None => builder.data(content_type.as_str(), data),
        };
    }

    if !topic.is_empty() {
        builder = builder.subject(topic);
    }

    // Extract the standard CE `time` attribute instead of skipping it.
    if let Some(v) = cloud_event.attributes.get(ProtocolKey::TIME) {
        match &v.attr {
            Some(PbAttr::CeTimestamp(ts)) => {
                if let Some(dt) = chrono::DateTime::from_timestamp(ts.seconds, ts.nanos as u32) {
                    builder = builder.time(dt);
                }
            }
            Some(PbAttr::CeString(s)) => {
                builder = builder.time(s.clone());
            }
            _ => {}
        }
    }

    for (k, v) in cloud_event.attributes {
        if matches!(
            k.as_str(),
            ProtocolKey::GRPC_RESPONSE_CODE
                | ProtocolKey::GRPC_RESPONSE_MESSAGE
                | ProtocolKey::TIME
                | ProtocolKey::DATA_SCHEMA
                | ProtocolKey::DATA_CONTENT_TYPE
        ) {
            continue;
        }
        builder = match v.attr {
            Some(PbAttr::CeBoolean(value)) => builder.extension(k.as_str(), value),
            Some(PbAttr::CeInteger(value)) => builder.extension(k.as_str(), i64::from(value)),
            Some(PbAttr::CeString(value))
            | Some(PbAttr::CeUri(value))
            | Some(PbAttr::CeUriRef(value)) => builder.extension(k.as_str(), value),
            Some(PbAttr::CeBytes(value)) => {
                let value = String::from_utf8(value).map_err(|_| {
                    EventMeshError::InvalidMessage(format!(
                        "CloudEvent byte extension {k:?} cannot be represented by the native \
                         CloudEvents extension model"
                    ))
                })?;
                builder.extension(k.as_str(), value)
            }
            Some(PbAttr::CeTimestamp(value)) => {
                builder.extension(k.as_str(), format!("{}.{}", value.seconds, value.nanos))
            }
            None => builder,
        };
    }
    builder.build().map_err(|e| EventMeshError::Protocol {
        transport: "grpc",
        message: format!("cloudevents build error: {e}"),
    })
}

#[cfg(feature = "cloud_events")]
fn is_json_content_type(content_type: &str) -> bool {
    let media_type = content_type
        .split(';')
        .next()
        .unwrap_or(content_type)
        .trim()
        .to_ascii_lowercase();
    media_type == "application/json" || media_type == "text/json" || media_type.ends_with("+json")
}

/// Build a heartbeat CloudEvent.
pub(crate) fn build_heartbeat(
    config: &GrpcClientConfig,
    items: &[(String, String)],
) -> Result<PbCloudEvent> {
    let mut attrs = common_attributes(config, EventMeshProtocolType::EventMeshMessage);
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
    Ok(base_event(attrs, Some(PbData::TextData(text))))
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
            .build()
            .unwrap();
        let ce = from_event_mesh_message(&msg, &cfg).unwrap();
        assert_eq!(get_subject(&ce), "test-topic");
        assert_eq!(get_seq_num(&ce), "seq-1");
        assert_eq!(get_text_data(&ce), "hello");
        assert_eq!(ce.attributes.get("custom").map(attr_as_str).unwrap(), "val");

        let back = to_event_mesh_message(&ce).unwrap();
        assert_eq!(back.topic(), "test-topic");
        assert_eq!(back.content(), "hello");
    }

    #[test]
    fn decodes_java_compatible_whitespace_content_and_unbounded_ttl() {
        let cfg = cfg();
        let msg = EventMeshMessage::builder()
            .topic("test-topic")
            .content(" \t")
            .prop(ProtocolKey::TTL, "2147483648")
            .build()
            .unwrap();
        let wire = from_event_mesh_message(&msg, &cfg).unwrap();
        let decoded = to_event_mesh_message(&wire).unwrap();
        assert_eq!(decoded.content(), " \t");
        assert_eq!(decoded.get_prop(ProtocolKey::TTL), Some("2147483648"));
        assert_eq!(decoded.ttl_millis(), Some(2_147_483_648));

        let msg = EventMeshMessage::builder()
            .topic("test-topic")
            .content("payload")
            .prop(ProtocolKey::TTL, "java-specific")
            .build()
            .unwrap();
        let wire = from_event_mesh_message(&msg, &cfg).unwrap();
        let decoded = to_event_mesh_message(&wire).unwrap();
        assert_eq!(decoded.get_prop(ProtocolKey::TTL), Some("java-specific"));
        assert_eq!(decoded.ttl_millis(), None);
    }

    #[test]
    fn builds_subscription_event_with_url() {
        let cfg = cfg();
        let items = vec![SubscriptionItem::new(
            "t",
            crate::model::SubscriptionMode::CLUSTERING,
            crate::model::SubscriptionType::ASYNC,
        )];
        let ce = build_subscription_event(
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
        assert!(get_text_data(&ce).contains("\"topic\":\"t\""));
    }

    #[test]
    fn parses_response_code() {
        let mut ce = PbCloudEvent::default();
        ce.attributes
            .insert(ProtocolKey::GRPC_RESPONSE_CODE.into(), attr_str("0"));
        ce.attributes
            .insert(ProtocolKey::GRPC_RESPONSE_MESSAGE.into(), attr_str("ok"));
        let resp = to_response(&ce);
        assert!(resp.is_success());
        assert_eq!(resp.message.as_deref(), Some("ok"));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn cloudevent_roundtrip_preserves_fields() {
        use cloudevents::{event::ExtensionValue, AttributesReader, EventBuilder, EventBuilderV10};

        let original = EventBuilderV10::new()
            .id("my-ce-id")
            .source("http://example.com/src")
            .ty("com.example.event")
            .time("2023-07-13T12:00:00Z")
            .data_with_schema(
                "application/json",
                "http://example.com/schema",
                r#"{"k":"v"}"#.to_string(),
            )
            .extension("bool-ext", true)
            .extension("int-ext", 42i64)
            .extension("str-ext", "hello")
            .build()
            .unwrap();

        let cfg = cfg();
        let pb = from_cloudevent(&original, &cfg).unwrap();

        // id is preserved, not replaced with a random UUID.
        assert_eq!(pb.id, "my-ce-id");

        // time is preserved as CeTimestamp.
        match pb
            .attributes
            .get(ProtocolKey::TIME)
            .and_then(|v| v.attr.as_ref())
        {
            Some(PbAttr::CeTimestamp(_)) => {}
            other => panic!("expected CeTimestamp for time, got {other:?}"),
        }

        // dataschema is preserved as CeUri.
        match pb
            .attributes
            .get(ProtocolKey::DATA_SCHEMA)
            .and_then(|v| v.attr.as_ref())
        {
            Some(PbAttr::CeUri(s)) => assert_eq!(s, "http://example.com/schema"),
            other => panic!("expected CeUri for dataschema, got {other:?}"),
        }

        // Typed extensions preserve their wire types.
        match pb.attributes.get("bool-ext").and_then(|v| v.attr.as_ref()) {
            Some(PbAttr::CeBoolean(true)) => {}
            other => panic!("expected CeBoolean(true) for bool-ext, got {other:?}"),
        }
        match pb.attributes.get("int-ext").and_then(|v| v.attr.as_ref()) {
            Some(PbAttr::CeInteger(42)) => {}
            other => panic!("expected CeInteger(42) for int-ext, got {other:?}"),
        }

        // Round-trip back to a native CE.
        let back = to_cloudevent(pb).unwrap();
        assert_eq!(back.id(), "my-ce-id");
        assert_eq!(back.ty(), "com.example.event");
        assert!(back.time().is_some());
        assert!(back.dataschema().is_some());
        assert!(matches!(
            back.extension("bool-ext"),
            Some(ExtensionValue::Boolean(true))
        ));
        assert!(matches!(
            back.extension("int-ext"),
            Some(ExtensionValue::Integer(42))
        ));
        assert!(matches!(
            back.data(),
            Some(cloudevents::Data::Json(value)) if value == &serde_json::json!({"k": "v"})
        ));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn rejects_cloudevent_integer_extensions_outside_int32_range() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("id")
            .source("/")
            .ty("test")
            .extension("too-large", i64::from(i32::MAX) + 1)
            .build()
            .unwrap();

        assert!(matches!(
            from_cloudevent(&event, &cfg()),
            Err(EventMeshError::InvalidMessage(message))
                if message.contains("too-large") && message.contains("int32")
        ));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn to_cloudevent_leaves_missing_data_absent() {
        use cloudevents::AttributesReader;

        let pb = PbCloudEvent {
            id: "empty-id".into(),
            source: "/".into(),
            spec_version: "1.0".into(),
            r#type: "com.example.empty".into(),
            ..Default::default()
        };

        assert!(to_cloudevent(pb).unwrap().data().is_none());
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn to_cloudevent_preserves_complete_protobuf_any_as_binary() {
        use cloudevents::AttributesReader;

        let any = PbAny {
            type_url: "type.googleapis.com/example.Payload".into(),
            value: vec![0xff, 0x00, 0x80, 0x01],
        };
        let expected = any.encode_to_vec();
        let mut pb = PbCloudEvent {
            id: "proto-id".into(),
            source: "/".into(),
            spec_version: "1.0".into(),
            r#type: "com.example.proto".into(),
            data: Some(PbData::ProtoData(any)),
            ..Default::default()
        };
        pb.attributes.insert(
            ProtocolKey::DATA_CONTENT_TYPE.into(),
            attr_str(DataContentType::PROTOBUF),
        );

        match to_cloudevent(pb).unwrap().data() {
            Some(cloudevents::Data::Binary(bytes)) => assert_eq!(bytes, &expected),
            other => panic!("expected encoded protobuf Any bytes, got {other:?}"),
        }
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn to_cloudevent_preserves_binary_data() {
        use cloudevents::AttributesReader;
        let mut pb = PbCloudEvent {
            id: "bin-id".into(),
            source: "/".into(),
            spec_version: "1.0".into(),
            r#type: "com.example.binary".into(),
            ..Default::default()
        };
        pb.attributes.insert(
            ProtocolKey::DATA_CONTENT_TYPE.into(),
            attr_str("application/octet-stream"),
        );
        pb.data = Some(PbData::BinaryData(vec![0, 1, 2, 3]));

        let ce = to_cloudevent(pb).unwrap();
        assert_eq!(ce.ty(), "com.example.binary");
        match ce.data() {
            Some(cloudevents::Data::Binary(b)) => assert_eq!(b, &[0, 1, 2, 3]),
            other => panic!("expected binary data, got {other:?}"),
        }
    }
}
