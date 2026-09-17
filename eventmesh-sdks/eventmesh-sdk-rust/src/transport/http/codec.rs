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

//! Codec for the EventMesh HTTP wire format.
//!
//! All HTTP request bodies are `application/x-www-form-urlencoded`. Message
//! payloads are serialized as JSON strings and placed in the `content` field.
//! This mirrors the Java SDK's `EventMeshMessageProducer` /
//! `CloudEventProducer` / `EventMeshHttpConsumer` wire format.
//!
//! # Building a custom webhook endpoint
//!
//! Besides the built-in [`WebhookServer`](crate::transport::http::WebhookServer),
//! you can host your own HTTP endpoint (axum, actix, plain hyper, …) and decode
//! runtime pushes with these framework-agnostic helpers:
//!
//! - [`parse_push_body`] — parse the form-urlencoded push body into a
//!   [`PushMessageRequestBody`].
//! - [`PushMessageRequestBody::to_message`] — decode it with the request
//!   headers into a [`Message`], preserving its EventMesh or CloudEvents dialect.
//! - [`WebhookReply`] — the JSON acknowledgment the runtime expects
//!   ([`WebhookReply::ok()`] returns `retCode: 1`; the runtime also accepts
//!   `retCode: 0`. A non-zero code other than 1 requests retry).
//!
//! See the `http_consumer_custom` example for a complete, runnable version.

use std::collections::HashMap;

use http::HeaderMap;
use serde::{Deserialize, Serialize};

use crate::common::status_code::RequestCode;
use crate::common::util::RandomStringUtils;
#[cfg(test)]
use crate::common::ProtocolKey;
use crate::common::DEFAULT_MESSAGE_TTL;
use crate::config::{Credentials, Identity};
use crate::error::{EventMeshError, Result};
use crate::message::Message;
use crate::model::{EventMeshMessage, EventMeshProtocolType, PublishResponse};
use crate::subscription::Subscription;

/// Default protocol version string sent in the `version` and
/// `protocolversion` headers.
///
/// Must be `"1.0"` (not `"V1.0"`): the runtime resolves it via
/// `ProtocolVersion.get("1.0")` and compares `protocolversion` against
/// CloudEvents `SpecVersion.V1` (`"1.0"`).
const PROTOCOL_VERSION: &str = "1.0";

/// Runtime endpoint paths (mirrors `RequestURI.java`).
///
/// The EventMesh HTTP server has two routing mechanisms, checked in this order:
///
/// 1. **Path-based** (`HandlerService`): requests whose URI *starts-with* a
///    registered path are dispatched to that path's processor and the code
///    header is never consulted. `/eventmesh/subscribe/local` and
///    `/eventmesh/unsubscribe/local` are registered this way
///    (`LocalSubscribeEventProcessor` / `LocalUnSubscribeEventProcessor`).
///    These path handlers parse the body as JSON: a form-urlencoded `topic`
///    field becomes a string value that cannot be deserialized as
///    `List<Subscription>`, so **form-based subscribe/unsubscribe must
///    avoid these paths**.
/// 2. **Code-header-based** (`httpRequestProcessorTable`): if no path matches,
///    the runtime reads the `code` header and looks up the processor by request
///    code (SUBSCRIBE, UNSUBSCRIBE, MSG_SEND_ASYNC, HEARTBEAT, …).
///
/// Because this SDK sends `application/x-www-form-urlencoded` bodies (matching
/// the Java SDK), **all** operations — publish, subscribe, unsubscribe, and
/// heartbeat — use a path such as [`uri::ROOT`] so the request falls through to code-header
/// dispatch. Posting to a path-based handler with a form body breaks body
/// decoding on the runtime side.
pub mod uri {
    /// Root path — matches no path-based handler, forcing code-header routing.
    pub const ROOT: &str = "/";
    /// Heartbeat — no dedicated path handler; any non-matching path works.
    pub const HEARTBEAT: &str = "/eventmesh/heartbeat";
}

