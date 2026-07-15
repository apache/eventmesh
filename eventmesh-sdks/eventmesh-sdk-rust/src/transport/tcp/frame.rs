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

//! TCP wire-frame types: [`Command`], [`Header`], [`Package`], [`UserAgent`].
//!
//! These mirror `org.apache.eventmesh.common.protocol.tcp.*` on the Java side
//! and are the in-memory representation decoded/encoded by [`super::codec`].

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::error::{EventMeshError, Result};
use crate::model::SubscriptionItem;

// ---------------------------------------------------------------------------
// Command
// ---------------------------------------------------------------------------

/// All TCP command types (mirrors Java `Command.java`, values 0–36).
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Command {
    /// Client sends heartbeat packet to server.
    HeartbeatRequest = 0,
    /// Server responds to client heartbeat.
    HeartbeatResponse = 1,
    /// Client sends handshake request.
    HelloRequest = 2,
    /// Server responds to handshake.
    HelloResponse = 3,
    /// Client notifies server of active disconnect.
    ClientGoodbyeRequest = 4,
    /// Server replies to client's disconnect notification.
    ClientGoodbyeResponse = 5,
    /// Server notifies client of active disconnect.
    ServerGoodbyeRequest = 6,
    /// Client replies to server's disconnect notification.
    ServerGoodbyeResponse = 7,
    /// Subscription request.
    SubscribeRequest = 8,
    /// Server replies to subscription.
    SubscribeResponse = 9,
    /// Unsubscribe request.
    UnsubscribeRequest = 10,
    /// Server replies to unsubscribe.
    UnsubscribeResponse = 11,
    /// Request to start topic listening.
    ListenRequest = 12,
    /// Server replies to listen request.
    ListenResponse = 13,
    /// Client sends RR request to server.
    RequestToServer = 14,
    /// Server pushes RR request to client.
    RequestToClient = 15,
    /// Client ACKs RR request.
    RequestToClientAck = 16,
    /// Client sends RR reply to server.
    ResponseToServer = 17,
    /// Server pushes RR reply to client.
    ResponseToClient = 18,
    /// Client ACKs RR reply.
    ResponseToClientAck = 19,
    /// Client sends asynchronous events.
    AsyncMessageToServer = 20,
    /// Server ACKs asynchronous events.
    AsyncMessageToServerAck = 21,
    /// Server pushes asynchronous events to client.
    AsyncMessageToClient = 22,
    /// Client ACKs asynchronous events.
    AsyncMessageToClientAck = 23,
    /// Client sends broadcast message.
    BroadcastMessageToServer = 24,
    /// Server ACKs broadcast message.
    BroadcastMessageToServerAck = 25,
    /// Server pushes broadcast message to client.
    BroadcastMessageToClient = 26,
    /// Client ACKs broadcast message.
    BroadcastMessageToClientAck = 27,
    /// Business log reporting.
    SysLogToLogServer = 28,
    /// RMB tracking log reporting.
    TraceLogToLogServer = 29,
    /// Server pushes redirection instruction.
    RedirectToClient = 30,
    /// Client sends registration request.
    RegisterRequest = 31,
    /// Server sends registration result.
    RegisterResponse = 32,
    /// Client sends de-registration request.
    UnregisterRequest = 33,
    /// Server sends de-registration result.
    UnregisterResponse = 34,
    /// Client sends recommendation request.
    RecommendRequest = 35,
    /// Server sends recommendation result.
    RecommendResponse = 36,
}

impl Command {
    /// Numeric wire value.
    pub fn as_u8(self) -> u8 {
        self as u8
    }

