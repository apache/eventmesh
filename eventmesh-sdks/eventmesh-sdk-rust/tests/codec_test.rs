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

use eventmesh::{
    config::{Endpoint, EndpointSet},
    message::{EventMeshMessage, Message, MessageKind},
    subscription::{DeliveryMode, DeliveryType, Subscription},
};

#[cfg(feature = "http")]
use eventmesh::http::codec::{parse_push_body, PushMessageRequestBody, WebhookReply};

#[test]
fn message_kind_is_explicit() {
    let message = Message::from(EventMeshMessage::new("orders", "created").unwrap());
    assert_eq!(message.kind(), MessageKind::EventMesh);
}

#[test]
fn message_construction_requires_fields_and_builder_is_public() {
    assert!(EventMeshMessage::new("", "created").is_err());
    let transport_specific_ttl = EventMeshMessage::builder()
        .topic("orders")
        .content("")
        .ttl_millis(0)
        .build()
        .unwrap();
    assert_eq!(transport_specific_ttl.content(), "");
    assert_eq!(transport_specific_ttl.ttl_millis(), Some(0));

    let message = EventMeshMessage::builder()
        .topic("orders")
        .content("created")
        .unique_id("event-1")
        .ttl_millis(1_000)
        .build()
        .unwrap();
    assert_eq!(message.topic(), "orders");
    assert_eq!(message.content(), "created");
    assert_eq!(message.unique_id(), Some("event-1"));
    assert_eq!(message.ttl_millis(), Some(1_000));
}

#[test]
fn subscriptions_have_rust_style_defaults_and_setters() {
    let subscription = Subscription::new("orders")
        .with_delivery_mode(DeliveryMode::Broadcast)
        .with_delivery_type(DeliveryType::Async);
    assert_eq!(subscription.topic, "orders");
    assert_eq!(subscription.delivery_mode, DeliveryMode::Broadcast);
}

#[test]
fn endpoint_sets_require_members() {
    assert!(EndpointSet::new(Vec::new()).is_err());
    assert_eq!(
        Endpoint::new("::1", 10_205).unwrap().authority(),
        "[::1]:10205"
    );
}

#[cfg(feature = "http")]
#[test]
fn custom_webhook_codec_is_public() {
    let parsed: PushMessageRequestBody =
        parse_push_body("content=hello&topic=orders&bizseqno=seq-1&uniqueId=id-1")
            .expect("decode webhook body");
    for protocol in [None, Some("eventmeshmessage")] {
        let mut headers = http::HeaderMap::new();
        if let Some(protocol) = protocol {
            headers.insert("protocoltype", protocol.parse().unwrap());
        }
        let message = parsed.to_message(&headers).unwrap();
        let message = message.as_event_mesh().unwrap();
        assert_eq!(message.topic(), "orders");
        assert_eq!(message.content(), "hello");
        assert_eq!(message.biz_seq_no(), Some("seq-1"));
        assert_eq!(message.unique_id(), Some("id-1"));
    }
    assert_eq!(WebhookReply::ok().ret_code, 1);
}

#[cfg(feature = "http")]
mod webhook_messages {
    use super::*;
    use eventmesh::Error;
    use http::{HeaderMap, HeaderValue};

    fn push(content: &str, protocol: Option<&str>) -> PushMessageRequestBody {
        let extfields = protocol
            .map(|protocol| serde_json::json!({"protocoltype": protocol}).to_string())
            .unwrap_or_default();
        let body = serde_urlencoded::to_string([
            ("topic", "orders"),
            ("content", content),
            ("extFields", &extfields),
        ])
        .unwrap();
        parse_push_body(&body).unwrap()
    }

    #[test]
    fn rejects_unknown_protocol_in_headers_or_extensions() {
        let mut headers = HeaderMap::new();
        headers.insert("protocoltype", "openmessage".parse().unwrap());
        for (body, headers) in [
            (push("created", None), headers),
            (push("created", Some("openmessage")), HeaderMap::new()),
        ] {
            assert!(matches!(
                body.to_message(&headers),
                Err(Error::Protocol {
                    transport: "http",
                    ..
                })
            ));
        }
    }

    #[test]
    fn rejects_conflicting_protocol_sources() {
        let mut headers = HeaderMap::new();
        headers.insert("protocoltype", "eventmeshmessage".parse().unwrap());
        assert!(matches!(
            push("created", Some("cloudevents")).to_message(&headers),
            Err(Error::Protocol {
                transport: "http",
                ..
            })
        ));
    }