/// The JSON reply body returned by the EventMesh runtime for publish /
/// subscribe / heartbeat operations.
///
/// Mirrors `org.apache.eventmesh.common.protocol.http.body.Body`.
#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct EventMeshRetObj {
    #[serde(rename = "retCode")]
    pub ret_code: i64,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "retMsg")]
    pub ret_msg: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "resTime")]
    pub res_time: Option<i64>,
}

impl From<EventMeshRetObj> for PublishResponse {
    fn from(obj: EventMeshRetObj) -> Self {
        PublishResponse::new(Some(obj.ret_code), obj.ret_msg, obj.res_time)
    }
}

/// The JSON body returned by a webhook consumer to acknowledge a pushed
/// message. The runtime reads `retCode` (`ProtocolKey.RETCODE`) from this
/// JSON to determine delivery success, so the field names **must** be
/// camelCase.
#[derive(Debug, Clone, Serialize)]
pub struct WebhookReply {
    /// EventMesh client acknowledgement code.
    #[serde(rename = "retCode")]
    pub ret_code: i32,
    /// Optional acknowledgement description.
    #[serde(skip_serializing_if = "Option::is_none", rename = "retMsg")]
    pub ret_msg: Option<String>,
}

impl WebhookReply {
    /// Return a successful-delivery acknowledgement.
    pub fn ok() -> Self {
        Self {
            ret_code: crate::common::status_code::ClientRetCode::Ok as i32,
            ret_msg: Some("OK".into()),
        }
    }

    /// Ask EventMesh to retry delivery with an explanatory message.
    pub fn retry(msg: impl Into<String>) -> Self {
        Self {
            ret_code: crate::common::status_code::ClientRetCode::Retry as i32,
            ret_msg: Some(msg.into()),
        }
    }
}

/// The form-urlencoded body pushed by the runtime to a consumer's webhook URL.
///
/// Mirrors `PushMessageRequestBody`. The `content` and `extFields` fields are
/// themselves JSON strings embedded inside the form body.
#[derive(Debug, Clone, Deserialize)]
pub struct PushMessageRequestBody {
    /// Message payload (typically a JSON-serialized CloudEvent or EventMeshMessage).
    pub content: String,
    /// Optional business sequence number.
    #[serde(default)]
    pub bizseqno: Option<String>,
    /// Optional application-level unique ID.
    #[serde(default, rename = "uniqueId")]
    pub unique_id: Option<String>,
    /// Optional runtime-generated random number.
    #[serde(default, rename = "randomNo")]
    pub random_no: Option<String>,
    /// Optional destination topic.
    #[serde(default)]
    pub topic: Option<String>,
    /// JSON-encoded `Map<String,String>` of extension attributes.
    #[serde(default, rename = "extFields")]
    pub extfields: Option<String>,
}