    /// The wire name — the exact Java `Command` enum constant name in
    /// SCREAMING_SNAKE_CASE, which is how the Java runtime (Jackson default)
    /// serializes the `cmd` field on the wire.
    pub fn name(self) -> &'static str {
        match self {
            Self::HeartbeatRequest => "HEARTBEAT_REQUEST",
            Self::HeartbeatResponse => "HEARTBEAT_RESPONSE",
            Self::HelloRequest => "HELLO_REQUEST",
            Self::HelloResponse => "HELLO_RESPONSE",
            Self::ClientGoodbyeRequest => "CLIENT_GOODBYE_REQUEST",
            Self::ClientGoodbyeResponse => "CLIENT_GOODBYE_RESPONSE",
            Self::ServerGoodbyeRequest => "SERVER_GOODBYE_REQUEST",
            Self::ServerGoodbyeResponse => "SERVER_GOODBYE_RESPONSE",
            Self::SubscribeRequest => "SUBSCRIBE_REQUEST",
            Self::SubscribeResponse => "SUBSCRIBE_RESPONSE",
            Self::UnsubscribeRequest => "UNSUBSCRIBE_REQUEST",
            Self::UnsubscribeResponse => "UNSUBSCRIBE_RESPONSE",
            Self::ListenRequest => "LISTEN_REQUEST",
            Self::ListenResponse => "LISTEN_RESPONSE",
            Self::RequestToServer => "REQUEST_TO_SERVER",
            Self::RequestToClient => "REQUEST_TO_CLIENT",
            Self::RequestToClientAck => "REQUEST_TO_CLIENT_ACK",
            Self::ResponseToServer => "RESPONSE_TO_SERVER",
            Self::ResponseToClient => "RESPONSE_TO_CLIENT",
            Self::ResponseToClientAck => "RESPONSE_TO_CLIENT_ACK",
            Self::AsyncMessageToServer => "ASYNC_MESSAGE_TO_SERVER",
            Self::AsyncMessageToServerAck => "ASYNC_MESSAGE_TO_SERVER_ACK",
            Self::AsyncMessageToClient => "ASYNC_MESSAGE_TO_CLIENT",
            Self::AsyncMessageToClientAck => "ASYNC_MESSAGE_TO_CLIENT_ACK",
            Self::BroadcastMessageToServer => "BROADCAST_MESSAGE_TO_SERVER",
            Self::BroadcastMessageToServerAck => "BROADCAST_MESSAGE_TO_SERVER_ACK",
            Self::BroadcastMessageToClient => "BROADCAST_MESSAGE_TO_CLIENT",
            Self::BroadcastMessageToClientAck => "BROADCAST_MESSAGE_TO_CLIENT_ACK",
            Self::SysLogToLogServer => "SYS_LOG_TO_LOGSERVER",
            Self::TraceLogToLogServer => "TRACE_LOG_TO_LOGSERVER",
            Self::RedirectToClient => "REDIRECT_TO_CLIENT",
            Self::RegisterRequest => "REGISTER_REQUEST",
            Self::RegisterResponse => "REGISTER_RESPONSE",
            Self::UnregisterRequest => "UNREGISTER_REQUEST",
            Self::UnregisterResponse => "UNREGISTER_RESPONSE",
            Self::RecommendRequest => "RECOMMEND_REQUEST",
            Self::RecommendResponse => "RECOMMEND_RESPONSE",
        }
    }

    /// Reverse lookup of [`Command::name`].
    pub fn from_name(name: &str) -> Option<Self> {
        Some(match name {
            "HEARTBEAT_REQUEST" => Self::HeartbeatRequest,
            "HEARTBEAT_RESPONSE" => Self::HeartbeatResponse,
            "HELLO_REQUEST" => Self::HelloRequest,
            "HELLO_RESPONSE" => Self::HelloResponse,
            "CLIENT_GOODBYE_REQUEST" => Self::ClientGoodbyeRequest,
            "CLIENT_GOODBYE_RESPONSE" => Self::ClientGoodbyeResponse,
            "SERVER_GOODBYE_REQUEST" => Self::ServerGoodbyeRequest,
            "SERVER_GOODBYE_RESPONSE" => Self::ServerGoodbyeResponse,
            "SUBSCRIBE_REQUEST" => Self::SubscribeRequest,
            "SUBSCRIBE_RESPONSE" => Self::SubscribeResponse,
            "UNSUBSCRIBE_REQUEST" => Self::UnsubscribeRequest,
            "UNSUBSCRIBE_RESPONSE" => Self::UnsubscribeResponse,
            "LISTEN_REQUEST" => Self::ListenRequest,
            "LISTEN_RESPONSE" => Self::ListenResponse,
            "REQUEST_TO_SERVER" => Self::RequestToServer,
            "REQUEST_TO_CLIENT" => Self::RequestToClient,
            "REQUEST_TO_CLIENT_ACK" => Self::RequestToClientAck,
            "RESPONSE_TO_SERVER" => Self::ResponseToServer,
            "RESPONSE_TO_CLIENT" => Self::ResponseToClient,
            "RESPONSE_TO_CLIENT_ACK" => Self::ResponseToClientAck,
            "ASYNC_MESSAGE_TO_SERVER" => Self::AsyncMessageToServer,
            "ASYNC_MESSAGE_TO_SERVER_ACK" => Self::AsyncMessageToServerAck,
            "ASYNC_MESSAGE_TO_CLIENT" => Self::AsyncMessageToClient,
            "ASYNC_MESSAGE_TO_CLIENT_ACK" => Self::AsyncMessageToClientAck,
            "BROADCAST_MESSAGE_TO_SERVER" => Self::BroadcastMessageToServer,
            "BROADCAST_MESSAGE_TO_SERVER_ACK" => Self::BroadcastMessageToServerAck,
            "BROADCAST_MESSAGE_TO_CLIENT" => Self::BroadcastMessageToClient,
            "BROADCAST_MESSAGE_TO_CLIENT_ACK" => Self::BroadcastMessageToClientAck,
            "SYS_LOG_TO_LOGSERVER" => Self::SysLogToLogServer,
            "TRACE_LOG_TO_LOGSERVER" => Self::TraceLogToLogServer,
            "REDIRECT_TO_CLIENT" => Self::RedirectToClient,
            "REGISTER_REQUEST" => Self::RegisterRequest,
            "REGISTER_RESPONSE" => Self::RegisterResponse,
            "UNREGISTER_REQUEST" => Self::UnregisterRequest,
            "UNREGISTER_RESPONSE" => Self::UnregisterResponse,
            "RECOMMEND_REQUEST" => Self::RecommendRequest,
            "RECOMMEND_RESPONSE" => Self::RecommendResponse,
            _ => return None,
        })
    }
}

