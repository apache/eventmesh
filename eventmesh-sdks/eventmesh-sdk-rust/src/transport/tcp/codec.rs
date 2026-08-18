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

//! Binary wire codec for the TCP transport.
//!
//! Frame layout (identical to the Java `Codec`):
//!
//! ```text
//! ┌─────────────┬──────────┬───────────────┬───────────────┬─────────┬────────┐
//! │ Magic Flag  │ Version  │ Package Len   │ Header Len    │ Header  │ Body   │
//! │ "EventMesh" │ "0000"   │ (i32 BE, 4B)  │ (i32 BE, 4B)  │ (JSON)  │ (bytes)│
//! │  (9 bytes)  │ (4 bytes)│               │               │         │        │
//! └─────────────┴──────────┴───────────────┴───────────────┴─────────┴────────┘
//! ```
//!
//! - **Package length** = `13 + header_len + body_len` (does not include the
//!   two 4-byte length fields themselves).
//! - All multi-byte integers are big-endian.

use bytes::{Buf, BufMut, BytesMut};
use tokio_util::codec::{Decoder, Encoder};
use tracing::warn;

use crate::error::{EventMeshError, Result};

use super::frame::{Command, Header, Package, PackageBody, RedirectInfo, Subscription, UserAgent};

/// Magic flag prefix (9 bytes).
const MAGIC_FLAG: &[u8] = b"EventMesh";

/// Protocol version (4 bytes).
const VERSION: &[u8] = b"0000";

/// Length of magic + version (9 + 4 = 13).
const PREFIX_LEN: usize = MAGIC_FLAG.len() + VERSION.len();

/// Maximum frame size: 4 MiB.
const FRAME_MAX_LENGTH: usize = 1024 * 1024 * 4;

/// Header property key for the protocol type (same as
/// `ProtocolKey::PROTOCOL_TYPE` but duplicated here so the tcp module is
/// self-contained).
const PROTOCOL_TYPE_KEY: &str = "protocoltype";

/// CloudEvents protocol name.
const CLOUD_EVENTS_PROTOCOL: &str = "cloudevents";

/// Tokio codec for encoding/decoding EventMesh TCP frames.
#[derive(Debug, Default)]
pub struct TcpCodec;

impl TcpCodec {
    pub fn new() -> Self {
        Self
    }
}

impl Encoder<Package> for TcpCodec {
    type Error = EventMeshError;

    fn encode(&mut self, pkg: Package, buf: &mut BytesMut) -> Result<()> {
        // --- Serialize header ---
        let header_bytes = serde_json::to_vec(&pkg.header)?;

        // --- Serialize body ---
        let is_cloudevents = pkg
            .header
            .get_string_property(PROTOCOL_TYPE_KEY)
            .map(|v| v == CLOUD_EVENTS_PROTOCOL)
            .unwrap_or(false);

        let body_bytes = serialize_body(&pkg.body, is_cloudevents)?;

        let header_len = header_bytes.len();
        let body_len = body_bytes.len();
        let total_len = PREFIX_LEN + header_len + body_len;

        if total_len > FRAME_MAX_LENGTH {
            return Err(EventMeshError::InvalidArgument(format!(
                "message size {total_len} exceeds limit {FRAME_MAX_LENGTH}"
            )));
        }

        // Reserve enough space for the entire frame.
        let frame_len = PREFIX_LEN + 4 + 4 + header_len + body_len;
        buf.reserve(frame_len);

        // Write frame.
        buf.put_slice(MAGIC_FLAG);
        buf.put_slice(VERSION);
        buf.put_i32(total_len as i32);
        buf.put_i32(header_len as i32);
        buf.put_slice(&header_bytes);
        if body_len > 0 {
            buf.put_slice(&body_bytes);
        }

        Ok(())
    }
}

impl Decoder for TcpCodec {
    type Item = Package;
    type Error = EventMeshError;