impl PushMessageRequestBody {
    /// Decode a webhook delivery into the public [`Message`] envelope.
    ///
    /// Uses the `protocoltype` HTTP header, falling back to `extFields` when
    /// the header is absent. If neither declares a protocol, the delivery is
    /// treated as a native EventMesh message. This is the same decoder used
    /// by the built-in webhook server.
    ///
    /// CloudEvents are preserved when the `cloud_events` feature is enabled;
    /// otherwise a CloudEvent delivery returns [`crate::Error::Unsupported`].
    /// An invalid `protocoltype` header, malformed `extFields`, conflicting
    /// protocol sources, and unknown protocols return [`crate::Error::Protocol`]. Invalid
    /// CloudEvents JSON returns [`crate::Error::Codec`].
    ///
    /// ```
    /// use eventmesh::http::codec::parse_push_body;
    /// use http::HeaderMap;
    ///
    /// let headers = HeaderMap::new();
    /// let push = parse_push_body("topic=orders&content=created")?;
    /// let message = push.to_message(&headers)?;
    /// assert_eq!(message.as_event_mesh().unwrap().content(), "created");
    /// # Ok::<(), eventmesh::Error>(())
    /// ```
    pub fn to_message(&self, headers: &HeaderMap) -> Result<Message> {
        let header_protocol_type = headers
            .get("protocoltype")
            .map(|value| {
                value.to_str().map_err(|error| EventMeshError::Protocol {
                    transport: "http",
                    message: format!("invalid protocoltype header: {error}"),
                })
            })
            .transpose()?;
        let extension_protocol_type = self
            .extfields
            .as_deref()
            .filter(|fields| !fields.trim().is_empty())
            .map(|fields| {
                serde_json::from_str::<HashMap<String, String>>(fields).map_err(|error| {
                    EventMeshError::Protocol {
                        transport: "http",
                        message: format!("failed to parse extFields JSON: {error}"),
                    }
                })
            })
            .transpose()?
            .and_then(|fields| fields.get("protocoltype").cloned());

        if let (Some(header), Some(extension)) =
            (header_protocol_type, extension_protocol_type.as_deref())
        {
            if header != extension {
                return Err(EventMeshError::Protocol {
                    transport: "http",
                    message: format!(
                        "conflicting protocoltype values: header={header:?}, \
                         extFields={extension:?}"
                    ),
                });
            }
        }

        // Runtime HTTP pushes created from an HttpCommand do not carry the
        // original `protocoltype` as an HTTP header. They do retain all
        // CloudEvent extensions in the form-level `extFields`, including
        // `protocoltype`, so consult that field before applying the legacy
        // native-message default.
        let protocol_type = header_protocol_type
            .or(extension_protocol_type.as_deref())
            .unwrap_or(EventMeshProtocolType::EventMeshMessage.as_str());

        if protocol_type == EventMeshProtocolType::CloudEvents.as_str() {
            #[cfg(feature = "cloud_events")]
            {
                return serde_json::from_str(&self.content)
                    .map(Message::CloudEvent)
                    .map_err(EventMeshError::Codec);
            }

            #[cfg(not(feature = "cloud_events"))]
            return Err(EventMeshError::Unsupported(
                "received a CloudEvent without the 'cloud_events' feature enabled".into(),
            ));
        }

        if protocol_type != EventMeshProtocolType::EventMeshMessage.as_str() {
            return Err(EventMeshError::Protocol {
                transport: "http",
                message: format!("unsupported protocoltype {protocol_type:?}"),
            });
        }

        let mut message = self.to_event_mesh_message()?;
        if let Some(context) = message.delivery_context.as_mut() {
            for (key, value) in headers {
                if let Ok(value) = value.to_str() {
                    context.insert_missing(key.as_str(), value.to_string());
                }
            }
        }
        Ok(Message::EventMesh(message))
    }

    /// Decode the pushed body into an [`EventMeshMessage`].
    ///
    /// This explicitly selects the native dialect. Use [`Self::to_message`]
    /// to detect the dialect from the request headers and body metadata.
    ///
    /// The `content` field is **always** treated as the business payload —
    /// the Runtime puts the original user payload there, not a serialized
    /// `EventMeshMessage`.  Message metadata (`topic`, `bizseqno`,
    /// `uniqueId`, `extFields`) is taken from the form-level fields. Wire TTL
    /// is extracted into the dedicated TTL field and excluded from properties;
    /// a present value that cannot be parsed as i64 is rejected.
    pub fn to_event_mesh_message(&self) -> Result<EventMeshMessage> {
        let topic = self
            .topic
            .clone()
            .ok_or_else(|| EventMeshError::InvalidMessage("topic is required".into()))?;
        let mut props = HashMap::new();
        if let Some(ext) = &self.extfields {
            let trimmed = ext.trim();
            if !trimmed.is_empty() {
                props = serde_json::from_str(trimmed).map_err(|e| EventMeshError::Protocol {
                    transport: "http",
                    message: format!("failed to parse extFields JSON: {e}"),
                })?;
            }
        }

        let mut builder = EventMeshMessage::builder()
            .topic(topic)
            .content(self.content.clone());
        if let Some(value) = &self.bizseqno {
            builder = builder.biz_seq_no(value.clone());
        }
        if let Some(value) = &self.unique_id {
            builder = builder.unique_id(value.clone());
        }
        crate::transport::decode_native_message(builder.build()?, props)
    }
}

