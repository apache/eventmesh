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

use crate::subscription::Subscription as TopicSubscription;

// ---------------------------------------------------------------------------
// Command
// ---------------------------------------------------------------------------

/// Java TCP command names, serialized as JSON strings by the Runtime.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Command {
    /// Client sends heartbeat packet to server.
    HeartbeatRequest,
    /// Server responds to client heartbeat.
    HeartbeatResponse,
    /// Client sends handshake request.
    HelloRequest,
    /// Server responds to handshake.
    HelloResponse,
    /// Client notifies server of active disconnect.
    ClientGoodbyeRequest,
    /// Server replies to client's disconnect notification.
    ClientGoodbyeResponse,
    /// Server notifies client of active disconnect.
    ServerGoodbyeRequest,
    /// Client replies to server's disconnect notification.
    ServerGoodbyeResponse,
    /// Subscription request.
    SubscribeRequest,
    /// Server replies to subscription.
    SubscribeResponse,
    /// Unsubscribe request.
    UnsubscribeRequest,
    /// Server replies to unsubscribe.
    UnsubscribeResponse,
    /// Request to start topic listening.
    ListenRequest,
    /// Server replies to listen request.
    ListenResponse,
    /// Client sends RR request to server.
    RequestToServer,
    /// Server pushes RR request to client.
    RequestToClient,
    /// Client ACKs RR request.
    RequestToClientAck,
    /// Client sends RR reply to server.
    ResponseToServer,
    /// Server pushes RR reply to client.
    ResponseToClient,
    /// Client ACKs RR reply.
    ResponseToClientAck,
    /// Client sends asynchronous events.
    AsyncMessageToServer,
    /// Server ACKs asynchronous events.
    AsyncMessageToServerAck,
    /// Server pushes asynchronous events to client.
    AsyncMessageToClient,
    /// Client ACKs asynchronous events.
    AsyncMessageToClientAck,
    /// Client sends broadcast message.
    BroadcastMessageToServer,
    /// Server ACKs broadcast message.
    BroadcastMessageToServerAck,
    /// Server pushes broadcast message to client.
    BroadcastMessageToClient,
    /// Client ACKs broadcast message.
    BroadcastMessageToClientAck,
    /// Business log reporting.
    #[serde(rename = "SYS_LOG_TO_LOGSERVER")]
    SysLogToLogServer,
    /// RMB tracking log reporting.
    #[serde(rename = "TRACE_LOG_TO_LOGSERVER")]
    TraceLogToLogServer,
    /// Server pushes redirection instruction.
    RedirectToClient,
    /// Client sends registration request.
    RegisterRequest,
    /// Server sends registration result.
    RegisterResponse,
    /// Client sends de-registration request.
    UnregisterRequest,
    /// Server sends de-registration result.
    UnregisterResponse,
    /// Client sends recommendation request.
    RecommendRequest,
    /// Server sends recommendation result.
    RecommendResponse,
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
    /// Build a `UserAgent` from the public identity configuration and the
    /// role's group, tagged with `purpose` ("pub" or "sub").
    ///
    /// `host` is the **client's** local IP (from the identity), NOT the server
    /// address. The Java runtime uses `session.getClient().getHost()` to stamp
    /// the `RSP_IP` CloudEvent extension on every pushed message, so a wrong
    /// value here corrupts tracing/metadata.
    pub fn from_role(
        identity: &crate::config::Identity,
        credentials: &crate::config::Credentials,
        group: &str,
        port: u16,
        purpose: &str,
    ) -> Self {
        Self {
            env: identity.env().to_string(),
            subsystem: identity.system().to_string(),
            path: String::new(),
            pid: identity.process_id().parse().unwrap_or(0),
            host: identity.ip().to_string(),
            port: port as i32,
            version: "1.0".to_string(),
            username: credentials.username().to_string(),
            password: credentials.password().to_string(),
            token: credentials.token().unwrap_or_default().to_string(),
            idc: identity.idc().to_string(),
            group: group.to_string(),
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
    pub topic_list: Vec<TopicSubscription>,
}

impl Subscription {
    pub fn new(topics: Vec<TopicSubscription>) -> Self {
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

    #[test]
    fn rejects_commands_that_java_does_not_emit() {
        for command in ["18", "-1", "256", "\"UNKNOWN\""] {
            let json = format!(r#"{{"cmd":{command},"code":0,"seq":"seq-1"}}"#);
            assert!(serde_json::from_str::<Header>(&json).is_err());
        }
    }

    #[test]
    fn preserves_java_log_command_spelling() {
        for (command, name) in [
            (Command::SysLogToLogServer, "SYS_LOG_TO_LOGSERVER"),
            (Command::TraceLogToLogServer, "TRACE_LOG_TO_LOGSERVER"),
        ] {
            let json = serde_json::to_string(&command).unwrap();
            assert_eq!(json, format!("\"{name}\""));
            assert_eq!(serde_json::from_str::<Command>(&json).unwrap(), command);
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
            assert_eq!(serde_json::to_value(header.cmd).unwrap(), cmd_name);
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