    fn decode(&mut self, buf: &mut BytesMut) -> Result<Option<Package>> {
        // We need at least the prefix + two length fields to know the frame size.
        let min_header = PREFIX_LEN + 4 + 4;
        if buf.len() < min_header {
            return Ok(None);
        }

        // Peek at the lengths without consuming.
        let magic = &buf[..MAGIC_FLAG.len()];
        if magic != MAGIC_FLAG {
            return Err(EventMeshError::Tcp(format!(
                "invalid magic flag: expected {:?}, got {:?}",
                String::from_utf8_lossy(MAGIC_FLAG),
                String::from_utf8_lossy(magic),
            )));
        }

        let version = &buf[MAGIC_FLAG.len()..PREFIX_LEN];
        if version != VERSION {
            return Err(EventMeshError::Tcp(format!(
                "invalid version: expected {:?}, got {:?}",
                String::from_utf8_lossy(VERSION),
                String::from_utf8_lossy(version),
            )));
        }

        // Read package length and header length.
        let total_len = (&buf[PREFIX_LEN..PREFIX_LEN + 4]).get_i32() as usize;
        let header_len = (&buf[PREFIX_LEN + 4..PREFIX_LEN + 8]).get_i32() as usize;

        if total_len > FRAME_MAX_LENGTH {
            return Err(EventMeshError::Tcp(format!(
                "frame length {total_len} exceeds limit {FRAME_MAX_LENGTH}"
            )));
        }

        let body_len = total_len
            .checked_sub(PREFIX_LEN)
            .and_then(|v| v.checked_sub(header_len))
            .ok_or_else(|| {
                EventMeshError::Tcp(format!(
                    "invalid frame: total_len={total_len}, header_len={header_len}"
                ))
            })?;

        // Total bytes on the wire = prefix + 4 (pkg len) + 4 (hdr len) + header + body.
        let frame_bytes = PREFIX_LEN + 4 + 4 + header_len + body_len;
        if buf.len() < frame_bytes {
            // Not enough data yet; wait for more.
            buf.reserve(frame_bytes - buf.len());
            return Ok(None);
        }

        // Consume the frame.
        let mut data = buf.split_to(frame_bytes);

        // Skip past prefix + two length fields.
        data.advance(PREFIX_LEN + 4 + 4);

        // Read header.
        let header: Header = if header_len > 0 {
            let hdr_data = data.copy_to_bytes(header_len);
            serde_json::from_slice(&hdr_data)?
        } else {
            // Java's `parseHeader` returns null when `headerLength <= 0`,
            // then the inbound handler does `Preconditions.checkNotNull(header)`
            // → exception → `ctx.close()`. We mirror that by erroring here.
            // Returning `Ok(None)` would be wrong: this frame has already been
            // consumed from `buf` via `split_to`, and `Ok(None)` means "need
            // more bytes", so the next call would be fed the body bytes and
            // fail with `invalid magic flag`, desyncing the stream.
            warn!("received frame with empty header");
            return Err(EventMeshError::Tcp(
                "received frame with empty header".into(),
            ));
        };

        // Read body bytes.
        let body_bytes = if body_len > 0 {
            let b = data.copy_to_bytes(body_len);
            b.to_vec()
        } else {
            Vec::new()
        };

        // Deserialize body based on command.
        let body = deserialize_body(&header.cmd, &body_bytes);

        Ok(Some(Package { header, body }))
    }
}

/// Serialize a [`PackageBody`] to bytes.
///
/// CloudEvents bodies (`Bytes` variant when `is_cloudevents` is true) are
/// written as-is; everything else is JSON-serialized (or in the case of
/// `Text`, returned as UTF-8).
fn serialize_body(body: &PackageBody, is_cloudevents: bool) -> Result<Vec<u8>> {
    Ok(match body {
        PackageBody::Empty => Vec::new(),
        PackageBody::Bytes(b) => {
            if is_cloudevents {
                b.clone()
            } else {
                serde_json::to_vec(b)?
            }
        }
        PackageBody::Text(s) => s.as_bytes().to_vec(),
        PackageBody::UserAgent(ua) => serde_json::to_vec(ua.as_ref())?,
        PackageBody::Subscription(sub) => serde_json::to_vec(sub)?,
        PackageBody::RedirectInfo(ri) => serde_json::to_vec(ri)?,
    })
}

