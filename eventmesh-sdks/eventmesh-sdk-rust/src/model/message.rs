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

//! The core user-facing message type.

use std::collections::HashMap;
use std::fmt;

use crate::common::util::now_millis;
use crate::error::{EventMeshError, Result};

/// A simple, idiomatic EventMesh message: a topic + string content + arbitrary
/// string properties.
///
/// This maps directly to `org.apache.eventmesh.common.EventMeshMessage` on the
/// Java side. It is the primary message type of the SDK; CloudEvents interop is
/// available behind the `cloud_events` feature (see the conversion impls in
/// `transport::grpc::codec`).
///
/// This business model intentionally does not implement serde. Each transport
/// owns a private wire DTO: protobuf for gRPC, form fields for HTTP, and the
/// Java-compatible `body`/`properties` JSON shape for TCP.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EventMeshMessage {
    pub(crate) biz_seq_no: Option<String>,
    pub(crate) unique_id: Option<String>,
    pub(crate) topic: String,
    pub(crate) content: String,
    pub(crate) props: HashMap<String, String>,
    pub(crate) create_time: u64,
    pub(crate) ttl: Option<i64>,
}

impl fmt::Display for EventMeshMessage {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("EventMeshMessage")
            .field("topic", &self.topic)
            .field("biz_seq_no", &self.biz_seq_no)
            .field("unique_id", &self.unique_id)
            .field("content_len", &self.content.len())
            .field("props", &self.props)
            .field("create_time", &self.create_time)
            .finish()
    }
}

impl EventMeshMessage {
    /// Construct a native EventMesh message with its required fields.
    ///
    /// Blank topics are rejected. An empty payload is preserved because it is
    /// valid on the HTTP transport and may be delivered by another SDK.
    pub fn new(topic: impl Into<String>, content: impl Into<String>) -> Result<Self> {
        Self::builder().topic(topic).content(content).build()
    }

    /// Start a builder. Equivalent to [`EventMeshMessageBuilder::default`].
    pub fn builder() -> EventMeshMessageBuilder {
        EventMeshMessageBuilder::default()
    }

    /// Return the destination topic.
    pub fn topic(&self) -> &str {
        &self.topic
    }

    /// Return the text payload.
    pub fn content(&self) -> &str {
        &self.content
    }

    /// Return the optional business sequence number.
    pub fn biz_seq_no(&self) -> Option<&str> {
        self.biz_seq_no.as_deref()
    }

    /// Return the optional application-level unique ID.
    pub fn unique_id(&self) -> Option<&str> {
        self.unique_id.as_deref()
    }

    /// Return all extension properties.
    pub fn properties(&self) -> &HashMap<String, String> {
        &self.props
    }

    /// Return the creation time in epoch milliseconds.
    pub fn create_time(&self) -> u64 {
        self.create_time
    }

    /// Return the optional time-to-live in milliseconds.
    pub fn ttl_millis(&self) -> Option<i64> {
        self.ttl
    }

    /// Insert or overwrite an extension property.
    ///
    /// Transport-specific values such as `ttl` are validated when the message
    /// is sent, while received values are preserved verbatim.
    pub fn set_prop(&mut self, key: impl Into<String>, value: impl Into<String>) -> &mut Self {
        self.props.insert(key.into(), value.into());
        self
    }

    /// Get a property by key.
    pub fn get_prop(&self, key: &str) -> Option<&str> {
        self.props.get(key).map(|s| s.as_str())
    }

    /// Return a copy with an additional extension property.
    pub fn with_property(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.set_prop(key, value);
        self
    }

    /// Validate requirements shared by all publishing transports.
    pub(crate) fn validate_for_publish(&self) -> Result<()> {
        if self.topic.trim().is_empty() {
            return Err(EventMeshError::InvalidMessage("topic is required".into()));
        }

        if let Some(ttl) = self.ttl {
            validate_ttl(ttl)?;
        } else if let Some(ttl) = self.get_prop(crate::common::ProtocolKey::TTL) {
            let ttl = ttl.parse::<i64>().map_err(|_| {
                EventMeshError::InvalidMessage(
                    "ttl property must be a positive integer number of milliseconds".into(),
                )
            })?;
            validate_ttl(ttl)?;
        }
        Ok(())
    }

    /// Validate requirements imposed by the gRPC runtime.
    pub(crate) fn validate_for_grpc_publish(&self) -> Result<()> {
        self.validate_for_publish()?;
        if self.content.is_empty() {
            return Err(EventMeshError::InvalidMessage("content is required".into()));
        }
        Ok(())
    }

    /// Validate requirements imposed by the Java-compatible TCP client.
    pub(crate) fn validate_for_tcp_publish(&self) -> Result<()> {
        self.validate_for_publish()?;
        if self.content.trim().is_empty() {
            return Err(EventMeshError::InvalidMessage("content is required".into()));
        }
        Ok(())
    }
}