impl TryFrom<u8> for Command {
    type Error = EventMeshError;

    fn try_from(value: u8) -> Result<Self> {
        Ok(match value {
            0 => Self::HeartbeatRequest,
            1 => Self::HeartbeatResponse,
            2 => Self::HelloRequest,
            3 => Self::HelloResponse,
            4 => Self::ClientGoodbyeRequest,
            5 => Self::ClientGoodbyeResponse,
            6 => Self::ServerGoodbyeRequest,
            7 => Self::ServerGoodbyeResponse,
            8 => Self::SubscribeRequest,
            9 => Self::SubscribeResponse,
            10 => Self::UnsubscribeRequest,
            11 => Self::UnsubscribeResponse,
            12 => Self::ListenRequest,
            13 => Self::ListenResponse,
            14 => Self::RequestToServer,
            15 => Self::RequestToClient,
            16 => Self::RequestToClientAck,
            17 => Self::ResponseToServer,
            18 => Self::ResponseToClient,
            19 => Self::ResponseToClientAck,
            20 => Self::AsyncMessageToServer,
            21 => Self::AsyncMessageToServerAck,
            22 => Self::AsyncMessageToClient,
            23 => Self::AsyncMessageToClientAck,
            24 => Self::BroadcastMessageToServer,
            25 => Self::BroadcastMessageToServerAck,
            26 => Self::BroadcastMessageToClient,
            27 => Self::BroadcastMessageToClientAck,
            28 => Self::SysLogToLogServer,
            29 => Self::TraceLogToLogServer,
            30 => Self::RedirectToClient,
            31 => Self::RegisterRequest,
            32 => Self::RegisterResponse,
            33 => Self::UnregisterRequest,
            34 => Self::UnregisterResponse,
            35 => Self::RecommendRequest,
            36 => Self::RecommendResponse,
            other => return Err(EventMeshError::Tcp(format!("unknown command: {other}"))),
        })
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

/// Frame header — JSON-serialized on the wire.
///
/// The `cmd` field is serialized/deserialized as the Java `Command` enum
/// constant name string (e.g. `"HELLO_RESPONSE"`) to match the Java server's
/// `Header` JSON (where `Command` is serialized by Jackson's default enum
/// handling).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Header {
    /// Command type (serialized as the Java enum constant name).
    #[serde(with = "command_serde")]
    pub cmd: Command,
    /// Status code (0 = success).
    pub code: i32,
    /// Optional description.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub desc: Option<String>,
    /// Correlation key (random 10-char string generated per request).
    ///
    /// Optional on the wire: the Java runtime sends some server-initiated
    /// frames (`SERVER_GOODBYE_REQUEST`, `REDIRECT_TO_CLIENT`) with `seq =
    /// null`, which `JsonUtils` omits. Treating the field as `Option<String>`
    /// lets us decode those valid frames instead of rejecting them for a
    /// missing required field before `handle_inbound` can ACK them.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub seq: Option<String>,
    /// Arbitrary key-value properties (e.g. `protocol_type`).
    #[serde(default)]
    pub properties: HashMap<String, serde_json::Value>,
}

impl Header {
    /// Create a new header with the given command and a correlation seq.
    ///
    /// `seq` is stored as `Some(seq)` — client-originated frames always carry
    /// a seq so the server can correlate the reply. Server-initiated frames
    /// with no seq are only ever *received* (built directly on the wire by the
    /// Java runtime), so there is no need to construct a `None`-seq header here.
    pub fn new(cmd: Command, seq: impl Into<String>) -> Self {
        Self {
            cmd,
            code: 0,
            desc: None,
            seq: Some(seq.into()),
            properties: HashMap::new(),
        }
    }

