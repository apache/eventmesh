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

//! Private transport implementations and wire codecs.
//!
//! Public clients delegate to concrete producer and consumer types. Each
//! transport exposes only the operations supported by its wire protocol.

#[cfg(feature = "grpc")]
pub mod grpc;

#[cfg(feature = "http")]
pub mod http;

#[cfg(feature = "tcp")]
pub mod tcp;

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

#[cfg(all(test, feature = "grpc", feature = "http", feature = "tcp"))]
mod ttl_tests {
    use super::{grpc, http, tcp};
    use crate::config::{Endpoint, GrpcConfig};
    use crate::proto_gen::{attr_as_str, attr_str};
    use crate::{EventMeshMessage, Result};
    use std::collections::HashMap;

    fn config() -> GrpcConfig {
        GrpcConfig::new(Endpoint::new("127.0.0.1", 10205).unwrap())
    }

    fn decode_java_message(protocol: &str, ttl: Option<&str>) -> Result<EventMeshMessage> {
        let mut properties = HashMap::from([("custom".to_string(), "value".to_string())]);
        if let Some(ttl) = ttl {
            properties.insert("ttl".into(), ttl.into());
        }
        match protocol {
            "http" => {
                let form = http::codec::form_encode(&[
                    ("topic".into(), "orders".into()),
                    ("content".into(), "payload".into()),
                    (
                        "extFields".into(),
                        serde_json::to_string(&properties).unwrap(),
                    ),
                ]);
                http::codec::parse_push_body(&form)?.to_event_mesh_message()
            }
            "grpc" => {
                let message = EventMeshMessage::new("orders", "payload")?;
                let mut wire = grpc::codec::from_event_mesh_message(&message, &config(), "group")?;
                wire.attributes.remove("ttl");
                for (key, value) in properties {
                    wire.attributes.insert(key, attr_str(value));
                }
                grpc::codec::to_event_mesh_message(&wire)
            }
            "tcp" => {
                let json = serde_json::json!({
                    "topic": "orders", "body": "payload", "properties": properties
                });
                tcp::message::parse_message(&tcp::frame::PackageBody::Text(json.to_string()))
                    .ok_or_else(|| crate::Error::InvalidMessage("invalid TCP message".into()))
            }
            _ => unreachable!(),
        }
    }

    fn assert_outbound_ttl(message: &EventMeshMessage) {
        let expected = message.ttl_millis().unwrap_or(4000).to_string();
        let http_fields: HashMap<_, _> = http::codec::encode_publish(message, "group")
            .into_iter()
            .collect();
        assert_eq!(http_fields.get("ttl"), Some(&expected));
        let extensions: HashMap<String, String> =
            serde_json::from_str(http_fields.get("extFields").unwrap()).unwrap();
        assert!(!extensions.contains_key("ttl"));
        let grpc_wire = grpc::codec::from_event_mesh_message(message, &config(), "group").unwrap();
        assert_eq!(attr_as_str(&grpc_wire.attributes["ttl"]), expected);

        let tcp_wire =
            tcp::message::build_message_package(message, tcp::frame::Command::AsyncMessageToServer)
                .unwrap();
        let expected_tcp = message.ttl_millis().map(|ttl| ttl.to_string());
        assert_eq!(
            tcp_wire.header.get_string_property("ttl"),
            expected_tcp.as_deref()
        );
        let tcp::frame::PackageBody::Text(body) = &tcp_wire.body else {
            panic!("TCP body")
        };
        let body: serde_json::Value = serde_json::from_str(body).unwrap();
        assert_eq!(body["properties"]["ttl"].as_str(), expected_tcp.as_deref());
        let decoded = tcp::message::parse_message(&tcp_wire.body).unwrap();
        assert_eq!(decoded.ttl_millis(), message.ttl_millis());
        assert_eq!(decoded.get_prop("ttl"), None);
    }

    #[test]
    fn native_ttl_is_decoded_into_one_field_on_every_transport() {
        for protocol in ["http", "grpc", "tcp"] {
            for ttl in [
                None,
                Some("4000"),
                Some("0"),
                Some("-1"),
                Some("2147483648"),
                Some("9223372036854775807"),
            ] {
                let message = decode_java_message(protocol, ttl).unwrap();
                assert_eq!(
                    message.ttl_millis(),
                    ttl.map(|value| value.parse().unwrap()),
                    "{protocol}"
                );
                assert_eq!(message.get_prop("ttl"), None, "{protocol}");
                assert_eq!(message.get_prop("custom"), Some("value"));
            }
            for ttl in ["", "invalid", "9223372036854775808"] {
                assert!(
                    decode_java_message(protocol, Some(ttl)).is_err(),
                    "{protocol}: {ttl}"
                );
            }
            // A Java message received on any transport can be forwarded using
            // each native encoder without losing its dedicated TTL.
            assert_outbound_ttl(&decode_java_message(protocol, Some("7000")).unwrap());
        }
    }

    #[test]
    fn native_encoders_ignore_generic_ttl_properties() {
        for ttl in [None, Some(7000)] {
            let mut builder = EventMeshMessage::builder()
                .topic("orders")
                .content("payload")
                .prop("ttl", "99000")
                .prop("custom", "value");
            if let Some(ttl) = ttl {
                builder = builder.ttl_millis(ttl);
            }
            let message = builder.build().unwrap();
            assert_outbound_ttl(&message);
            // Encoding borrows the model; it does not rewrite user properties.
            assert_eq!(message.get_prop("ttl"), Some("99000"));
            assert_eq!(message.ttl_millis(), ttl);
        }
    }
}
