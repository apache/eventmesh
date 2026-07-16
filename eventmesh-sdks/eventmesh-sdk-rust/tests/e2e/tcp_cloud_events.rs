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

//! E2e: TCP CloudEvents publishing through the v2 message enum.

use std::time::Duration;

use cloudevents::{EventBuilder, EventBuilderV10};
use eventmesh::message::Message;

use crate::harness::{ensure_topic, tcp_producer, tcp_warm_topic, unique_topic};
use crate::require_runtime;

#[tokio::test(flavor = "multi_thread")]
async fn tcp_publish_cloud_event() {
    require_runtime!();
    let topic = unique_topic("tcp-ce-pub");
    ensure_topic(&topic).await;
    let (consumer, mut receiver) = tcp_warm_topic(&topic).await;
    let producer = tcp_producer().await;

    let event = EventBuilderV10::new()
        .id("tcp-ce-e2e-1")
        .source("https://eventmesh.apache.org/rust-sdk")
        .ty("com.example.someevent")
        .subject(&topic)
        .data(
            "application/cloudevents+json",
            r#"{"msg":"hello from rust tcp cloudevents e2e"}"#,
        )
        .build()
        .expect("valid CloudEvent");
    let receipt = producer
        .publish(Message::from(event))
        .await
        .expect("publish CloudEvent");
    assert_eq!(receipt.code, 0);

    let received = tokio::time::timeout(Duration::from_secs(35), receiver.recv())
        .await
        .expect("timed out waiting for CloudEvent delivery")
        .expect("handler channel closed");
    assert_eq!(received.topic.as_deref(), Some(topic.as_str()));
    assert!(received
        .content
        .as_deref()
        .is_some_and(|content| content.contains("hello from rust tcp cloudevents e2e")));

    producer.shutdown().await;
    consumer.shutdown().await;
}
