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

//! Package construction helpers (mirrors Java `MessageUtils`).

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::error::Result;
use crate::model::{EventMeshMessage, PublishResponse, SubscriptionItem};

use super::frame::{Command, Header, Package, PackageBody, Subscription, UserAgent};

/// Length of the random correlation seq (matches Java `SEQ_LENGTH = 10`).
const SEQ_LEN: usize = 10;

const PROTOCOL_TYPE_KEY: &str = "protocoltype";
const PROTOCOL_VERSION_KEY: &str = "protocolversion";
const PROTOCOL_DESC_KEY: &str = "protocoldesc";
const EM_MESSAGE_PROTOCOL: &str = "eventmeshmessage";
const OPEN_MESSAGE_PROTOCOL: &str = "openmessage";
const CLOUD_EVENTS_PROTOCOL: &str = "cloudevents";
const PROTOCOL_DESC_TCP: &str = "tcp";

/// The TCP wire-format body for `eventmeshmessage` protocol messages.
///
/// This mirrors `org.apache.eventmesh.common.protocol.tcp.EventMeshMessage`
/// (NOT `org.apache.eventmesh.common.EventMeshMessage`). The Java runtime's
/// TCP codec serializes/deserializes the package body as JSON using this class's
/// field names: `topic`, `properties`, `headers`, `body`.
///
/// The SDK's user-facing [`EventMeshMessage`] uses different field names
/// (`content`, `props`). This struct bridges the two so that messages round-trip
/// correctly through the Java server.
#[derive(Debug, Clone, Serialize, Deserialize)]
struct TcpWireMessage {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    topic: Option<String>,
    #[serde(default, skip_serializing_if = "HashMap::is_empty")]
    properties: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "HashMap::is_empty")]
    headers: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    body: Option<String>,
}

impl From<&EventMeshMessage> for TcpWireMessage {
    fn from(msg: &EventMeshMessage) -> Self {
        Self {
            topic: msg.topic.clone(),
            properties: msg.props.clone(),
            headers: HashMap::new(),
            body: msg.content.clone(),
        }
    }
}

impl From<TcpWireMessage> for EventMeshMessage {
    fn from(wire: TcpWireMessage) -> Self {
        // Merge wire `headers` into `props` so protocol-level metadata
        // (e.g. `datacontenttype`) set by the Java runtime is not lost.
        // The Rust SDK's `EventMeshMessage` model does not have a separate
        // `headers` field, so `props` is the only place to carry them.
        let mut props = wire.properties;
        for (k, v) in wire.headers {
            props.entry(k).or_insert(v);
        }
        EventMeshMessage {
            topic: wire.topic,
            content: wire.body,
            props,
            ..Default::default()
        }
    }
}

/// Generate a random numeric string of length [`SEQ_LEN`] (mirrors Java
/// `MessageUtils.generateRandomString`).
fn random_seq() -> String {
    crate::common::RandomStringUtils::generate_num(SEQ_LEN)
}

/// Build a bare package with the given command and a fresh random seq.
pub fn package(cmd: Command) -> Package {
    Package::new(Header::new(cmd, random_seq()))
}

/// Build an ACK package for an inbound `in_pkg`, copying its seq and body.
/// Mirrors Java `MessageUtils.getPackage(command, in)`.
///
/// The seq is copied verbatim (including `None`): server-initiated frames such
/// as `SERVER_GOODBYE_REQUEST` arrive without a seq, and the ACK must echo that
/// shape rather than synthesizing one.
pub fn ack(cmd: Command, in_pkg: &Package) -> Package {
    let header = Header {
        cmd,
        code: in_pkg.header.code,
        desc: None,
        seq: in_pkg.header.seq.clone(),
        properties: in_pkg.header.properties.clone(),
    };
    Package {
        header,
        body: in_pkg.body.clone(),
    }
}

// ---------------------------------------------------------------------------
// Control-plane builders
// ---------------------------------------------------------------------------

/// HELLO_REQUEST with a `UserAgent` body.
pub fn hello(user_agent: &UserAgent) -> Package {
    package(Command::HelloRequest).with_body(PackageBody::UserAgent(Box::new(user_agent.clone())))
}

