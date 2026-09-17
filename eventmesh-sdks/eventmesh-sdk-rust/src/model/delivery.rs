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
    protocol_type: Option<String>,
    protocol_version: Option<String>,
    protocol_description: Option<String>,
    attributes: HashMap<String, String>,
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

    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    pub(crate) fn take_from(attributes: &mut HashMap<String, String>) -> Self {
        let mut context = Self {
            protocol_type: attributes.remove("protocoltype"),
            protocol_version: attributes.remove("protocolversion"),
            protocol_description: attributes.remove("protocoldesc"),
            attributes: HashMap::new(),
        };
        attributes.retain(|key, value| {
            if is_reserved_property(key) {
                context.attributes.insert(key.clone(), value.clone());
                false
            } else {
                true
            }
        });
        context
    }

    #[cfg(any(feature = "http", feature = "tcp"))]
    pub(crate) fn insert_missing(&mut self, key: &str, value: String) {
        match key {
            "protocoltype" => {
                self.protocol_type.get_or_insert(value);
            }
            "protocolversion" => {
                self.protocol_version.get_or_insert(value);
            }
            "protocoldesc" => {
                self.protocol_description.get_or_insert(value);
            }
            _ if is_transport_property(key) || is_delivery_property(key) => {
                self.attributes.entry(key.to_string()).or_insert(value);
            }
            _ => {}
        }
    }

    #[cfg(feature = "tcp")]
    pub(crate) fn reply_attributes(&self) -> impl Iterator<Item = (&String, &String)> {
        self.attributes
            .iter()
            .filter(|(key, _)| is_delivery_property(key))
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

pub(crate) fn is_transport_property(key: &str) -> bool {
    matches!(
        key,
        "protocoltype"
            | "protocolversion"
            | "protocoldesc"
            | "code"
            | "version"
            | "env"
            | "idc"
            | "ip"
            | "pid"
            | "sys"
            | "username"
            | "passwd"
            | "token"
            | "language"
            | "producergroup"
    )
}

// Exact runtime/storage keys, not a prefix rule: a business property named
// `requestid`, for example, must not be mistaken for a routing attribute.
pub(crate) fn is_delivery_property(key: &str) -> bool {
    matches!(
        key,
        "cluster"
            | "consumergroup"
            | "url"
            | "clienttype"
            | "submessagetype"
            | "msgtype"
            | "req0sys"
            | "req0ip"
            | "req0idc"
            | "req0group"
            | "rsp0sys"
            | "rsp0ip"
            | "rsp0idc"
            | "rsp0group"
            | "rsp0url"
            | "reqc2eventmeshtimestamp"
            | "reqeventmesh2mqtimestamp"
            | "reqmq2eventmeshtimestamp"
            | "reqeventmesh2ctimestamp"
            | "rspc2eventmeshtimestamp"
            | "rspeventmesh2mqtimestamp"
            | "rspmq2eventmeshtimestamp"
            | "rspeventmesh2ctimestamp"
            | "reqsendeventmeship"
            | "reqreceiveeventmeship"
            | "rspsendeventmeship"
            | "rspreceiveeventmeship"
            | "correlation99id"
            | "reply99to99client"
            | "arrive99time"
            | "push99reply99time"
            | "msg99type"
            | "bornhost"
            | "borntimestamp"
            | "storehost"
            | "storetimestamp"
    )
}

pub(crate) fn is_reserved_property(key: &str) -> bool {
    is_transport_property(key)
        || is_delivery_property(key)
        || matches!(
            key,
            "ttl"
                | "seqnum"
                | "bizseqno"
                | "uniqueid"
                | "topic"
                | "content"
                | "datacontenttype"
                | "id"
                | "source"
                | "specversion"
                | "type"
                | "dataschema"
                | "subject"
                | "time"
                | "statuscode"
                | "responsemessage"
                | "subscription_reply"
        )
}