// ---------- Encoding helpers (producer side) ----------

/// Build the HTTP headers for a request.
///
/// Identity fields (`env`, `idc`, `sys`, `pid`, `ip`, `username`, `passwd`,
/// `language`, and the optional `token`) are sent as HTTP headers, mirroring
/// the Java SDK's `EventMeshMessageProducer.buildCommonPostParam` /
/// `EventMeshHttpConsumer.buildCommonRequestParam` and the runtime's
/// `ProtocolKey.ClientInstanceKey` handling. The runtime reads identity
/// exclusively from headers — never from the form body.
pub fn build_headers(
    code: i32,
    protocol_type: EventMeshProtocolType,
    identity: &Identity,
    credentials: &Credentials,
) -> Vec<(&'static str, String)> {
    let mut headers = vec![
        ("code", code.to_string()),
        ("env", identity.env().to_string()),
        ("idc", identity.idc().to_string()),
        ("sys", identity.system().to_string()),
        ("pid", identity.process_id().to_string()),
        ("ip", identity.ip().to_string()),
        ("username", credentials.username().to_string()),
        ("passwd", credentials.password().to_string()),
        ("language", identity.language().to_string()),
        ("version", PROTOCOL_VERSION.to_string()),
        ("protocoltype", protocol_type.as_str().to_string()),
        ("protocolversion", PROTOCOL_VERSION.to_string()),
        ("protocoldesc", "http".to_string()),
    ];
    if let Some(token) = credentials.token() {
        headers.push(("token", token.to_string()));
    }
    headers
}

/// Encode an [`EventMeshMessage`] into form-urlencoded body fields for a
/// publish request.
///
/// Identity fields are NOT included here — they are sent as HTTP headers via
/// [`build_headers`]. Only the message-specific fields (`producergroup`,
/// `topic`, `content`, `ttl`, `bizseqno`, `uniqueid`) go in the body, matching
/// `SendMessageRequestBody` on the Java side.
pub fn encode_publish(msg: &EventMeshMessage, producer_group: &str) -> Vec<(String, String)> {
    let mut fields: Vec<(String, String)> = Vec::new();
    fields.push(("producergroup".into(), producer_group.to_string()));
    fields.push(("topic".into(), msg.topic.clone()));
    fields.push(("content".into(), msg.content.clone()));
    // Always emit a `ttl` form field, falling back to `DEFAULT_MESSAGE_TTL`
    // when the caller did not set one. The runtime's
    // `SendSyncMessageProcessor` rejects a blank TTL with
    // `EVENTMESH_PROTOCOL_BODY_ERR` before any defaulting (unlike the async
    // processor, which patches in a default after validation), so request-reply
    // calls would fail whenever `EventMeshMessage::ttl` is unset. This mirrors
    // the gRPC codec (and the Java gRPC SDK's
    // `EventMeshCloudEventBuilder`, which falls back to
    // `Constants.DEFAULT_EVENTMESH_MESSAGE_TTL`).
    //
    // NOTE: this intentionally diverges from the Java HTTP SDK's
    // `EventMeshMessageProducer.buildCommonPostParam`, which does
    // `addBody(TTL, message.getProp("ttl"))` with no fallback — emitting a
    // blank `ttl=` when the prop is unset and hitting the same runtime
    // rejection on the sync path. Defaulting here keeps the Rust HTTP
    // transport consistent with its own gRPC transport.
    let ttl = msg
        .ttl
        .map(|t| t.to_string())
        .unwrap_or_else(|| DEFAULT_MESSAGE_TTL.to_string());
    fields.push(("ttl".into(), ttl));
    // The runtime's code-header publish processors (MSG_SEND_ASYNC /
    // MSG_SEND_SYNC) require non-blank `bizseqno` and `uniqueid` and reject
    // with EVENTMESH_PROTOCOL_BODY_ERR when either is missing. Mirror the gRPC
    // codec and the Java CloudEventProducer by auto-generating them when the
    // caller did not supply values.
    let biz = msg
        .biz_seq_no
        .as_deref()
        .filter(|s| !s.is_empty())
        .map(str::to_owned)
        .unwrap_or_else(|| RandomStringUtils::generate_num(30));
    let uid = msg
        .unique_id
        .as_deref()
        .filter(|s| !s.is_empty())
        .map(str::to_owned)
        .unwrap_or_else(|| RandomStringUtils::generate_num(30));
    fields.push(("bizseqno".into(), biz));
    fields.push(("uniqueid".into(), uid));
    // Only business extensions and explicitly mapped payload metadata enter
    // extFields. Java merges this map after the current request's headers.
    let mut extensions: HashMap<&str, &str> = msg
        .props
        .iter()
        .filter(|(key, _)| !crate::model::delivery::is_reserved_property(key))
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect();
    if let Some(content_type) = msg.data_content_type() {
        extensions.insert("datacontenttype", content_type);
    }
    if !extensions.is_empty() {
        fields.push((
            "extFields".into(),
            serde_json::to_string(&extensions).unwrap_or_default(),
        ));
    }
    fields
}

