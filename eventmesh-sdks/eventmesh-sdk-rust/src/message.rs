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

//! Public EventMesh message models.
//!
//! [`Message`] is the protocol-independent envelope accepted by every v2
//! producer and delivered to stream consumers.  It deliberately preserves the
//! three message dialects EventMesh supports instead of flattening them into a
//! lossy set of string fields.  Transport-specific wire encoding remains an
//! implementation detail.

#[cfg(feature = "cloud_events")]
use crate::error::EventMeshError;
use crate::error::Result;

pub use crate::model::{EventMeshMessage, OpenMessage};

/// Which public event dialect a [`Message`] contains.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum MessageKind {
    /// EventMesh's native message model.
    EventMesh,
    /// The OpenMessaging-compatible message model.
    Open,
    /// A CNCF CloudEvent.
    #[cfg(feature = "cloud_events")]
    CloudEvent,
}

/// A public EventMesh event.
///
/// This enum is intentionally not `serde::Serialize`: serializing an enum
/// would produce an SDK-specific tagged representation, which is not any of
/// the EventMesh protocol wire formats.  The selected transport performs the
/// corresponding protobuf, form, or TCP-frame encoding internally.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Message {
    /// EventMesh's native envelope.
    EventMesh(EventMeshMessage),
    /// An OpenMessaging-compatible envelope.
    Open(OpenMessage),
    /// A native CloudEvent.
    #[cfg(feature = "cloud_events")]
    CloudEvent(cloudevents::Event),
}

/// Confirmation returned by a successful EventMesh publish operation.
///
/// A broker-side rejection is returned as [`crate::Error::Server`], so a
/// receipt always represents an accepted operation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PublishReceipt {
    /// The server's acknowledgement code (normally zero).
    pub code: i64,
    /// Optional acknowledgement text.
    pub message: Option<String>,
    /// Optional server processing time in milliseconds.
    pub server_time_millis: Option<i64>,
}

impl PublishReceipt {
    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    pub(crate) fn from_legacy(response: crate::model::PublishResponse) -> Self {
        Self {
            code: response.code.unwrap_or(0),
            message: response.message,
            server_time_millis: response.time,
        }
    }
}

impl Message {
    /// Return the dialect stored in this message.
    pub const fn kind(&self) -> MessageKind {
        match self {
            Self::EventMesh(_) => MessageKind::EventMesh,
            Self::Open(_) => MessageKind::Open,
            #[cfg(feature = "cloud_events")]
            Self::CloudEvent(_) => MessageKind::CloudEvent,
        }
    }

    /// Borrow the native EventMesh message, if this is that dialect.
    pub fn as_event_mesh(&self) -> Option<&EventMeshMessage> {
        match self {
            Self::EventMesh(message) => Some(message),
            _ => None,
        }
    }

    /// Borrow the OpenMessaging message, if this is that dialect.
    pub fn as_open(&self) -> Option<&OpenMessage> {
        match self {
            Self::Open(message) => Some(message),
            _ => None,
        }
    }

    /// Convert this message to the EventMesh native model.
    ///
    /// OpenMessaging conversion is lossless.  CloudEvents are not silently
    /// collapsed here: callers must select a transport that supports the
    /// CloudEvents variant directly.
    pub fn into_event_mesh(self) -> Result<EventMeshMessage> {
        match self {
            Self::EventMesh(message) => Ok(message),
            Self::Open(message) => Ok(message.to_event_mesh_message()),
            #[cfg(feature = "cloud_events")]
            Self::CloudEvent(_) => Err(EventMeshError::Unsupported(
                "converting CloudEvent to EventMeshMessage loses CloudEvents semantics".into(),
            )),
        }
    }

    /// Convert this message to the OpenMessaging model.
    pub fn into_open(self) -> Result<OpenMessage> {
        match self {
            Self::EventMesh(message) => Ok(OpenMessage::from_event_mesh_message(message)),
            Self::Open(message) => Ok(message),
            #[cfg(feature = "cloud_events")]
            Self::CloudEvent(_) => Err(EventMeshError::Unsupported(
                "converting CloudEvent to OpenMessage loses CloudEvents semantics".into(),
            )),
        }
    }
}

impl From<EventMeshMessage> for Message {
    fn from(message: EventMeshMessage) -> Self {
        Self::EventMesh(message)
    }
}

impl From<OpenMessage> for Message {
    fn from(message: OpenMessage) -> Self {
        Self::Open(message)
    }
}

#[cfg(feature = "cloud_events")]
impl From<cloudevents::Event> for Message {
    fn from(event: cloudevents::Event) -> Self {
        Self::CloudEvent(event)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn open_message_round_trips_through_native_conversion() {
        let open = OpenMessage::builder()
            .topic("orders")
            .body("created")
            .build();
        let native = Message::from(open.clone()).into_event_mesh().unwrap();
        assert_eq!(Message::from(native).into_open().unwrap(), open);
    }
}