/// Deserialize a body based on the header's command type (mirrors Java
/// `Codec.deserializeBody`).
fn deserialize_body(cmd: &Command, body_bytes: &[u8]) -> PackageBody {
    if body_bytes.is_empty() {
        return PackageBody::Empty;
    }

    let body_str = match std::str::from_utf8(body_bytes) {
        Ok(s) => s.to_string(),
        Err(_) => return PackageBody::Bytes(body_bytes.to_vec()),
    };

    match cmd {
        Command::HelloRequest | Command::RecommendRequest => {
            match serde_json::from_str::<UserAgent>(&body_str) {
                Ok(ua) => PackageBody::UserAgent(Box::new(ua)),
                Err(_) => PackageBody::Text(body_str),
            }
        }
        Command::SubscribeRequest | Command::UnsubscribeRequest => {
            match serde_json::from_str::<Subscription>(&body_str) {
                Ok(sub) => PackageBody::Subscription(sub),
                Err(_) => PackageBody::Text(body_str),
            }
        }
        Command::RedirectToClient => match serde_json::from_str::<RedirectInfo>(&body_str) {
            Ok(ri) => PackageBody::RedirectInfo(ri),
            Err(_) => PackageBody::Text(body_str),
        },
        // All message/ACK/response commands: defer to protocol layer as raw text.
        _ => PackageBody::Text(body_str),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip_heartbeat() {
        let mut codec = TcpCodec::new();
        let mut buf = BytesMut::new();

        let pkg = Package::new(Header::new(Command::HeartbeatRequest, "1234567890"));
        codec.encode(pkg.clone(), &mut buf).expect("encode");

        // Frame should start with magic + version.
        assert_eq!(&buf[..9], MAGIC_FLAG);
        assert_eq!(&buf[9..13], VERSION);

        let decoded = codec.decode(&mut buf).expect("decode");
        let decoded = decoded.expect("should have a frame");

        assert_eq!(decoded.header.cmd, Command::HeartbeatRequest);
        assert_eq!(decoded.header.seq.as_deref(), Some("1234567890"));
        assert!(matches!(decoded.body, PackageBody::Empty));
        assert!(buf.is_empty(), "buffer should be fully consumed");
    }

    #[test]
    fn round_trip_with_body() {
        let mut codec = TcpCodec::new();
        let mut buf = BytesMut::new();

        let pkg = Package::new(Header::new(Command::HelloRequest, "abcdefghij")).with_body(
            PackageBody::UserAgent(Box::new(UserAgent {
                env: "prod".into(),
                group: "g1".into(),
                purpose: "pub".into(),
                pid: 42,
                ..Default::default()
            })),
        );

        codec.encode(pkg, &mut buf).expect("encode");
        let decoded = codec
            .decode(&mut buf)
            .expect("decode")
            .expect("frame present");

        assert_eq!(decoded.header.cmd, Command::HelloRequest);
        match decoded.body {
            PackageBody::UserAgent(ua) => {
                assert_eq!(ua.env, "prod");
                assert_eq!(ua.group, "g1");
                assert_eq!(ua.purpose, "pub");
                assert_eq!(ua.pid, 42);
            }
            other => panic!("expected UserAgent body, got {other:?}"),
        }
    }

    #[test]
    fn partial_frame_returns_none() {
        let mut codec = TcpCodec::new();
        let mut buf = BytesMut::new();

        let pkg = Package::new(Header::new(Command::HeartbeatRequest, "12345"));
        codec.encode(pkg, &mut buf).expect("encode");

        // Only feed the first 10 bytes.
        let mut partial = buf.split_to(10);
        let result = codec.decode(&mut partial).expect("decode partial");
        assert!(result.is_none(), "should return None for partial frame");
    }

    #[test]
    fn invalid_magic_rejected() {
        let mut codec = TcpCodec::new();
        let mut buf = BytesMut::new();
        buf.put_slice(b"BADMAGIC!");
        buf.put_slice(VERSION);
        buf.put_i32(100);
        buf.put_i32(0);

        let result = codec.decode(&mut buf);
        assert!(result.is_err(), "should reject bad magic");
    }

    #[test]
    fn text_body_round_trip() {
        let mut codec = TcpCodec::new();
        let mut buf = BytesMut::new();

        let json = r#"{"topic":"test","content":"hello"}"#;
        let pkg = Package::new(Header::new(Command::AsyncMessageToServerAck, "1234567890"))
            .with_body(PackageBody::Text(json.to_string()));

        codec.encode(pkg, &mut buf).expect("encode");
        let decoded = codec
            .decode(&mut buf)
            .expect("decode")
            .expect("frame present");

        match decoded.body {
            PackageBody::Text(s) => assert_eq!(s, json),
            other => panic!("expected Text body, got {other:?}"),
        }
    }
}