    #[test]
    fn rejects_malformed_protocol_metadata() {
        let mut headers = HeaderMap::new();
        headers.insert("protocoltype", HeaderValue::from_bytes(&[0xff]).unwrap());
        assert!(matches!(
            push("created", None).to_message(&headers),
            Err(Error::Protocol {
                transport: "http",
                ..
            })
        ));

        let mut body = push("created", None);
        body.extfields = Some("invalid JSON".into());
        assert!(matches!(
            body.to_message(&HeaderMap::new()),
            Err(Error::Protocol {
                transport: "http",
                ..
            })
        ));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn preserves_cloud_events_from_either_or_both_protocol_sources() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("event-1")
            .source("urn:test")
            .ty("orders.created")
            .subject("orders")
            .data(
                "application/json",
                serde_json::json!({"text": "订单 + & ="}),
            )
            .extension("custom", "value")
            .build()
            .unwrap();
        let content = serde_json::to_string(&event).unwrap();
        for (header, extension) in [(true, false), (false, true), (true, true)] {
            let mut headers = HeaderMap::new();
            if header {
                headers.insert("protocoltype", "cloudevents".parse().unwrap());
            }
            let body = push(&content, extension.then_some("cloudevents"));
            assert_eq!(
                body.to_message(&headers).unwrap(),
                Message::CloudEvent(event.clone())
            );
        }
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn rejects_malformed_cloud_event_payload() {
        assert!(matches!(
            push("invalid JSON", Some("cloudevents")).to_message(&HeaderMap::new()),
            Err(Error::Codec(_))
        ));
    }

    #[cfg(not(feature = "cloud_events"))]
    #[test]
    fn rejects_cloud_events_when_feature_is_disabled() {
        let mut headers = HeaderMap::new();
        headers.insert("protocoltype", "cloudevents".parse().unwrap());
        for (body, headers) in [
            (push("{}", None), headers),
            (push("{}", Some("cloudevents")), HeaderMap::new()),
        ] {
            assert!(matches!(
                body.to_message(&headers),
                Err(Error::Unsupported(_))
            ));
        }
    }
}

#[cfg(feature = "http")]
#[test]
fn native_webhook_exposes_read_only_context_and_typed_fields() {
    let ext = serde_json::json!({
        "protocoltype": "eventmeshmessage", "protocoldesc": "http", "protocolversion": "1.0",
        "sys": "source-system", "cluster": "source-cluster", "correlation99id": "correlation",
        "ttl": "7000", "datacontenttype": "application/json", "custom": "business-value",
        "seqnum": "old-sequence", "uniqueid": "old-id"
    })
    .to_string();
    let body = serde_urlencoded::to_string([
        ("topic", "orders"),
        ("content", "{}"),
        ("bizseqno", "form-sequence"),
        ("uniqueId", "form-id"),
        ("extFields", ext.as_str()),
    ])
    .unwrap();
    let mut headers = http::HeaderMap::new();
    headers.insert("language", "JAVA".parse().unwrap());
    let message = parse_push_body(&body)
        .unwrap()
        .to_message(&headers)
        .unwrap();
    let mut message = message.into_event_mesh().unwrap();
    assert_eq!(message.properties().len(), 1);
    assert_eq!(message.get_prop("custom"), Some("business-value"));
    assert_eq!(message.biz_seq_no(), Some("form-sequence"));
    assert_eq!(message.unique_id(), Some("form-id"));
    assert_eq!(message.ttl_millis(), Some(7000));
    assert_eq!(message.data_content_type(), Some("application/json"));
    let context: &eventmesh::DeliveryContext = message.delivery_context().unwrap();
    assert_eq!(context.protocol_type(), Some("eventmeshmessage"));
    assert_eq!(context.protocol_version(), Some("1.0"));
    assert_eq!(context.protocol_description(), Some("http"));
    assert_eq!(context.attribute("sys"), Some("source-system"));
    assert_eq!(context.attribute("language"), Some("JAVA"));
    assert_eq!(context.attribute("cluster"), Some("source-cluster"));
    assert_eq!(context.attribute("correlation99id"), Some("correlation"));
    let context = context.clone();
    assert!(message.set_prop("protocoldesc", "tcp").is_err());
    message.set_prop("custom", "updated").unwrap();
    assert_eq!(message.delivery_context(), Some(&context));
}