/// HEARTBEAT_REQUEST (no body).
pub fn heartbeat() -> Package {
    package(Command::HeartbeatRequest)
}

/// CLIENT_GOODBYE_REQUEST (no body).
pub fn goodbye() -> Package {
    package(Command::ClientGoodbyeRequest)
}

/// LISTEN_REQUEST (no body).
pub fn listen() -> Package {
    package(Command::ListenRequest)
}

/// SUBSCRIBE_REQUEST with a single-item `Subscription` body.
pub fn subscribe(topic: &str, items: &[SubscriptionItem]) -> Package {
    let _ = topic; // topic is in the items; kept for API symmetry with Java
    let sub = Subscription::new(items.to_vec());
    package(Command::SubscribeRequest).with_body(PackageBody::Subscription(sub))
}

/// UNSUBSCRIBE_REQUEST with a `Subscription` body.
pub fn unsubscribe(items: &[SubscriptionItem]) -> Package {
    let sub = Subscription::new(items.to_vec());
    package(Command::UnsubscribeRequest).with_body(PackageBody::Subscription(sub))
}

// ---------------------------------------------------------------------------
// ACK builders
// ---------------------------------------------------------------------------

pub fn async_message_ack(in_pkg: &Package) -> Package {
    ack(Command::AsyncMessageToClientAck, in_pkg)
}

pub fn broadcast_message_ack(in_pkg: &Package) -> Package {
    ack(Command::BroadcastMessageToClientAck, in_pkg)
}

pub fn request_to_client_ack(in_pkg: &Package) -> Package {
    ack(Command::RequestToClientAck, in_pkg)
}

pub fn response_to_client_ack(in_pkg: &Package) -> Package {
    ack(Command::ResponseToClientAck, in_pkg)
}

// ---------------------------------------------------------------------------
// User-message builders
// ---------------------------------------------------------------------------

/// Wrap an [`EventMeshMessage`] into a [`Package`] with the given command.
///
/// Sets the `protocoltype`/`protocolversion`/`protocoldesc` header properties
/// so the server knows how to deserialize the body.
///
/// Returns a [`crate::error::EventMeshError::Codec`] if the message cannot be
/// serialized — never silently sends an empty body.
pub fn build_message_package(msg: &EventMeshMessage, cmd: Command) -> Result<Package> {
    let mut pkg = package(cmd);
    pkg.header
        .set_property(PROTOCOL_TYPE_KEY, EM_MESSAGE_PROTOCOL);
    pkg.header.set_property(PROTOCOL_VERSION_KEY, "1.0");
    pkg.header
        .set_property(PROTOCOL_DESC_KEY, PROTOCOL_DESC_TCP);

    // Serialize the message body using the TCP wire format
    // (`org.apache.eventmesh.common.protocol.tcp.EventMeshMessage`), which uses
    // `body`/`properties` — NOT the SDK's `content`/`props` field names.
    let wire = TcpWireMessage::from(msg);
    let json = serde_json::to_string(&wire)?;
    pkg.body = PackageBody::Text(json);

    // Copy seqnum/uniqueid/ttl into header properties for routing.
    if let Some(ref seq) = msg.biz_seq_no {
        pkg.header.set_property("seqnum", seq);
    }
    if let Some(ref uid) = msg.unique_id {
        pkg.header.set_property("uniqueid", uid);
    }
    if let Some(ttl) = msg.ttl {
        pkg.header.set_property("ttl", ttl.to_string());
    }
    Ok(pkg)
}

/// Encode an OpenMessaging value using the native TCP body while retaining
/// the original public protocol discriminator.
pub(crate) fn build_open_message_package(
    msg: &crate::model::OpenMessage,
    cmd: Command,
) -> Result<Package> {
    let mut pkg = build_message_package(&msg.to_event_mesh_message(), cmd)?;
    pkg.header
        .set_property(PROTOCOL_TYPE_KEY, OPEN_MESSAGE_PROTOCOL);
    Ok(pkg)
}

