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
//! - [`PushMessageRequestBody::to_event_mesh_message`] — decode it into an
//!   [`EventMeshMessage`].
//! - [`WebhookReply`] — the JSON acknowledgment the runtime expects
//!   ([`WebhookReply::ok()`] returns `retCode: 1`; the runtime also accepts
//!   `retCode: 0`. A non-zero code other than 1 requests retry).
//!
//! ```ignore
//! # use eventmesh::http::codec::{parse_push_body, WebhookReply};
//! # use eventmesh::MessageListener;
//! # use eventmesh::model::EventMeshMessage;
//! # use axum::{extract::State, response::IntoResponse, Json};
//! # use bytes::Bytes;
//! # use std::sync::Arc;
//! # struct MyListener;
//! # impl MessageListener for MyListener {
//! #     type Message = EventMeshMessage;
//! #     async fn handle(&self, _: Self::Message) -> Option<Self::Message> { None }
//! # }
//! // Axum handler written by the user — no SDK handler type involved.
//! async fn webhook(
//!     State(listener): State<Arc<MyListener>>,
//!     body: Bytes,
//! ) -> impl IntoResponse {
//!     let text = match std::str::from_utf8(&body) {
//!         Ok(s) => s,
//!         Err(_) => return Json(WebhookReply::retry("invalid UTF-8")),
//!     };
//!     match parse_push_body(text).and_then(|p| p.to_event_mesh_message()) {
//!         Ok(msg) => {
//!             listener.handle(msg).await;
//!             Json(WebhookReply::ok())
//!         }
//!         Err(_) => Json(WebhookReply::retry("decode error")),
//!     }
//! }
//! ```
//!
//! See the `http_consumer_custom` example for a complete, runnable version.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::common::status_code::RequestCode;
use crate::common::util::RandomStringUtils;
use crate::common::{ProtocolKey, DEFAULT_MESSAGE_TTL};
use crate::config::ClientIdentity;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, EventMeshProtocolType, PublishResponse, SubscriptionItem};

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
///    `List<SubscriptionItem>`, so **form-based subscribe/unsubscribe must
///    avoid these paths**.
/// 2. **Code-header-based** (`httpRequestProcessorTable`): if no path matches,
///    the runtime reads the `code` header and looks up the processor by request
///    code (SUBSCRIBE, UNSUBSCRIBE, MSG_SEND_ASYNC, HEARTBEAT, …).
///
/// Because this SDK sends `application/x-www-form-urlencoded` bodies (matching
/// the Java SDK), **all** operations — publish, subscribe, unsubscribe, and
/// heartbeat — must use [`ROOT`] so the request falls through to code-header
/// dispatch. Posting to a path-based handler with a form body breaks body
/// decoding on the runtime side.
pub mod uri {
    /// Root path — matches no path-based handler, forcing code-header routing.
    pub const ROOT: &str = "/";
    /// Async single-message publish — path-based handler `SendAsyncEventProcessor`.
    pub const PUBLISH: &str = "/eventmesh/publish";
    /// Path-based subscribe handler on the runtime side (`LocalSubscribeEventProcessor`).
    ///
    /// **Do not use for form-based subscribe** — that handler expects a JSON
    /// body and fails to deserialize a form-urlencoded `topic` field. Use
    /// [`ROOT`] with the `SUBSCRIBE` code header instead.
    pub const SUBSCRIBE: &str = "/eventmesh/subscribe/local";
    /// Path-based unsubscribe handler on the runtime side (`LocalUnSubscribeEventProcessor`).
    ///
    /// **Do not use for form-based unsubscribe** — same issue as [`SUBSCRIBE`].
    /// Use [`ROOT`] with the `UNSUBSCRIBE` code header instead.
    pub const UNSUBSCRIBE: &str = "/eventmesh/unsubscribe/local";
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

impl EventMeshRetObj {
    pub fn is_success(&self) -> bool {
        self.ret_code == 0
    }
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
    #[serde(rename = "retCode")]
    pub ret_code: i32,
    #[serde(skip_serializing_if = "Option::is_none", rename = "retMsg")]
    pub ret_msg: Option<String>,
}

impl WebhookReply {
    pub fn ok() -> Self {
        Self {
            ret_code: crate::common::status_code::ClientRetCode::Ok as i32,
            ret_msg: Some("OK".into()),
        }
    }

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
    #[serde(default)]
    pub bizseqno: Option<String>,
    #[serde(default, rename = "uniqueId")]
    pub unique_id: Option<String>,
    #[serde(default, rename = "randomNo")]
    pub random_no: Option<String>,
    #[serde(default)]
    pub topic: Option<String>,
    /// JSON-encoded `Map<String,String>` of extension attributes.
    #[serde(default, rename = "extFields")]
    pub extfields: Option<String>,
}

impl PushMessageRequestBody {
    /// Decode the pushed body into an [`EventMeshMessage`].
    ///
    /// The `content` field is **always** treated as the business payload —
    /// the Runtime puts the original user payload there, not a serialized
    /// `EventMeshMessage`.  Message metadata (`topic`, `bizseqno`,
    /// `uniqueId`, `extFields`) is taken from the form-level fields.
    pub fn to_event_mesh_message(&self) -> Result<EventMeshMessage> {
        let mut msg = EventMeshMessage::builder()
            .content(self.content.clone())
            .build();
        msg.topic = self.topic.clone();
        msg.biz_seq_no = self.bizseqno.clone();
        msg.unique_id = self.unique_id.clone();

        // Parse extFields JSON into props. Invalid JSON is an error — the
        // webhook handler returns a retry reply so the runtime redelivers,
        // matching the Java runtime's PushMessageRequestBody.buildBody()
        // which throws on parse failure.
        if let Some(ext) = &self.extfields {
            let trimmed = ext.trim();
            if !trimmed.is_empty() {
                let props: HashMap<String, String> =
                    serde_json::from_str(trimmed).map_err(|e| EventMeshError::Protocol {
                        transport: "http",
                        message: format!("failed to parse extFields JSON: {e}"),
                    })?;
                msg.props = props;
            }
        }

        Ok(msg)
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
    identity: &ClientIdentity,
) -> Vec<(&'static str, String)> {
    let mut headers = vec![
        ("code", code.to_string()),
        ("env", identity.env.clone()),
        ("idc", identity.idc.clone()),
        ("sys", identity.sys.clone()),
        ("pid", identity.pid.clone()),
        ("ip", identity.ip.clone()),
        ("username", identity.username.clone()),
        ("passwd", identity.password.clone()),
        ("language", identity.language.clone()),
        ("version", PROTOCOL_VERSION.to_string()),
        ("protocoltype", protocol_type.as_str().to_string()),
        ("protocolversion", PROTOCOL_VERSION.to_string()),
        ("protocoldesc", "http".to_string()),
    ];
    if let Some(token) = &identity.token {
        headers.push(("token", token.clone()));
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
pub fn encode_publish(msg: &EventMeshMessage, identity: &ClientIdentity) -> Vec<(String, String)> {
    let mut fields: Vec<(String, String)> = Vec::new();
    fields.push(("producergroup".into(), identity.producer_group.clone()));
    fields.push(("topic".into(), msg.topic.clone().unwrap_or_default()));
    fields.push(("content".into(), msg.content.clone().unwrap_or_default()));
    // Always emit a `ttl` form field, falling back to `DEFAULT_MESSAGE_TTL`
    // when the caller did not set one. The runtime's
    // `SendSyncMessageProcessor` rejects a blank TTL with
    // `EVENTMESH_PROTOCOL_BODY_ERR` before any defaulting (unlike the async
    // processor, which patches in a default after validation), so request-reply
    // calls would fail whenever `EventMeshMessage::ttl` / the `ttl` prop is
    // unset. This mirrors the gRPC codec (and the Java gRPC SDK's
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
        .or_else(|| msg.get_prop(ProtocolKey::TTL).map(str::to_string))
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
    if !msg.props.is_empty() {
        // Filter out reserved keys that are already emitted as typed form
        // fields above. The runtime post-processes `extFields` and would
        // reverse-overwrite the typed values (e.g. a stale `ttl` prop
        // clobbering the resolved TTL, or an old `bizseqno` overwriting the
        // auto-generated one).
        const RESERVED_KEYS: &[&str] = &[
            "producergroup",
            "topic",
            "content",
            "ttl",
            "bizseqno",
            "uniqueid",
        ];
        let filtered: HashMap<&String, &String> = msg
            .props
            .iter()
            .filter(|(k, _)| !RESERVED_KEYS.contains(&k.as_str()))
            .collect();
        if !filtered.is_empty() {
            fields.push((
                "extFields".into(),
                serde_json::to_string(&filtered).unwrap_or_default(),
            ));
        }
    }
    fields
}

/// Encode subscribe body fields.
pub fn encode_subscribe(
    items: &[SubscriptionItem],
    url: &str,
    identity: &ClientIdentity,
) -> Vec<(String, String)> {
    vec![
        ("consumerGroup".into(), identity.consumer_group.clone()),
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
    identity: &ClientIdentity,
) -> Vec<(String, String)> {
    vec![
        ("consumerGroup".into(), identity.consumer_group.clone()),
        (
            "topic".into(),
            serde_json::to_string(topics).unwrap_or_default(),
        ),
        ("url".into(), url.to_string()),
    ]
}

/// Encode heartbeat body fields.
pub fn encode_heartbeat(
    items: &[(String, String)],
    identity: &ClientIdentity,
) -> Vec<(String, String)> {
    use crate::model::HeartbeatItem;

    let entities: Vec<HeartbeatItem> = items
        .iter()
        .map(|(topic, url)| HeartbeatItem::new(topic.clone(), url.clone()))
        .collect();
    vec![
        ("consumerGroup".into(), identity.consumer_group.clone()),
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

/// The reply payload returned inside the `retMsg` field of a request-reply
/// `EventMeshRetObj`.
///
/// Mirrors `SendMessageResponseBody.ReplyMessage` on the Java side:
/// `topic`, `body`, and `properties`.
#[derive(Debug, Clone, Deserialize)]
pub struct ReplyMessage {
    #[serde(default)]
    pub topic: Option<String>,
    #[serde(default)]
    pub body: Option<String>,
    #[serde(default)]
    pub properties: HashMap<String, String>,
}

/// Parse the reply message from the `retMsg` field of a request-reply
/// response, mapping `body` → `content`, `topic` → `topic`, and
/// `properties` → `props`.
///
/// Mirrors the Java SDK's `EventMeshMessageProducer.transformMessage`, which
/// deserializes `retMsg` as a `SendMessageResponseBody.ReplyMessage`.
pub fn parse_reply(ret_msg: &str) -> Result<EventMeshMessage> {
    let reply: ReplyMessage = serde_json::from_str(ret_msg)?;
    Ok(EventMeshMessage::builder()
        .topic(reply.topic.unwrap_or_default())
        .content(reply.body.unwrap_or_default())
        .props(reply.properties)
        .build())
}

/// Form-encode a list of `(key, value)` pairs into a URL-encoded body string.
pub fn form_encode(fields: &[(String, String)]) -> String {
    serde_urlencoded::to_string(fields).unwrap_or_default()
}

/// Request code for the given operation.
pub fn publish_code() -> i32 {
    RequestCode::MSG_SEND_ASYNC
}

/// Request code for synchronous request-reply (code-based routing).
pub fn publish_sync_code() -> i32 {
    RequestCode::MSG_SEND_SYNC
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

    #[test]
    fn encode_publish_round_trip() {
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder()
            .topic("test-topic")
            .content("hello")
            .biz_seq_no("seq-1")
            .build();
        let fields = encode_publish(&msg, &identity);
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
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder().topic("t").content("c").build();
        let fields = encode_publish(&msg, &identity);
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
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .biz_seq_no("my-seq")
            .unique_id("my-uid")
            .build();
        let fields = encode_publish(&msg, &identity);
        let map: HashMap<String, String> = fields.into_iter().collect();
        assert_eq!(map.get("bizseqno"), Some(&"my-seq".to_string()));
        assert_eq!(map.get("uniqueid"), Some(&"my-uid".to_string()));
    }

    #[test]
    fn encode_publish_keeps_identity_out_of_body() {
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder().topic("t").content("c").build();
        let fields = encode_publish(&msg, &identity);
        let encoded = form_encode(&fields);
        // Identity must be in headers, not body.
        assert!(!encoded.contains("env="));
        assert!(!encoded.contains("username="));
        assert!(!encoded.contains("passwd="));
        assert!(!encoded.contains("pid="));
    }

    #[test]
    fn encode_publish_includes_ext_fields() {
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .prop("key1", "val1")
            .prop("key2", "val2")
            .build();
        let fields = encode_publish(&msg, &identity);
        let map: HashMap<String, String> = fields.into_iter().collect();
        let ext = map.get("extFields").expect("extFields should be present");
        let props: HashMap<String, String> = serde_json::from_str(ext).unwrap();
        assert_eq!(props.get("key1"), Some(&"val1".to_string()));
        assert_eq!(props.get("key2"), Some(&"val2".to_string()));
    }

    #[test]
    fn encode_publish_filters_reserved_keys_from_ext_fields() {
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .ttl_millis(7_000)
            .biz_seq_no("my-seq")
            .unique_id("my-uid")
            .prop("key1", "val1")
            .prop("ttl", "99000")
            .prop("bizseqno", "stale-seq")
            .prop("uniqueid", "stale-uid")
            .prop("topic", "stale-topic")
            .prop("content", "stale-content")
            .prop("producergroup", "stale-group")
            .build();
        let fields = encode_publish(&msg, &identity);
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
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .prop("ttl", "99000")
            .prop("bizseqno", "stale")
            .build();
        let fields = encode_publish(&msg, &identity);
        // All props were reserved keys → no extFields field should be emitted.
        assert!(!fields.iter().any(|(k, _)| k == "extFields"));
    }

    #[test]
    fn encode_publish_omits_ext_fields_when_empty() {
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder().topic("t").content("c").build();
        let fields = encode_publish(&msg, &identity);
        assert!(!fields.iter().any(|(k, _)| k == "extFields"));
    }

    #[test]
    fn encode_publish_defaults_ttl_when_unset() {
        // The runtime's SendSyncMessageProcessor rejects a blank TTL with
        // EVENTMESH_PROTOCOL_BODY_ERR, so encode_publish must always emit one.
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder().topic("t").content("c").build();
        let fields = encode_publish(&msg, &identity);
        let map: HashMap<String, String> = fields.into_iter().collect();
        let ttl = map.get("ttl").expect("ttl should always be present");
        assert_eq!(ttl, &DEFAULT_MESSAGE_TTL.to_string());
    }

    #[test]
    fn encode_publish_keeps_caller_supplied_ttl() {
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .ttl_millis(30_000)
            .build();
        let fields = encode_publish(&msg, &identity);
        let map: HashMap<String, String> = fields.into_iter().collect();
        assert_eq!(map.get("ttl"), Some(&"30000".to_string()));
    }

    #[test]
    fn encode_publish_keeps_ttl_from_prop_when_field_unset() {
        // A `ttl` prop should be honored when the typed `ttl` field is None,
        // matching the gRPC codec's fallback chain.
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .prop(ProtocolKey::TTL, "99000")
            .build();
        let fields = encode_publish(&msg, &identity);
        let map: HashMap<String, String> = fields.into_iter().collect();
        assert_eq!(map.get("ttl"), Some(&"99000".to_string()));
    }

    #[test]
    fn encode_publish_typed_ttl_takes_precedence_over_prop() {
        let identity = ClientIdentity::detect();
        let msg = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .ttl_millis(7_000)
            .prop(ProtocolKey::TTL, "99000")
            .build();
        let fields = encode_publish(&msg, &identity);
        let map: HashMap<String, String> = fields.into_iter().collect();
        assert_eq!(map.get("ttl"), Some(&"7000".to_string()));
    }

    #[test]
    fn build_headers_carries_identity_and_token() {
        let mut identity = ClientIdentity::detect();
        identity.token = Some("my-jwt".into());
        let headers = build_headers(
            RequestCode::MSG_SEND_ASYNC,
            EventMeshProtocolType::EventMeshMessage,
            &identity,
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
        let identity = ClientIdentity::detect();
        assert!(identity.token.is_none());
        let headers = build_headers(
            RequestCode::MSG_SEND_ASYNC,
            EventMeshProtocolType::EventMeshMessage,
            &identity,
        );
        assert!(!headers.iter().any(|(k, _)| *k == "token"));
    }

    #[test]
    fn publish_sync_code_is_101() {
        assert_eq!(publish_sync_code(), RequestCode::MSG_SEND_SYNC);
        assert_eq!(publish_sync_code(), 101);
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
    fn parse_reply_from_ret_msg() {
        let ret_msg = r#"{"topic":"reply-topic","body":"reply-body","properties":{"k":"v"}}"#;
        let msg = parse_reply(ret_msg).unwrap();
        assert_eq!(msg.topic.as_deref(), Some("reply-topic"));
        assert_eq!(msg.content.as_deref(), Some("reply-body"));
        assert_eq!(msg.get_prop("k"), Some("v"));
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
        assert_eq!(msg.content.as_deref(), Some(business_json));
        assert_eq!(msg.topic.as_deref(), Some("test-topic"));
        assert_eq!(msg.biz_seq_no.as_deref(), Some("seq-1"));
    }

    #[test]
    fn push_body_decodes_ext_fields_camel_case() {
        // The runtime sends extFields (camelCase) as a JSON-encoded map string.
        let props_json = r#"{"prop1":"val1","prop2":"val2"}"#;
        let body = form_encode(&[
            ("content".to_string(), "hello".to_string()),
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