    /// Set a string property.
    pub fn set_property(&mut self, key: impl Into<String>, value: impl Into<String>) -> &mut Self {
        self.properties
            .insert(key.into(), serde_json::Value::String(value.into()));
        self
    }

    /// Get a string property.
    pub fn get_string_property(&self, key: &str) -> Option<&str> {
        self.properties.get(key).and_then(|v| v.as_str())
    }
}

/// Serde module for the `cmd` field.
///
/// The Java runtime serializes `Command` as its enum constant **name string**
/// (e.g. `"HELLO_RESPONSE"`) via Jackson's default enum handling, so we must do
/// the same when sending. On decode we additionally accept the numeric form for
/// robustness (Jackson falls back to ordinals when given an integer token, so
/// either form may legitimately appear on the wire).
mod command_serde {
    use serde::{de, Deserialize, Deserializer, Serializer};

    use super::Command;

    pub fn serialize<S: Serializer>(cmd: &Command, s: S) -> Result<S::Ok, S::Error> {
        s.serialize_str(cmd.name())
    }

    pub fn deserialize<'de, D: Deserializer<'de>>(d: D) -> Result<Command, D::Error> {
        let v = serde_json::Value::deserialize(d)?;
        match v {
            serde_json::Value::String(name) => Command::from_name(&name)
                .ok_or_else(|| de::Error::custom(format!("unknown command name: {name}"))),
            serde_json::Value::Number(n) => {
                let value = n.as_i64().ok_or_else(|| {
                    de::Error::custom(format!("command number out of range: {n}"))
                })?;
                // Validate the value fits in a `u8` before the narrowing cast;
                // `value as u8` would silently wrap for values >= 256 (e.g.
                // 256 -> 0 -> HeartbeatRequest) and misinterpret the frame.
                u8::try_from(value)
                    .map_err(|_| de::Error::custom(format!("command number out of range: {value}")))
                    .and_then(|b| Command::try_from(b).map_err(de::Error::custom))
            }
            other => Err(de::Error::custom(format!(
                "expected string or number for `cmd`, got {other}"
            ))),
        }
    }
}

// ---------------------------------------------------------------------------
// UserAgent
// ---------------------------------------------------------------------------

/// Client identity sent in the HELLO body (mirrors Java `UserAgent.java`).
#[derive(Clone, Default, Serialize, Deserialize)]
pub struct UserAgent {
    #[serde(default)]
    pub env: String,
    #[serde(default)]
    pub subsystem: String,
    #[serde(default)]
    pub path: String,
    #[serde(default)]
    pub pid: i32,
    #[serde(default)]
    pub host: String,
    #[serde(default)]
    pub port: i32,
    #[serde(default)]
    pub version: String,
    #[serde(default)]
    pub username: String,
    #[serde(default)]
    pub password: String,
    #[serde(default)]
    pub token: String,
    #[serde(default)]
    pub idc: String,
    #[serde(default)]
    pub group: String,
    #[serde(default)]
    pub purpose: String,
    #[serde(default)]
    pub unack: i32,
}

impl UserAgent {
    /// Build a `UserAgent` from a [`TcpClientConfig`](crate::config::TcpClientConfig)'s
    /// identity, tagged with `purpose` ("pub" or "sub").
    ///
    /// `host` is the **client's** local IP (from `identity.ip`), NOT the server
    /// address. The Java runtime uses `session.getClient().getHost()` to stamp
    /// the `RSP_IP` CloudEvent extension on every pushed message, so a wrong
    /// value here corrupts tracing/metadata.
    pub fn from_identity(
        identity: &crate::config::ClientIdentity,
        port: u16,
        purpose: &str,
    ) -> Self {
        Self {
            env: identity.env.clone(),
            subsystem: identity.sys.clone(),
            path: String::new(),
            pid: identity.pid.parse().unwrap_or(0),
            host: identity.ip.clone(),
            port: port as i32,
            version: "1.0".to_string(),
            username: identity.username.clone(),
            password: identity.password.clone(),
            token: identity.token.clone().unwrap_or_default(),
            idc: identity.idc.clone(),
            group: if purpose == "pub" {
                identity.producer_group.clone()
            } else {
                identity.consumer_group.clone()
            },
            purpose: purpose.to_string(),
            unack: 0,
        }
    }
}

impl std::fmt::Debug for UserAgent {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("UserAgent")
            .field("env", &self.env)
            .field("subsystem", &self.subsystem)
            .field("path", &self.path)
            .field("pid", &self.pid)
            .field("host", &self.host)
            .field("port", &self.port)
            .field("version", &self.version)
            .field("username", &self.username)
            .field("password", &"***")
            .field("token", &"***")
            .field("idc", &self.idc)
            .field("group", &self.group)
            .field("purpose", &self.purpose)
            .field("unack", &self.unack)
            .finish()
    }
}