/// Convert a server ACK [`Package`] into a [`PublishResponse`].
///
/// The Java runtime encodes the ACK result in the `Header`'s dedicated `code`
/// (an `OPStatus` value: `0 = SUCCESS`, `1 = FAIL`, `2 = ACL_FAIL`,
/// `3 = TPS_OVERLOAD`) and `desc` fields. The reply processors
/// (`MessageTransferProcessor`, `SubscribeProcessor`, `UnSubscribeProcessor`)
/// build responses via `new Header(replyCmd, OPStatus.<status>.getCode(), desc,
/// seq)`. Reading from `header.properties["statuscode"]` always yields `None`
/// (the server never populates it) and would mask every server-side failure as
/// a success.
pub fn response_from_pkg(pkg: &Package) -> PublishResponse {
    PublishResponse::new(Some(pkg.header.code as i64), pkg.header.desc.clone(), None)
}

/// Parse an inbound message body ([`PackageBody::Text`]) back into an
/// [`EventMeshMessage`]. Returns an empty message on failure.
///
/// Deserializes the TCP wire format (`body`/`properties` fields) and maps them
/// back to the SDK's `content`/`props` fields.
pub fn parse_message(body: &PackageBody) -> Option<EventMeshMessage> {
    match body {
        PackageBody::Text(s) => {
            let wire: TcpWireMessage = serde_json::from_str(s).ok()?;
            Some(EventMeshMessage::from(wire))
        }
        _ => None,
    }
}

// ---------------------------------------------------------------------------
// CloudEvents wire support
// ---------------------------------------------------------------------------

/// Whether the given header properties declare a CloudEvents body
/// (`protocoltype == "cloudevents"`). Used by the consumer to decide whether
/// to parse the body as a CloudEvent JSON or a TCP-wire `EventMeshMessage`.
pub fn is_cloudevents(pkg: &Package) -> bool {
    pkg.header.get_string_property(PROTOCOL_TYPE_KEY) == Some(CLOUD_EVENTS_PROTOCOL)
}

/// Whether the package retains an OpenMessaging protocol discriminator.
pub(crate) fn is_open_message(pkg: &Package) -> bool {
    pkg.header.get_string_property(PROTOCOL_TYPE_KEY) == Some(OPEN_MESSAGE_PROTOCOL)
}

/// Wrap a native [`cloudevents::Event`] into a [`Package`] with the given
/// command, using the CloudEvents JSON wire format
/// (`application/cloudevents+json`).
///
/// Sets `protocoltype=cloudevents`, `protocolversion=<event.specversion>`,
/// `protocoldesc=tcp` so the Java runtime's codec writes the body bytes
/// verbatim instead of re-serializing via Jackson.
///
/// # `datacontenttype` requirement
///
/// The CloudEvent's `datacontenttype` **must** be set to
/// `application/cloudevents+json`. The Java runtime's
/// `CloudEventsProtocolAdaptor.fromCloudEvent` (downlink path) uses
/// `datacontenttype` to resolve the CloudEvents `EventFormat` serializer
/// via `EventFormatProvider.resolveFormat(dataContentType)`. The only
/// registered format is `application/cloudevents+json`; any other value
/// (e.g. `application/json`, `text/plain`) causes `resolveFormat()` to
/// return null, which triggers an NPE that silently drops the message.
///
/// This is a known server-side quirk — the Java SDK works around it by
/// always setting `datacontenttype = application/cloudevents+json` for TCP
/// CloudEvents (see `ExampleConstants.CLOUDEVENT_CONTENT_TYPE`).
///
/// This mirrors Java's `MessageUtils.buildPackage(cloudEvent, command)`:
/// the CloudEvent is serialized to JSON by the cloudevents crate's serde
/// impl (equivalent to `EventFormat.serialize` in Java), and the resulting
/// bytes are stored as [`PackageBody::Bytes`]. The TCP codec detects the
/// `cloudevents` protocol type and writes the raw bytes without further
/// JSON encoding.
#[cfg(feature = "cloud_events")]
pub fn build_cloud_event_package(event: &cloudevents::Event, cmd: Command) -> Result<Package> {
    use cloudevents::AttributesReader;

    let mut pkg = package(cmd);
    pkg.header
        .set_property(PROTOCOL_TYPE_KEY, CLOUD_EVENTS_PROTOCOL);
    pkg.header
        .set_property(PROTOCOL_VERSION_KEY, event.specversion().as_str());
    pkg.header
        .set_property(PROTOCOL_DESC_KEY, PROTOCOL_DESC_TCP);

    // Serialize the CloudEvent as CloudEvents JSON
    // (application/cloudevents+json). The cloudevents crate's serde impl
    // produces the canonical CloudEvents JSON format, matching what the Java
    // runtime's `EventFormatProvider.resolveFormat(JsonFormat.CONTENT_TYPE)`
    // expects on decode.
    let json = serde_json::to_vec(event)?;
    pkg.body = PackageBody::Bytes(json);

    Ok(pkg)
}

