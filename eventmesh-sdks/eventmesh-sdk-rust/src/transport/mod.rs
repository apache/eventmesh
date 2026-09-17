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

#[cfg(all(test, feature = "grpc", feature = "http", feature = "tcp"))]
mod forwarding_tests {
    use super::{grpc, http, tcp};
    use crate::config::{Credentials, Endpoint, GrpcConfig, Identity};
    use crate::model::EventMeshProtocolType;
    use crate::proto_gen::attr_as_str;
    use crate::EventMeshMessage;
    use std::collections::HashMap;

    #[test]
    fn forwarding_rebuilds_transport_metadata_without_changing_the_received_message() {
        let source_config = GrpcConfig::new(Endpoint::new("127.0.0.1", 10205).unwrap())
            .with_identity(Identity::default().with_system("source-system"))
            .with_credentials(
                Credentials::new()
                    .with_basic("source-user", "source-password")
                    .with_token("source-token"),
            );
        let original = EventMeshMessage::builder()
            .topic("orders")
            .content("payload")
            .biz_seq_no("business-id")
            .unique_id("unique-id")
            .ttl_millis(7000)
            .prop("custom", "business-value")
            .prop("tag", "order-created")
            .build()
            .unwrap();
        let wire = grpc::codec::from_event_mesh_message(&original, &source_config, "source-group")
            .unwrap();
        let received = grpc::codec::to_event_mesh_message(&wire).unwrap();
        let before = received.clone();
        assert_eq!(received.properties(), original.properties());
        assert_eq!(received.data_content_type(), Some("text/plain"));
        let debug = format!("{received:?}");
        assert!(!debug.contains("source-password"));
        assert!(!debug.contains("source-token"));
        assert_eq!(received.get_prop("protocoldesc"), None);
        assert_eq!(
            received.delivery_context().unwrap().protocol_description(),
            Some("grpc-cloud-event")
        );

        let identity = Identity::default().with_system("destination-system");
        let credentials = Credentials::new().with_basic("destination-user", "destination-password");
        let mut http_attributes: HashMap<String, String> = http::codec::build_headers(
            http::codec::publish_code(),
            EventMeshProtocolType::EventMeshMessage,
            &identity,
            &credentials,
        )
        .into_iter()
        .map(|(key, value)| (key.to_string(), value))
        .collect();
        let http_fields: HashMap<_, _> =
            http::codec::encode_publish(&received, "destination-group")
                .into_iter()
                .collect();
        // Java's resolver applies extFields after the request's own metadata.
        let extensions: HashMap<String, String> =
            serde_json::from_str(&http_fields["extFields"]).unwrap();
        http_attributes.extend(extensions);
        assert_eq!(http_attributes["protocoldesc"], "http");
        assert_eq!(http_attributes["protocoltype"], "eventmeshmessage");
        assert_eq!(http_attributes["sys"], "destination-system");
        assert_eq!(http_attributes["username"], "destination-user");
        assert_eq!(http_attributes["passwd"], "destination-password");
        assert!(!http_attributes.contains_key("token"));
        assert_eq!(http_fields["ttl"], "7000");
        assert_eq!(http_fields["bizseqno"], "business-id");
        assert_eq!(http_fields["uniqueid"], "unique-id");
        assert_eq!(http_fields["producergroup"], "destination-group");
        assert_eq!(http_attributes["custom"], "business-value");

        let tcp_forwarded = tcp::message::build_message_package(
            &received,
            tcp::frame::Command::AsyncMessageToServer,
        )
        .unwrap();
        let tcp::frame::PackageBody::Text(body) = tcp_forwarded.body else {
            panic!("TCP text body")
        };
        let body: serde_json::Value = serde_json::from_str(&body).unwrap();
        assert!(body["properties"].get("protocoldesc").is_none());
        assert!(body["properties"].get("passwd").is_none());
        assert_eq!(body["properties"]["custom"], "business-value");
        assert_eq!(body["properties"]["ttl"], "7000");
        assert_eq!(
            tcp_forwarded.header.get_string_property("protocoldesc"),
            Some("tcp")
        );
        assert_eq!(
            tcp_forwarded.header.get_string_property("uniqueid"),
            Some("unique-id")
        );

        let destination_config = GrpcConfig::new(Endpoint::new("127.0.0.1", 10205).unwrap())
            .with_identity(identity)
            .with_credentials(credentials);
        let forwarded = grpc::codec::from_event_mesh_message(
            &received,
            &destination_config,
            "destination-group",
        )
        .unwrap();
        assert_eq!(
            attr_as_str(&forwarded.attributes["sys"]),
            "destination-system"
        );
        assert!(!forwarded.attributes.contains_key("token"));
        assert_eq!(
            attr_as_str(&forwarded.attributes["custom"]),
            "business-value"
        );
        assert_eq!(received, before);
    }