fn validate_ttl(ttl: i64) -> Result<()> {
    if !(1..=i64::from(i32::MAX)).contains(&ttl) {
        return Err(EventMeshError::InvalidMessage(format!(
            "ttl must be between 1 and {} milliseconds; EventMesh does not define a never-expire value",
            i32::MAX
        )));
    }
    Ok(())
}

/// Fluent builder for [`EventMeshMessage`].
#[derive(Debug, Clone, Default)]
pub struct EventMeshMessageBuilder {
    biz_seq_no: Option<String>,
    unique_id: Option<String>,
    topic: Option<String>,
    content: Option<String>,
    props: HashMap<String, String>,
    ttl: Option<i64>,
}

impl EventMeshMessageBuilder {
    /// Set the optional business sequence number.
    pub fn biz_seq_no(mut self, v: impl Into<String>) -> Self {
        self.biz_seq_no = Some(v.into());
        self
    }
    /// Set the optional application-level unique ID.
    pub fn unique_id(mut self, v: impl Into<String>) -> Self {
        self.unique_id = Some(v.into());
        self
    }
    /// Set the required destination topic.
    pub fn topic(mut self, v: impl Into<String>) -> Self {
        self.topic = Some(v.into());
        self
    }
    /// Set the required text payload.
    pub fn content(mut self, v: impl Into<String>) -> Self {
        self.content = Some(v.into());
        self
    }
    /// Set the optional time-to-live in milliseconds.
    ///
    /// Its transport-specific range is validated when the message is sent.
    pub fn ttl_millis(mut self, v: i64) -> Self {
        self.ttl = Some(v);
        self
    }
    /// Insert or overwrite an extension property.
    pub fn prop(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.props.insert(key.into(), value.into());
        self
    }
    /// Replace all extension properties.
    pub fn props(mut self, props: HashMap<String, String>) -> Self {
        self.props = props;
        self
    }

    /// Validate the required fields and construct the message.
    ///
    /// The payload must be present but may be empty. Transport-specific
    /// constraints such as TTL range are checked when publishing.
    pub fn build(self) -> Result<EventMeshMessage> {
        let message = EventMeshMessage {
            biz_seq_no: self.biz_seq_no,
            unique_id: self.unique_id,
            topic: self
                .topic
                .ok_or_else(|| EventMeshError::InvalidMessage("topic is required".into()))?,
            content: self
                .content
                .ok_or_else(|| EventMeshError::InvalidMessage("content is required".into()))?,
            props: self.props,
            create_time: now_millis(),
            ttl: self.ttl,
        };
        if message.topic.trim().is_empty() {
            return Err(EventMeshError::InvalidMessage("topic is required".into()));
        }
        Ok(message)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builder_round_trip() {
        let m = EventMeshMessage::builder()
            .topic("t")
            .content("c")
            .biz_seq_no("b")
            .unique_id("u")
            .prop("k", "v")
            .ttl_millis(1000)
            .build()
            .unwrap();
        assert_eq!(m.topic(), "t");
        assert_eq!(m.content(), "c");
        assert_eq!(m.get_prop("k"), Some("v"));
        assert_eq!(m.ttl_millis(), Some(1000));
        assert!(m.create_time() > 0);
    }

    #[test]
    fn construction_requires_a_nonblank_topic_but_preserves_empty_content() {
        assert!(EventMeshMessage::new(" ", "content").is_err());
        assert_eq!(EventMeshMessage::new("topic", "").unwrap().content(), "");
        assert_eq!(
            EventMeshMessage::new("topic", "\t").unwrap().content(),
            "\t"
        );
    }

    #[test]
    fn publish_validation_is_transport_specific() {
        let empty = EventMeshMessage::new("topic", "").unwrap();
        assert!(empty.validate_for_publish().is_ok());
        assert!(empty.validate_for_grpc_publish().is_err());
        assert!(empty.validate_for_tcp_publish().is_err());

        let whitespace = EventMeshMessage::new("topic", "\t").unwrap();
        assert!(whitespace.validate_for_grpc_publish().is_ok());
        assert!(whitespace.validate_for_tcp_publish().is_err());
    }

    #[test]
    fn ttl_is_preserved_at_construction_and_validated_for_publish() {
        let message = EventMeshMessage::builder()
            .topic("topic")
            .content("content")
            .ttl_millis(0)
            .build()
            .unwrap();
        assert_eq!(message.ttl_millis(), Some(0));
        assert!(message.validate_for_publish().is_err());

        let message = EventMeshMessage::new("topic", "content")
            .unwrap()
            .with_property(crate::common::ProtocolKey::TTL, "not-a-number");
        assert_eq!(
            message.get_prop(crate::common::ProtocolKey::TTL),
            Some("not-a-number")
        );
        assert!(message.validate_for_publish().is_err());
    }

    #[test]
    fn publish_validation_accepts_positive_typed_or_property_ttl() {
        EventMeshMessage::builder()
            .topic("topic")
            .content("content")
            .ttl_millis(4_000)
            .build()
            .unwrap();

        EventMeshMessage::new("topic", "content")
            .unwrap()
            .with_property(crate::common::ProtocolKey::TTL, "4000")
            .validate_for_publish()
            .unwrap();
    }
}