/// Parse a CloudEvents body ([`PackageBody::Text`] or [`PackageBody::Bytes`])
/// back into a native [`cloudevents::Event`]. Returns `None` on failure.
///
/// On the wire, CloudEvents bodies arrive as a JSON string in `Text` (the
/// codec decodes valid UTF-8 bodies as strings). This function reverses the
/// serialization done by [`build_cloud_event_package`].
#[cfg(feature = "cloud_events")]
pub fn parse_cloud_event(body: &PackageBody) -> Option<cloudevents::Event> {
    match body {
        PackageBody::Text(s) => serde_json::from_str(s).ok(),
        PackageBody::Bytes(b) => serde_json::from_slice(b).ok(),
        _ => None,
    }
}

/// Convert a CloudEvent to an [`EventMeshMessage`] so the consumer's existing
/// `MessageListener<Message = EventMeshMessage>` can handle CloudEvents
/// deliveries transparently.
///
/// - `subject` → `topic`
/// - `data` → `content` (string values are kept as-is; JSON values are
///   stringified; binary values are lossily converted to UTF-8)
/// - CloudEvent extensions (e.g. `ttl`, `seqnum`, `uniqueid`) → `props`
///
/// This mirrors the gRPC codec's `to_event_mesh_message`.
#[cfg(feature = "cloud_events")]
pub fn cloud_event_to_message(event: &cloudevents::Event) -> EventMeshMessage {
    use cloudevents::{AttributesReader, Data};

    let topic = event.subject().map(|s| s.to_string());
    let content = match event.data() {
        Some(Data::String(s)) => Some(s.clone()),
        Some(Data::Binary(b)) => Some(String::from_utf8_lossy(b).into_owned()),
        Some(Data::Json(j)) => Some(j.to_string()),
        None => None,
    };

    let mut props = std::collections::HashMap::new();
    for (k, v) in event.iter_extensions() {
        props.insert(k.to_string(), v.to_string());
    }

    EventMeshMessage {
        topic,
        content,
        props,
        ..Default::default()
    }
}