/// Encode subscribe body fields.
pub fn encode_subscribe(
    items: &[Subscription],
    url: &str,
    consumer_group: &str,
) -> Vec<(String, String)> {
    vec![
        ("consumerGroup".into(), consumer_group.to_string()),
        (
            "topic".into(),
            serde_json::to_string(items).unwrap_or_default(),
        ),
        ("url".into(), url.to_string()),
    ]
}

/// Encode unsubscribe body fields.
pub fn encode_unsubscribe(
    topics: &[String],
    url: &str,
    consumer_group: &str,
) -> Vec<(String, String)> {
    vec![
        ("consumerGroup".into(), consumer_group.to_string()),
        (
            "topic".into(),
            serde_json::to_string(topics).unwrap_or_default(),
        ),
        ("url".into(), url.to_string()),
    ]
}

/// Encode heartbeat body fields.
pub fn encode_heartbeat(items: &[(String, String)], consumer_group: &str) -> Vec<(String, String)> {
    use crate::model::HeartbeatItem;

    let entities: Vec<HeartbeatItem> = items
        .iter()
        .map(|(topic, url)| HeartbeatItem::new(topic.clone(), url.clone()))
        .collect();
    vec![
        ("consumerGroup".into(), consumer_group.to_string()),
        ("clientType".into(), "2".into()), // SUB
        (
            "heartbeatEntities".into(),
            serde_json::to_string(&entities).unwrap_or_default(),
        ),
    ]
}

/// Parse a `EventMeshRetObj` from the response body text, returning a
/// [`PublishResponse`].
pub fn parse_response(body: &str) -> Result<PublishResponse> {
    let obj: EventMeshRetObj = serde_json::from_str(body)?;
    Ok(obj.into())
}

/// Form-encode a list of `(key, value)` pairs into a URL-encoded body string.
#[cfg(test)]
pub fn form_encode(fields: &[(String, String)]) -> String {
    serde_urlencoded::to_string(fields).unwrap_or_default()
}

/// Request code for the given operation.
pub fn publish_code() -> i32 {
    RequestCode::MSG_SEND_ASYNC
}

pub fn subscribe_code() -> i32 {
    RequestCode::SUBSCRIBE
}

pub fn unsubscribe_code() -> i32 {
    RequestCode::UNSUBSCRIBE
}

pub fn heartbeat_code() -> i32 {
    RequestCode::HEARTBEAT
}