// ---------------------------------------------------------------------------
// Subscription body
// ---------------------------------------------------------------------------

/// Body for `SUBSCRIBE_REQUEST` / `UNSUBSCRIBE_REQUEST`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Subscription {
    /// Matches Java field name `topicList` (camelCase).
    #[serde(rename = "topicList")]
    pub topic_list: Vec<SubscriptionItem>,
}

impl Subscription {
    pub fn new(topics: Vec<SubscriptionItem>) -> Self {
        Self { topic_list: topics }
    }
}

/// Body for `REDIRECT_TO_CLIENT`.
///
/// Mirrors `org.apache.eventmesh.common.protocol.tcp.RedirectInfo`, whose
/// fields are `ip` (String) and `port` (int). The runtime emits this in
/// `EventMeshTcp2Client.redirectClient2NewEventMesh` to tell the client which
/// EventMesh node to reconnect to during a rebalance. The previous shape only
/// had a defaulted `redirect_to`, which serde silently discarded the target
/// address for, making any redirect handling impossible.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RedirectInfo {
    #[serde(default)]
    pub ip: String,
    #[serde(default)]
    pub port: u16,
}

// ---------------------------------------------------------------------------
// Package
// ---------------------------------------------------------------------------

/// Type-erased body of a [`Package`].
///
/// On the wire, most bodies are JSON. The codec dispatches on the header's
/// `Command` to decide which Rust type to deserialize into (mirrors the Java
/// `Codec.deserializeBody` switch).
#[derive(Debug, Clone, Default)]
pub enum PackageBody {
    /// No body (heartbeat, goodbye, listen, ...).
    #[default]
    Empty,
    /// HELLO / RECOMMEND body.
    UserAgent(Box<UserAgent>),
    /// SUBSCRIBE / UNSUBSCRIBE body.
    Subscription(Subscription),
    /// REDIRECT_TO_CLIENT body.
    RedirectInfo(RedirectInfo),
    /// A raw JSON string — deferred to the protocol layer (most message /
    /// ACK commands). Mirrors the Java "return bodyJsonString" default.
    Text(String),
    /// Raw bytes — used for CloudEvents bodies (serialized by the caller).
    Bytes(Vec<u8>),
}