/// Convert an [`EventMeshMessage`] back into a native [`cloudevents::Event`].
///
/// This is the reverse of [`cloud_event_to_message`] and is used when the
/// consumer replies with an `EventMeshMessage` to a CloudEvents
/// `REQUEST_TO_SERVER` — the producer's `request_reply_cloud_event` uses it to
/// produce a uniform `Event` return type.
#[cfg(feature = "cloud_events")]
pub fn message_to_cloud_event(msg: &EventMeshMessage) -> Result<cloudevents::Event> {
    use cloudevents::{EventBuilder, EventBuilderV10};

    let source = msg.topic.as_deref().unwrap_or("/").to_string();
    let mut builder = EventBuilderV10::new()
        .id(msg
            .unique_id
            .clone()
            .unwrap_or_else(crate::common::RandomStringUtils::generate_uuid))
        .source(source)
        .ty("org.apache.eventmesh");

    if let Some(ref topic) = msg.topic {
        builder = builder.subject(topic);
    }
    if let Some(ref content) = msg.content {
        builder = builder.data("text/plain", content.clone());
    }
    for (k, v) in &msg.props {
        builder = builder.extension(k.as_str(), v.as_str());
    }
    builder
        .build()
        .map_err(|e| crate::error::EventMeshError::Protocol {
            transport: "tcp",
            message: format!("cloudevents build error: {e}"),
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn random_seq_length() {
        let s = random_seq();
        assert_eq!(s.len(), SEQ_LEN);
        assert!(s.chars().all(|c| c.is_ascii_digit()));
    }

    #[test]
    fn ack_preserves_seq() {
        let pkg = package(Command::AsyncMessageToClient);
        let ack_pkg = async_message_ack(&pkg);
        assert_eq!(ack_pkg.header.seq, pkg.header.seq);
        assert_eq!(ack_pkg.header.cmd, Command::AsyncMessageToClientAck);
    }

    #[test]
    fn build_message_sets_protocol_type() {
        let msg = EventMeshMessage::builder()
            .topic("test")
            .content("hello")
            .build();
        let pkg = build_message_package(&msg, Command::AsyncMessageToServer).expect("build pkg");
        assert_eq!(
            pkg.header.get_string_property(PROTOCOL_TYPE_KEY),
            Some(EM_MESSAGE_PROTOCOL)
        );
        assert_eq!(pkg.header.cmd, Command::AsyncMessageToServer);
    }

    #[test]
    fn response_reads_header_code_and_desc() {
        // Server encodes ACK status in header.code/desc, not in properties.
        let mut pkg = package(Command::AsyncMessageToServerAck);
        pkg.header.code = 3; // TPS_OVERLOAD
        pkg.header.desc = Some("tps overload".into());
        // An irrelevant property that must NOT be read as the status.
        pkg.header.set_property("statuscode", "0");

        let resp = response_from_pkg(&pkg);
        assert_eq!(resp.code, Some(3));
        assert!(!resp.is_success());
        assert_eq!(resp.message.as_deref(), Some("tps overload"));
    }

    #[test]
    fn response_success_when_code_zero() {
        let mut pkg = package(Command::AsyncMessageToServerAck);
        pkg.header.code = 0;
        assert!(response_from_pkg(&pkg).is_success());
    }

    #[test]
    fn build_message_uses_tcp_wire_field_names() {
        // The Java runtime's TCP protocol deserializes the body into
        // `org.apache.eventmesh.common.protocol.tcp.EventMeshMessage`, which
        // uses `body` and `properties` — NOT `content` and `props`. If the SDK
        // emits the wrong field names, the server reads null for the content.
        let msg = EventMeshMessage::builder()
            .topic("test-topic")
            .content("hello-body")
            .prop("ttl", "4000")
            .build();
        let pkg = build_message_package(&msg, Command::AsyncMessageToServer).expect("build pkg");
        let json = match &pkg.body {
            PackageBody::Text(s) => s.as_str(),
            other => panic!("expected Text body, got {other:?}"),
        };
        assert!(
            json.contains("\"body\":\"hello-body\""),
            "body must use Java wire field 'body', got: {json}"
        );
        assert!(
            json.contains("\"properties\":"),
            "body must use Java wire field 'properties', got: {json}"
        );
        assert!(
            !json.contains("\"content\""),
            "must NOT emit SDK field 'content' on the wire, got: {json}"
        );
        assert!(
            !json.contains("\"props\""),
            "must NOT emit SDK field 'props' on the wire, got: {json}"
        );
    }

    #[test]
    fn parse_message_reads_tcp_wire_field_names() {
        // Simulate a JSON body produced by the Java server (uses body/properties).
        let server_json = r#"{"topic":"t","properties":{"k":"v"},"body":"payload"}"#;
        let msg = parse_message(&PackageBody::Text(server_json.into())).expect("parse");
        assert_eq!(msg.topic.as_deref(), Some("t"));
        assert_eq!(msg.content.as_deref(), Some("payload"));
        assert_eq!(msg.get_prop("k"), Some("v"));
    }

    #[test]
    fn parse_message_preserves_wire_headers() {
        // The Java runtime puts protocol-level metadata (e.g.
        // datacontenttype) in the wire `headers` field. These must not be
        // silently discarded when deserializing into EventMeshMessage.
        let server_json = r#"{"topic":"t","headers":{"datacontenttype":"application/json"},"properties":{"k":"v"},"body":"payload"}"#;
        let msg = parse_message(&PackageBody::Text(server_json.into())).expect("parse");
        assert_eq!(msg.content.as_deref(), Some("payload"));
        assert_eq!(msg.get_prop("k"), Some("v"));
        assert_eq!(
            msg.get_prop("datacontenttype"),
            Some("application/json"),
            "wire headers must be merged into props"
        );
    }

    #[test]
    fn wire_format_round_trip() {
        let original = EventMeshMessage::builder()
            .topic("round-trip")
            .content("payload")
            .prop("key", "val")
            .build();
        let pkg =
            build_message_package(&original, Command::AsyncMessageToServer).expect("build pkg");
        let parsed = parse_message(&pkg.body).expect("parse");
        assert_eq!(parsed.topic, original.topic);
        assert_eq!(parsed.content, original.content);
        assert_eq!(parsed.props, original.props);
    }

    #[test]
    fn is_cloudevents_detects_protocol() {
        let em_pkg = package(Command::AsyncMessageToServer);
        assert!(!is_cloudevents(&em_pkg));

        let mut ce_pkg = package(Command::AsyncMessageToServer);
        ce_pkg
            .header
            .set_property(PROTOCOL_TYPE_KEY, CLOUD_EVENTS_PROTOCOL);
        assert!(is_cloudevents(&ce_pkg));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn cloudevents_build_sets_protocol_headers() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("ce-1")
            .source("https://example.com")
            .ty("com.example.test")
            .subject("ce-topic")
            .data(
                "application/cloudevents+json",
                serde_json::json!({"hello": "world"}),
            )
            .build()
            .expect("valid event");

        let pkg =
            build_cloud_event_package(&event, Command::AsyncMessageToServer).expect("build pkg");
        assert_eq!(
            pkg.header.get_string_property(PROTOCOL_TYPE_KEY),
            Some(CLOUD_EVENTS_PROTOCOL)
        );
        assert_eq!(
            pkg.header.get_string_property(PROTOCOL_DESC_KEY),
            Some(PROTOCOL_DESC_TCP)
        );
        assert_eq!(
            pkg.header.get_string_property(PROTOCOL_VERSION_KEY),
            Some("1.0")
        );
        assert_eq!(pkg.header.cmd, Command::AsyncMessageToServer);
        // Body must be Bytes (raw JSON, not re-encoded).
        assert!(matches!(pkg.body, PackageBody::Bytes(_)));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn cloudevents_round_trip() {
        use cloudevents::{AttributesReader, EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("ce-rt-1")
            .source("https://example.com")
            .ty("com.example.test")
            .subject("ce-round-trip")
            .data("text/plain", "hello cloudevents")
            .build()
            .expect("valid event");

        let pkg =
            build_cloud_event_package(&event, Command::AsyncMessageToServer).expect("build pkg");
        assert!(is_cloudevents(&pkg));

        // Parse back — the codec would deliver the body as Text (valid UTF-8).
        let body_text = match &pkg.body {
            PackageBody::Bytes(b) => {
                PackageBody::Text(String::from_utf8(b.clone()).expect("cloudevents json is utf-8"))
            }
            ref other => panic!("expected Bytes body, got {other:?}"),
        };
        let parsed = parse_cloud_event(&body_text).expect("parse cloudevent");
        assert_eq!(parsed.subject(), Some("ce-round-trip"));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn cloudevents_to_message_preserves_topic_and_content() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("ce-conv-1")
            .source("https://example.com")
            .ty("com.example.test")
            .subject("conv-topic")
            .data("text/plain", "conv-content")
            .extension("ttl", "5000")
            .build()
            .expect("valid event");

        let msg = cloud_event_to_message(&event);
        assert_eq!(msg.topic.as_deref(), Some("conv-topic"));
        assert_eq!(msg.content.as_deref(), Some("conv-content"));
        assert_eq!(msg.get_prop("ttl"), Some("5000"));
    }
}