/// Decode a webhook push body (form-urlencoded) into fields.
pub fn parse_push_body(body: &str) -> Result<PushMessageRequestBody> {
    serde_urlencoded::from_str(body).map_err(|e| EventMeshError::Protocol {
        transport: "http",
        message: format!("form decode error: {e}"),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::{Credentials, Identity};

    fn identity() -> Identity {
        Identity::default()
    }

    fn credentials() -> Credentials {
        Credentials::new()
    }

    #[test]
    fn encode_publish_round_trip() {
        let msg = EventMeshMessage::builder()
            .topic("test-topic")
            .content("hello")
            .biz_seq_no("seq-1")
            .build()
            .unwrap();
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let encoded = form_encode(&fields);
        assert!(encoded.contains("topic=test-topic"));
        assert!(encoded.contains("bizseqno=seq-1"));
        // content should be the raw content string, NOT the whole message
        // serialized as JSON (matches the Java SDK's EventMeshMessageProducer).
        assert!(encoded.contains("content=hello"));
        assert!(!encoded.contains("biz_seq_no"));
    }

    #[test]
    fn encode_publish_auto_generates_ids_when_missing() {
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .build()
            .unwrap();
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let map: HashMap<String, String> = fields.into_iter().collect();
        let biz = map
            .get("bizseqno")
            .expect("bizseqno should be auto-generated");
        let uid = map
            .get("uniqueid")
            .expect("uniqueid should be auto-generated");
        assert!(!biz.is_empty());
        assert!(!uid.is_empty());
        assert!(biz.chars().all(|c| c.is_ascii_digit()));
        assert!(uid.chars().all(|c| c.is_ascii_digit()));
    }

    #[test]
    fn encode_publish_keeps_caller_supplied_ids() {
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .biz_seq_no("my-seq")
            .unique_id("my-uid")
            .build()
            .unwrap();
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let map: HashMap<String, String> = fields.into_iter().collect();
        assert_eq!(map.get("bizseqno"), Some(&"my-seq".to_string()));
        assert_eq!(map.get("uniqueid"), Some(&"my-uid".to_string()));
    }

    #[test]
    fn encode_publish_keeps_identity_out_of_body() {
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .build()
            .unwrap();
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let encoded = form_encode(&fields);
        // Identity must be in headers, not body.
        assert!(!encoded.contains("env="));
        assert!(!encoded.contains("username="));
        assert!(!encoded.contains("passwd="));
        assert!(!encoded.contains("pid="));
    }

    #[test]
    fn encode_publish_includes_ext_fields() {
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .prop("key1", "val1")
            .prop("key2", "val2")
            .build()
            .unwrap();
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let map: HashMap<String, String> = fields.into_iter().collect();
        let ext = map.get("extFields").expect("extFields should be present");
        let props: HashMap<String, String> = serde_json::from_str(ext).unwrap();
        assert_eq!(props.get("key1"), Some(&"val1".to_string()));
        assert_eq!(props.get("key2"), Some(&"val2".to_string()));
    }

    #[test]
    fn encode_publish_filters_reserved_keys_from_ext_fields() {
        let mut msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .ttl_millis(7_000)
            .biz_seq_no("my-seq")
            .unique_id("my-uid")
            .prop("key1", "val1")
            .build()
            .unwrap();
        // Exercise the encoder guard against malformed crate-private state.
        msg.props.insert("ttl".into(), "99000".into());
        msg.props.insert("bizseqno".into(), "stale-seq".into());
        msg.props.insert("uniqueid".into(), "stale-uid".into());
        msg.props.insert("topic".into(), "stale-topic".into());
        msg.props.insert("content".into(), "stale-content".into());
        msg.props
            .insert("producergroup".into(), "stale-group".into());
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let map: HashMap<String, String> = fields.into_iter().collect();
        let ext = map.get("extFields").expect("extFields should be present");
        let props: HashMap<String, String> = serde_json::from_str(ext).unwrap();
        // Non-reserved keys survive.
        assert_eq!(props.get("key1"), Some(&"val1".to_string()));
        // Reserved keys are filtered out — they are already emitted as typed
        // form fields and must not reverse-overwrite via extFields.
        assert!(!props.contains_key("ttl"));
        assert!(!props.contains_key("bizseqno"));
        assert!(!props.contains_key("uniqueid"));
        assert!(!props.contains_key("topic"));
        assert!(!props.contains_key("content"));
        assert!(!props.contains_key("producergroup"));
    }

    #[test]
    fn encode_publish_omits_ext_fields_when_all_props_filtered() {
        let mut msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .build()
            .unwrap();
        // Exercise the encoder guard against malformed crate-private state.
        msg.props.insert("ttl".into(), "99000".into());
        msg.props.insert("bizseqno".into(), "stale".into());
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        // All props were reserved keys → no extFields field should be emitted.
        assert!(!fields.iter().any(|(k, _)| k == "extFields"));
    }

    #[test]
    fn encode_publish_omits_ext_fields_when_empty() {
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .build()
            .unwrap();
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        assert!(!fields.iter().any(|(k, _)| k == "extFields"));
    }

    #[test]
    fn encode_publish_defaults_ttl_when_unset() {
        // The runtime's SendSyncMessageProcessor rejects a blank TTL with
        // EVENTMESH_PROTOCOL_BODY_ERR, so encode_publish must always emit one.
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .build()
            .unwrap();
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let map: HashMap<String, String> = fields.into_iter().collect();
        let ttl = map.get("ttl").expect("ttl should always be present");
        assert_eq!(ttl, &DEFAULT_MESSAGE_TTL.to_string());
    }

    #[test]
    fn encode_publish_keeps_caller_supplied_ttl() {
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .ttl_millis(30_000)
            .build()
            .unwrap();
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let map: HashMap<String, String> = fields.into_iter().collect();
        assert_eq!(map.get("ttl"), Some(&"30000".to_string()));
    }

    #[test]
    fn encode_publish_ignores_ttl_prop_when_field_unset() {
        // Generic properties never configure native-message TTL.
        let mut msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .build()
            .unwrap();
        // Exercise the encoder guard against malformed crate-private state.
        msg.props.insert(ProtocolKey::TTL.into(), "99000".into());
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let map: HashMap<String, String> = fields.into_iter().collect();
        assert_eq!(map.get("ttl"), Some(&DEFAULT_MESSAGE_TTL.to_string()));
    }

    #[test]
    fn encode_publish_typed_ttl_takes_precedence_over_prop() {
        let mut msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .ttl_millis(7_000)
            .build()
            .unwrap();
        // Exercise the encoder guard against malformed crate-private state.
        msg.props.insert(ProtocolKey::TTL.into(), "99000".into());
        let fields = encode_publish(&msg, "DefaultProducerGroup");
        let map: HashMap<String, String> = fields.into_iter().collect();
        assert_eq!(map.get("ttl"), Some(&"7000".to_string()));
    }

    #[test]
    fn build_headers_carries_identity_and_token() {
        let headers = build_headers(
            RequestCode::MSG_SEND_ASYNC,
            EventMeshProtocolType::EventMeshMessage,
            &identity(),
            &Credentials::new().with_token("my-jwt"),
        );
        let header_str: String = headers
            .iter()
            .map(|(k, v)| format!("{k}={v}"))
            .collect::<Vec<_>>()
            .join("\n");
        assert!(header_str.contains("env="));
        assert!(header_str.contains("username="));
        assert!(header_str.contains("passwd="));
        assert!(header_str.contains("pid="));
        assert!(header_str.contains("token=my-jwt"));
    }

    #[test]
    fn build_headers_omits_token_when_unset() {
        let headers = build_headers(
            RequestCode::MSG_SEND_ASYNC,
            EventMeshProtocolType::EventMeshMessage,
            &identity(),
            &credentials(),
        );
        assert!(!headers.iter().any(|(k, _)| *k == "token"));
    }

    #[test]
    fn parse_response_success() {
        let body = r#"{"retCode":0,"retMsg":"success","resTime":42}"#;
        let resp = parse_response(body).unwrap();
        assert!(resp.is_success());
        assert_eq!(resp.time, Some(42));
    }

    #[test]
    fn parse_response_missing_ret_code_is_error() {
        let body = r#"{"retMsg":"oops"}"#;
        assert!(parse_response(body).is_err());
    }

    #[test]
    fn parse_response_empty_object_is_error() {
        assert!(parse_response("{}").is_err());
    }

    #[test]
    fn parse_response_non_numeric_ret_code_is_error() {
        let body = r#"{"retCode":"abc"}"#;
        assert!(parse_response(body).is_err());
    }

    #[test]
    fn parse_push_body_form_urlencoded() {
        let body = "content=hello&topic=test-topic&bizseqno=seq1";
        let parsed = parse_push_body(body).unwrap();
        assert_eq!(parsed.content, "hello");
        assert_eq!(parsed.topic.as_deref(), Some("test-topic"));
    }

    #[test]
    fn push_body_to_message_with_json_content() {
        // The Runtime puts the *business payload* in `content`, not a
        // serialized EventMeshMessage.  A JSON payload that happens to
        // contain a `create_time` field must NOT be misinterpreted as a
        // full EventMeshMessage — it must be preserved verbatim and the
        // form-level metadata (topic, bizseqno, extFields) must be applied.
        let business_json = r#"{"create_time":123,"order_id":"x"}"#;
        let body = form_encode(&[
            ("content".to_string(), business_json.to_string()),
            ("topic".to_string(), "test-topic".to_string()),
            ("bizseqno".to_string(), "seq-1".to_string()),
        ]);
        let parsed = parse_push_body(&body).unwrap();
        let msg = parsed.to_event_mesh_message().unwrap();
        assert_eq!(msg.content(), business_json);
        assert_eq!(msg.topic(), "test-topic");
        assert_eq!(msg.biz_seq_no.as_deref(), Some("seq-1"));
    }

    #[test]
    fn push_body_preserves_empty_content_and_transport_specific_ttl() {
        let body = form_encode(&[
            ("content".to_string(), String::new()),
            ("topic".to_string(), "test-topic".to_string()),
            (
                "extFields".to_string(),
                r#"{"ttl":"2147483648","custom":"value"}"#.to_string(),
            ),
        ]);
        let msg = parse_push_body(&body)
            .and_then(|body| body.to_event_mesh_message())
            .unwrap();
        assert_eq!(msg.content(), "");
        assert_eq!(msg.ttl_millis(), Some(2_147_483_648));
        assert_eq!(msg.get_prop(ProtocolKey::TTL), None);
        assert_eq!(msg.get_prop("custom"), Some("value"));
    }

    #[test]
    fn push_body_decodes_ext_fields_camel_case() {
        // The runtime sends extFields (camelCase) as a JSON-encoded map string.
        let props_json = r#"{"prop1":"val1","prop2":"val2"}"#;
        let body = form_encode(&[
            ("content".to_string(), "hello".to_string()),
            ("topic".to_string(), "orders".to_string()),
            ("extFields".to_string(), props_json.to_string()),
        ]);
        let parsed = parse_push_body(&body).unwrap();
        assert_eq!(parsed.extfields.as_deref(), Some(props_json));
        let msg = parsed.to_event_mesh_message().unwrap();
        assert_eq!(msg.get_prop("prop1"), Some("val1"));
        assert_eq!(msg.get_prop("prop2"), Some("val2"));
    }

    #[test]
    fn push_body_without_ext_fields() {
        let body = "content=hello&topic=t";
        let parsed = parse_push_body(body).unwrap();
        assert!(parsed.extfields.is_none());
        let msg = parsed.to_event_mesh_message().unwrap();
        assert!(msg.props.is_empty());
    }

    #[test]
    fn push_body_invalid_ext_fields_returns_error() {
        let body = form_encode(&[
            ("content".to_string(), "hello".to_string()),
            ("extFields".to_string(), "not valid json".to_string()),
        ]);
        let parsed = parse_push_body(&body).unwrap();
        assert!(parsed.to_event_mesh_message().is_err());
    }

    #[test]
    fn form_encode_special_chars() {
        let fields = vec![("key".to_string(), "val ue".to_string())];
        let encoded = form_encode(&fields);
        // serde_urlencoded encodes spaces as '+'.
        assert!(encoded.contains("key=val+ue"));
    }
}