/// The wire envelope — a [`Header`] plus an optional [`PackageBody`].
///
/// Mirrors Java `Package.java`.
#[derive(Debug, Clone)]
pub struct Package {
    pub header: Header,
    pub body: PackageBody,
}

impl Package {
    pub fn new(header: Header) -> Self {
        Self {
            header,
            body: PackageBody::Empty,
        }
    }

    pub fn with_body(mut self, body: PackageBody) -> Self {
        self.body = body;
        self
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The Java runtime (Jackson default) serializes `Command` as its enum
    /// constant *name string*, not an ordinal. We must emit the same on the wire.
    #[test]
    fn command_serializes_as_java_enum_name_string() {
        let header = Header::new(Command::HelloResponse, "seq-1");
        let json = serde_json::to_string(&header).unwrap();
        assert!(
            json.contains("\"cmd\":\"HELLO_RESPONSE\""),
            "expected cmd serialized as the Java enum name string, got: {json}"
        );
        assert!(
            !json.contains("\"cmd\":3"),
            "cmd must NOT be serialized as a number, got: {json}"
        );
    }

    /// The Java server's HELLO response arrives as `{"cmd":"HELLO_RESPONSE",...}`;
    /// the Rust client must be able to decode it.
    #[test]
    fn decodes_java_wire_format_string_cmd() {
        let json = r#"{"cmd":"HELLO_RESPONSE","code":0,"seq":"seq-1"}"#;
        let header: Header = serde_json::from_str(json).unwrap();
        assert_eq!(header.cmd, Command::HelloResponse);
    }

    /// Ordinal/integer form is also accepted on decode (Jackson falls back to
    /// ordinal matching for integer tokens, so this is a legitimate wire shape).
    #[test]
    fn decodes_numeric_cmd_form() {
        let json = r#"{"cmd":18,"code":0,"seq":"seq-1"}"#;
        let header: Header = serde_json::from_str(json).unwrap();
        assert_eq!(header.cmd, Command::ResponseToClient);
    }

    /// Values >= 256 must be rejected rather than silently wrapped by a
    /// narrowing `as u8` cast (e.g. 256 -> 0 -> HeartbeatRequest).
    #[test]
    fn rejects_out_of_range_numeric_cmd() {
        for bad in [256, 1_000, 65_536] {
            let json = format!(r#"{{"cmd":{bad},"code":0,"seq":"seq-1"}}"#);
            let err = serde_json::from_str::<Header>(&json).unwrap_err();
            assert!(
                err.to_string().contains("out of range"),
                "value {bad} rejected with unexpected message: {err}"
            );
        }
    }

    /// Negative numbers are not valid command ordinals.
    #[test]
    fn rejects_negative_numeric_cmd() {
        let json = r#"{"cmd":-1,"code":0,"seq":"seq-1"}"#;
        assert!(serde_json::from_str::<Header>(json).is_err());
    }

    /// `name()` / `from_name()` must be exact inverses and cover every variant.
    #[test]
    fn name_round_trip_all_variants() {
        for &cmd in &[
            Command::HeartbeatRequest,
            Command::HelloResponse,
            Command::SysLogToLogServer,
            Command::TraceLogToLogServer,
            Command::RecommendResponse,
        ] {
            let name = cmd.name();
            assert_eq!(Command::from_name(name), Some(cmd), "{name}");
        }
    }

    /// Server-initiated frames (`SERVER_GOODBYE_REQUEST`, `REDIRECT_TO_CLIENT`)
    /// are built by the Java runtime with `seq = null`, which Jackson omits on
    /// the wire. We must accept those frames rather than rejecting them for a
    /// missing required field before `handle_inbound` can send the
    /// `SERVER_GOODBYE_RESPONSE`.
    #[test]
    fn accepts_missing_seq_for_server_initiated_frames() {
        for cmd_name in ["SERVER_GOODBYE_REQUEST", "REDIRECT_TO_CLIENT"] {
            let json = format!(r#"{{"cmd":"{cmd_name}","code":0}}"#);
            let header: Header = serde_json::from_str(&json)
                .unwrap_or_else(|e| panic!("failed to decode {cmd_name} frame without seq: {e}"));
            assert_eq!(header.cmd.name(), cmd_name);
            assert_eq!(header.seq, None, "{cmd_name} seq should be absent");
        }
    }

    /// A header with a seq still round-trips it as `Some`.
    #[test]
    fn present_seq_decodes_as_some_and_is_omitted_when_none() {
        let with_seq = r#"{"cmd":"HELLO_RESPONSE","code":0,"seq":"seq-1"}"#;
        let header: Header = serde_json::from_str(with_seq).unwrap();
        assert_eq!(header.seq.as_deref(), Some("seq-1"));
        // Serializing a None-seq header must omit the field (matches Java
        // JsonUtils, which skips nulls).
        let none_seq = Header {
            cmd: Command::ServerGoodbyeRequest,
            code: 0,
            desc: None,
            seq: None,
            properties: HashMap::new(),
        };
        let json = serde_json::to_string(&none_seq).unwrap();
        assert!(
            !json.contains("seq"),
            "None seq should be omitted, got: {json}"
        );
    }

    /// `RedirectInfo` must carry `ip`/`port` (the Java
    /// `org.apache.eventmesh.common.protocol.tcp.RedirectInfo` wire shape), not
    /// a synthetic `redirect_to`. The runtime serializes it via Jackson with
    /// these exact field names; any other shape would make serde silently drop
    /// the redirect target on decode.
    #[test]
    fn redirect_info_round_trips_java_wire_shape() {
        let java_json = r#"{"ip":"10.0.0.5","port":10000}"#;
        let ri: RedirectInfo = serde_json::from_str(java_json).expect("decode RedirectInfo");
        assert_eq!(ri.ip, "10.0.0.5");
        assert_eq!(ri.port, 10000);

        // Re-serialize and ensure the field names match the Java wire format.
        let out = serde_json::to_string(&ri).unwrap();
        assert!(
            out.contains("\"ip\":\"10.0.0.5\""),
            "expected ip field on the wire, got: {out}"
        );
        assert!(
            out.contains("\"port\":10000"),
            "expected port field on the wire, got: {out}"
        );
        assert!(
            !out.contains("redirect_to"),
            "must NOT emit a redirect_to field, got: {out}"
        );
    }

    /// Missing `ip`/`port` default (mirrors Jackson populating `null`/`0` for
    /// an absent field rather than rejecting the frame).
    #[test]
    fn redirect_info_defaults_missing_fields() {
        let ri: RedirectInfo = serde_json::from_str("{}").expect("decode empty RedirectInfo");
        assert_eq!(ri.ip, "");
        assert_eq!(ri.port, 0);
    }
}
