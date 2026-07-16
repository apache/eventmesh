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
    /// Construct an OpenMessaging-compatible message.
    pub fn new(topic: impl Into<String>, body: impl Into<String>) -> Self {
        Self {
            topic: Some(topic.into()),
            body: Some(body.into()),
            headers: HashMap::new(),
            properties: HashMap::new(),
        }
    }

    /// Start building an [`OpenMessage`].
    pub(crate) fn builder() -> OpenMessageBuilder {
        OpenMessageBuilder::default()
    }

    /// Convert to the SDK's common transport message representation.
    pub fn to_event_mesh_message(&self) -> EventMeshMessage {
        // Keep properties and headers in separate wire namespaces. OpenMessage
        // permits arbitrary property keys, including `header.*`, so storing a
        // header directly as `header.{key}` while leaving properties unescaped
        // makes the conversion lossy.
        let mut props: HashMap<String, String> = self
            .properties
            .iter()
            .map(|(key, value)| (format!("property.{key}"), value.clone()))
            .collect();
        for (key, value) in &self.headers {
            props.insert(format!("header.{key}"), value.clone());
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
            } else if let Some(key) = key.strip_prefix("property.") {
                properties.insert(key.to_string(), value);
            } else {
                // Preserve messages produced by SDK versions before the
                // property namespace was introduced.
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

    /// Return a copy with an OpenMessaging header.
    pub fn with_header(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.headers.insert(key.into(), value.into());
        self
    }

    /// Return a copy with an OpenMessaging property.
    pub fn with_property(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.properties.insert(key.into(), value.into());
        self
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
        assert_eq!(common.get_prop("property.region"), Some("cn"));
        assert_eq!(OpenMessage::from_event_mesh_message(common), message);
    }

    #[test]
    fn conversion_preserves_colliding_header_prefixed_property() {
        let message = OpenMessage::builder()
            .header("traceparent", "header-value")
            .property("header.traceparent", "property-value")
            .build();

        let common = message.to_event_mesh_message();
        assert_eq!(common.get_prop("header.traceparent"), Some("header-value"));
        assert_eq!(
            common.get_prop("property.header.traceparent"),
            Some("property-value")
        );
        assert_eq!(OpenMessage::from_event_mesh_message(common), message);
    }
}
