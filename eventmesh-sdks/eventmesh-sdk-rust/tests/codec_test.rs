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

//! Integration tests for the message <-> CloudEvent codec (no live server).

#![cfg(feature = "grpc")]

use eventmesh::common::ProtocolKey;
use eventmesh::{
    config::GrpcClientConfig,
    grpc::codec,
    model::{
        EventMeshMessage, EventMeshProtocolType, SubscriptionItem, SubscriptionMode,
        SubscriptionType,
    },
    proto_gen::{attr_as_str, attr_str, PbCloudEvent, PbData},
};

fn cfg() -> GrpcClientConfig {
    GrpcClientConfig::builder()
        .server_addr("127.0.0.1")
        .server_port(10205)
        .env("env")
        .idc("idc")
        .producer_group("pg")
        .consumer_group("cg")
        .build()
}

#[test]
fn round_trip_message() {
    let cfg = cfg();
    let msg = EventMeshMessage::builder()
        .topic("t")
        .content("c")
        .biz_seq_no("b")
        .unique_id("u")
        .prop("custom", "val")
        .build();
    let ce = codec::from_event_mesh_message(&msg, &cfg).unwrap();
    assert_eq!(codec::get_subject(&ce), "t");
    assert_eq!(codec::get_text_data(&ce), "c");
    assert_eq!(ce.attributes.get("custom").map(attr_as_str).unwrap(), "val");

    let back = codec::to_event_mesh_message(&ce);
    assert_eq!(back.topic.as_deref(), Some("t"));
    assert_eq!(back.content.as_deref(), Some("c"));
}

#[test]
fn subscription_event_carries_url_and_items() {
    let cfg = cfg();
    let items = vec![SubscriptionItem::new(
        "t",
        SubscriptionMode::CLUSTERING,
        SubscriptionType::ASYNC,
    )];
    let ce = codec::build_subscription_event(
        &cfg,
        EventMeshProtocolType::EventMeshMessage,
        Some("http://localhost:8080/cb"),
        &items,
    )
    .unwrap();
    assert_eq!(
        ce.attributes.get("url").map(attr_as_str).unwrap(),
        "http://localhost:8080/cb"
    );
    assert!(codec::get_text_data(&ce).contains("CLUSTERING"));
}

#[test]
fn response_code_success() {
    let mut ce = PbCloudEvent::default();
    ce.attributes
        .insert(ProtocolKey::GRPC_RESPONSE_CODE.into(), attr_str("0"));
    let resp = codec::to_response(&ce);
    assert!(resp.is_success());
}

#[test]
fn batch_encode() {
    let cfg = cfg();
    let msgs: Vec<EventMeshMessage> = (0..3)
        .map(|i| {
            EventMeshMessage::builder()
                .topic("t")
                .content(format!("c{i}"))
                .build()
        })
        .collect();
    let batch = codec::from_event_mesh_messages(&msgs, &cfg).unwrap();
    assert_eq!(batch.events.len(), 3);
}

#[test]
fn binary_data_fallback() {
    let cfg = cfg();
    let msg = EventMeshMessage::builder()
        .topic("t")
        .content("raw-bytes")
        .prop(ProtocolKey::DATA_CONTENT_TYPE, "application/octet-stream")
        .build();
    let ce = codec::from_event_mesh_message(&msg, &cfg).unwrap();
    assert!(matches!(ce.data, Some(PbData::BinaryData(_))));
}
