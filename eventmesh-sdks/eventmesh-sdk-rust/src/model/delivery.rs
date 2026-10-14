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

//! Read-only context attached to received native messages.

use std::collections::HashMap;
use std::fmt;

/// Protocol and routing metadata associated with a received native message.
///
/// Created only by SDK decoders and available through
/// [`crate::EventMeshMessage::delivery_context`]. Producers never publish this
/// context. Consumer reply paths use it internally to restore correlation.
/// CloudEvents retain their standard attributes and extensions instead.
#[derive(Clone, Default, PartialEq, Eq)]
pub struct DeliveryContext {
    pub(crate) protocol_type: Option<String>,
    pub(crate) protocol_version: Option<String>,
    pub(crate) protocol_description: Option<String>,
    pub(crate) attributes: HashMap<String, String>,
}

impl DeliveryContext {
    /// The received EventMesh dialect, such as `eventmeshmessage`.
    pub fn protocol_type(&self) -> Option<&str> {
        self.protocol_type.as_deref()
    }

    /// The received EventMesh protocol version.
    pub fn protocol_version(&self) -> Option<&str> {
        self.protocol_version.as_deref()
    }

    /// The received protocol descriptor, such as `http` or `grpc-cloud-event`.
    pub fn protocol_description(&self) -> Option<&str> {
        self.protocol_description.as_deref()
    }

    /// Inspect a received identity, routing, or other known protocol attribute.
    ///
    /// Values are an inbound snapshot, not configuration for a future publish.
    /// Message IDs, TTL, and content type have dedicated message accessors.
    pub fn attribute(&self, key: &str) -> Option<&str> {
        match key {
            "protocoltype" => self.protocol_type(),
            "protocolversion" => self.protocol_version(),
            "protocoldesc" => self.protocol_description(),
            _ => self.attributes.get(key).map(String::as_str),
        }
    }
}

impl fmt::Debug for DeliveryContext {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let attributes: HashMap<_, _> = self
            .attributes
            .iter()
            .map(|(key, value)| {
                (
                    key.as_str(),
                    if matches!(key.as_str(), "passwd" | "token") {
                        "***"
                    } else {
                        value.as_str()
                    },
                )
            })
            .collect();
        f.debug_struct("DeliveryContext")
            .field("protocol_type", &self.protocol_type)
            .field("protocol_version", &self.protocol_version)
            .field("protocol_description", &self.protocol_description)
            .field("attributes", &attributes)
            .finish()
    }
}
