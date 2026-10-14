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

//! Inbound metadata separation and outbound transport/reply metadata policy.

use std::collections::HashMap;

use crate::common::metadata::is_reserved_property;
#[cfg(any(feature = "http-client", feature = "tcp"))]
use crate::common::metadata::{is_delivery_property, is_transport_property};
use crate::DeliveryContext;

impl DeliveryContext {
    #[cfg(any(feature = "grpc", feature = "http-client", feature = "tcp"))]
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

    #[cfg(any(feature = "http-client", feature = "tcp"))]
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

/// Decode native wire attributes into their owning fields. Business properties
/// are the remainder; protocol/routing context can never be published as props.
pub(crate) fn decode_native_message(
    mut message: crate::EventMeshMessage,
    mut attributes: std::collections::HashMap<String, String>,
) -> crate::Result<crate::EventMeshMessage> {
    message.ttl = take_wire_ttl(&mut attributes)?;
    let sequence = attributes
        .remove("bizseqno")
        .or(attributes.remove("seqnum"));
    let unique_id = attributes.remove("uniqueid");
    if message.biz_seq_no.is_none() {
        message.biz_seq_no = sequence;
    }
    if message.unique_id.is_none() {
        message.unique_id = unique_id;
    }
    message.data_content_type = attributes.remove("datacontenttype");
    // Topic and content already came from each protocol's authoritative fields.
    for key in ["topic", "subject", "content"] {
        attributes.remove(key);
    }
    message.delivery_context = Some(Box::new(crate::DeliveryContext::take_from(&mut attributes)));
    message.props = attributes;
    Ok(message)
}

/// Extract native-message TTL from wire attributes without duplicating it in
/// the business model's extension properties. Inbound values need only fit i64;
/// publishing applies the outbound range limits separately.
pub(crate) fn take_wire_ttl(
    attributes: &mut std::collections::HashMap<String, String>,
) -> crate::Result<Option<i64>> {
    attributes
        .remove(crate::common::ProtocolKey::TTL)
        .map(|value| {
            value.parse().map_err(|_| {
                crate::Error::InvalidMessage(
                    "wire ttl must be an integer number of milliseconds fitting in i64".into(),
                )
            })
        })
        .transpose()
}

/// Copy a CloudEvent for a new operation without carrying an earlier client's
/// authentication, identity, or delivery route onto the destination transport.
#[cfg(all(
    feature = "cloud_events",
    any(feature = "http-client", feature = "tcp")
))]
pub(crate) fn outbound_cloud_event(
    event: &cloudevents::Event,
    keep_reply_route: bool,
) -> cloudevents::Event {
    let mut outbound = event.clone();
    let removed: Vec<_> = event
        .iter_extensions()
        .map(|(key, _)| key)
        .filter(|key| {
            is_transport_property(key) || (!keep_reply_route && is_delivery_property(key))
        })
        .collect();
    for key in removed {
        outbound.remove_extension(key);
    }
    outbound
}
