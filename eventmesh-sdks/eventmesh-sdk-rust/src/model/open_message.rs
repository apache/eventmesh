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

//! Lightweight OpenMessaging-compatible message model.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use super::EventMeshMessage;

/// An OpenMessaging-style message.
///
/// `topic` corresponds to OpenMessaging's destination header.  The Rust SDK
/// keeps it explicit so it can be validated and routed by every EventMesh
/// transport without requiring an OpenMessaging provider implementation.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
pub struct OpenMessage {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub topic: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub body: Option<String>,
    #[serde(default, skip_serializing_if = "HashMap::is_empty")]
    pub headers: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "HashMap::is_empty")]
    pub properties: HashMap<String, String>,
}

impl OpenMessage {
    /// Start building an [`OpenMessage`].
    pub fn builder() -> OpenMessageBuilder {
        OpenMessageBuilder::default()
    }

    /// Convert to the SDK's common transport message representation.
    pub fn to_event_mesh_message(&self) -> EventMeshMessage {
        let mut props = self.properties.clone();
        for (key, value) in &self.headers {
            props
                .entry(format!("header.{key}"))
                .or_insert_with(|| value.clone());
        }
        let mut builder = EventMeshMessage::builder().props(props);
        if let Some(topic) = &self.topic {
            builder = builder.topic(topic);
        }
        if let Some(body) = &self.body {
            builder = builder.content(body);
        }
        builder.build()
    }

    /// Reconstruct an OpenMessaging-style message from a common transport message.
    pub fn from_event_mesh_message(message: EventMeshMessage) -> Self {
        let mut headers = HashMap::new();
        let mut properties = HashMap::new();
        for (key, value) in message.props {
            if let Some(key) = key.strip_prefix("header.") {
                headers.insert(key.to_string(), value);
            } else {
                properties.insert(key, value);
            }
        }
        Self {
            topic: message.topic,
            body: message.content,
            headers,
            properties,
        }
    }
}

/// Fluent builder for [`OpenMessage`].
#[derive(Debug, Clone, Default)]
pub struct OpenMessageBuilder {
    topic: Option<String>,
    body: Option<String>,
    headers: HashMap<String, String>,
    properties: HashMap<String, String>,
}

impl OpenMessageBuilder {
    pub fn topic(mut self, value: impl Into<String>) -> Self {
        self.topic = Some(value.into());
        self
    }

    pub fn body(mut self, value: impl Into<String>) -> Self {
        self.body = Some(value.into());
        self
    }

    pub fn header(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.headers.insert(key.into(), value.into());
        self
    }

    pub fn property(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.properties.insert(key.into(), value.into());
        self
    }

    pub fn build(self) -> OpenMessage {
        OpenMessage {
            topic: self.topic,
            body: self.body,
            headers: self.headers,
            properties: self.properties,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn conversion_preserves_body_and_metadata() {
        let message = OpenMessage::builder()
            .topic("orders")
            .body("created")
            .header("traceparent", "00-abc")
            .property("region", "cn")
            .build();
        let common = message.to_event_mesh_message();
        assert_eq!(common.topic.as_deref(), Some("orders"));
        assert_eq!(common.content.as_deref(), Some("created"));
        assert_eq!(common.get_prop("header.traceparent"), Some("00-abc"));
        assert_eq!(OpenMessage::from_event_mesh_message(common), message);
    }
}
