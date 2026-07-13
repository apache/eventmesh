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

//! E2e: producer-side operations (publish / batch / one-way).

use eventmesh::{grpc::GrpcProducer, model::EventMeshMessage, transport::Publisher};

use crate::harness::{ensure_topic, producer_config, unique_topic, warm_topic};
use crate::require_runtime;

#[tokio::test(flavor = "multi_thread")]
async fn publish_single() {
    require_runtime!();
    let topic = unique_topic("pub-single");
    ensure_topic(&topic).await;
    let (_consumer, _rx) = warm_topic(&topic).await;

    let producer = GrpcProducer::connect(producer_config()).expect("connect producer");

    let msg = EventMeshMessage::builder()
        .topic(&topic)
        .content("hello from rust e2e")
        .build();
    let resp = producer.publish(msg).await.expect("publish");
    assert!(resp.is_success(), "publish should succeed: {resp}");
}

#[tokio::test(flavor = "multi_thread")]
async fn publish_batch() {
    require_runtime!();
    let topic = unique_topic("pub-batch");
    ensure_topic(&topic).await;
    let (_consumer, _rx) = warm_topic(&topic).await;

    let producer = GrpcProducer::connect(producer_config()).expect("connect producer");

    let batch: Vec<EventMeshMessage> = (0..3)
        .map(|i| {
            EventMeshMessage::builder()
                .topic(&topic)
                .content(format!("batch message #{i}"))
                .build()
        })
        .collect();
    let resp = producer.publish_batch(batch).await.expect("batch publish");
    assert!(resp.is_success(), "batch publish should succeed: {resp}");
}

#[tokio::test(flavor = "multi_thread")]
async fn publish_one_way() {
    require_runtime!();
    let topic = unique_topic("pub-oneway");
    ensure_topic(&topic).await;
    let (_consumer, _rx) = warm_topic(&topic).await;

    let producer = GrpcProducer::connect(producer_config()).expect("connect producer");

    let msg = EventMeshMessage::builder()
        .topic(&topic)
        .content("fire-and-forget")
        .build();
    producer.publish_one_way(msg).await.unwrap_or_else(|e| {
        // The EventMesh runtime's gRPC PublisherService does not override
        // publishOneWay (gRPC UNIMPLEMENTED). Treat that as a skip rather
        // than a failure.
        let msg = format!("{e}");
        if msg.contains("Unimplemented") || msg.contains("unimplemented") {
            eprintln!("[e2e] skipping publish_one_way: server does not implement it. error: {msg}");
        } else {
            panic!("publish_one_way: {e}");
        }
    });
}
