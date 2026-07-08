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

use serde::{Deserialize, Serialize};

use crate::common::util::now_millis;

/// A simple, idiomatic EventMesh message: a topic + string content + arbitrary
/// string properties.
///
/// This maps directly to `org.apache.eventmesh.common.EventMeshMessage` on the
/// Java side. It is the primary message type of the SDK; CloudEvents interop is
/// available behind the `cloud_events` feature (see the conversion impls in
/// `transport::grpc::codec`).
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct EventMeshMessage {
    /// Optional business sequence number (correlates request/reply).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub biz_seq_no: Option<String>,
    /// Optional application-level unique id.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub unique_id: Option<String>,
    /// The destination topic. Required for publish.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub topic: Option<String>,
    /// The message payload as a string.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub content: Option<String>,
    /// Free-form string properties (become CloudEvent attributes on the wire).
    #[serde(default, skip_serializing_if = "HashMap::is_empty")]
    pub props: HashMap<String, String>,
    /// Creation time, epoch milliseconds.
    pub create_time: u64,
    /// Optional TTL in milliseconds.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ttl: Option<i64>,
}

impl Default for EventMeshMessage {
    fn default() -> Self {
        Self {
            biz_seq_no: None,
            unique_id: None,
            topic: None,
            content: None,
            props: HashMap::new(),
            create_time: now_millis(),
            ttl: None,
        }
    }
}

impl fmt::Display for EventMeshMessage {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("EventMeshMessage")
            .field("topic", &self.topic)
            .field("biz_seq_no", &self.biz_seq_no)
            .field("unique_id", &self.unique_id)
            .field("content_len", &self.content.as_ref().map(|c| c.len()))
            .field("props", &self.props)
            .field("create_time", &self.create_time)
            .finish()
    }
}

impl EventMeshMessage {
    /// Start a builder. Equivalent to [`EventMeshMessageBuilder::default`].
    pub fn builder() -> EventMeshMessageBuilder {
        EventMeshMessageBuilder::default()
    }

    /// Insert/overwrite a property.
    pub fn set_prop(&mut self, key: impl Into<String>, value: impl Into<String>) -> &mut Self {
        self.props.insert(key.into(), value.into());
        self
    }

    /// Get a property by key.
    pub fn get_prop(&self, key: &str) -> Option<&str> {
        self.props.get(key).map(|s| s.as_str())
    }
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
    pub fn biz_seq_no(mut self, v: impl Into<String>) -> Self {
        self.biz_seq_no = Some(v.into());
        self
    }
    pub fn unique_id(mut self, v: impl Into<String>) -> Self {
        self.unique_id = Some(v.into());
        self
    }
    pub fn topic(mut self, v: impl Into<String>) -> Self {
        self.topic = Some(v.into());
        self
    }
    pub fn content(mut self, v: impl Into<String>) -> Self {
        self.content = Some(v.into());
        self
    }
    pub fn ttl_millis(mut self, v: i64) -> Self {
        self.ttl = Some(v);
        self
    }
    pub fn prop(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.props.insert(key.into(), value.into());
        self
    }
    pub fn props(mut self, props: HashMap<String, String>) -> Self {
        self.props = props;
        self
    }

    pub fn build(self) -> EventMeshMessage {
        EventMeshMessage {
            biz_seq_no: self.biz_seq_no,
            unique_id: self.unique_id,
            topic: self.topic,
            content: self.content,
            props: self.props,
            create_time: now_millis(),
            ttl: self.ttl,
        }
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
            .build();
        assert_eq!(m.topic.as_deref(), Some("t"));
        assert_eq!(m.content.as_deref(), Some("c"));
        assert_eq!(m.get_prop("k"), Some("v"));
        assert_eq!(m.ttl, Some(1000));
        assert!(m.create_time > 0);
    }

    #[test]
    fn serde_round_trip() {
        let m = EventMeshMessage::builder().topic("t").content("c").build();
        let json = serde_json::to_string(&m).unwrap();
        let back: EventMeshMessage = serde_json::from_str(&json).unwrap();
        assert_eq!(m, back);
    }
}