    #[test]
    fn tcp_forwarding_preserves_business_and_reply_properties_but_not_transport_overrides() {
        use tcp::frame::{Command, PackageBody};

        for source in ["http", "grpc-cloud-event", "tcp"] {
            let json = serde_json::json!({
                "topic": "orders", "body": "payload",
                "properties": {
                    "protocoldesc": source, "protocoltype": "eventmeshmessage", "protocolversion": "0.3",
                    "env": "old-env", "sys": "old-system", "token": "old-token", "code": "999", "version": "old-version",
                    "ttl": "7000", "custom": "business-value", "tag": "order-created",
                    "seqnum": "request-sequence", "req0sys": "request-system", "req0group": "request-group",
                    "cluster": "reply-cluster", "correlation99id": "broker-correlation", "reply99to99client": "request-client"
                }
            });
            let received =
                tcp::message::parse_message(&PackageBody::Text(json.to_string())).unwrap();
            for command in [
                Command::AsyncMessageToServer,
                Command::BroadcastMessageToServer,
                Command::RequestToServer,
                Command::ResponseToServer,
            ] {
                let forwarded = tcp::message::build_message_package(&received, command).unwrap();
                let PackageBody::Text(body) = forwarded.body else {
                    panic!("TCP text body")
                };
                let body: serde_json::Value = serde_json::from_str(&body).unwrap();
                let properties = body["properties"].as_object().unwrap();
                for key in [
                    "protocoldesc",
                    "protocoltype",
                    "protocolversion",
                    "env",
                    "sys",
                    "token",
                    "code",
                    "version",
                ] {
                    assert!(
                        !properties.contains_key(key),
                        "{source} -> TCP {command:?}: stale {key}"
                    );
                }
                assert_eq!(
                    forwarded.header.get_string_property("protocoldesc"),
                    Some("tcp")
                );
                assert_eq!(
                    forwarded.header.get_string_property("protocoltype"),
                    Some("eventmeshmessage")
                );
                assert_eq!(
                    forwarded.header.get_string_property("protocolversion"),
                    Some("1.0")
                );
                assert_eq!(properties["ttl"], "7000");
                assert_eq!(properties["custom"], "business-value");
                assert_eq!(properties["tag"], "order-created");
                assert_eq!(properties["seqnum"], "request-sequence");
                if command == Command::ResponseToServer {
                    assert_eq!(properties["req0sys"], "request-system");
                    assert_eq!(properties["req0group"], "request-group");
                    assert_eq!(properties["cluster"], "reply-cluster");
                    assert_eq!(properties["correlation99id"], "broker-correlation");
                    assert_eq!(properties["reply99to99client"], "request-client");
                } else {
                    assert!(!properties.contains_key("req0sys"));
                    assert!(!properties.contains_key("req0group"));
                    assert!(!properties.contains_key("cluster"));
                    assert!(!properties.contains_key("correlation99id"));
                    assert!(!properties.contains_key("reply99to99client"));
                }
            }
        }
    }
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
                .prop("custom", "value");
            if let Some(ttl) = ttl {
                builder = builder.ttl_millis(ttl);
            }
            let mut message = builder.build().unwrap();
            // Defense in depth if crate-private code supplies a reserved key.
            message.props.insert("ttl".into(), "99000".into());
            assert_outbound_ttl(&message);
            // Encoding borrows the model; it does not rewrite user properties.
            assert_eq!(message.get_prop("ttl"), Some("99000"));
            assert_eq!(message.ttl_millis(), ttl);
        }
    }
}
