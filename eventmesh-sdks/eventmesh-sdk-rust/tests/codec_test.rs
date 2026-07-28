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
        parse_push_body("content=hello&topic=orders").expect("decode webhook body");
    assert_eq!(parsed.topic.as_deref(), Some("orders"));
    assert_eq!(WebhookReply::ok().ret_code, 1);
}
