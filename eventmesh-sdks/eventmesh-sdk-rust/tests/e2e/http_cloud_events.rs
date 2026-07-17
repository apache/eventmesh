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

//! E2e: HTTP CloudEvents publishing, consumed by a real gRPC stream.

use std::time::Duration;

use cloudevents::{AttributesReader, Event, EventBuilder, EventBuilderV10};
use eventmesh::{message::Message, subscription::Subscription, MessageHandler, Result};
use tokio::sync::mpsc;

use crate::harness::{
    ensure_topic, grpc_client, grpc_consumer_options, http_producer, let_stream_settle,
    unique_topic,
};
use crate::require_runtime;

struct CloudEventListener(mpsc::UnboundedSender<Event>);

impl MessageHandler for CloudEventListener {
    async fn handle(&self, message: Message) -> Result<Option<Message>> {
        if let Message::CloudEvent(event) = message {
            let _ = self.0.send(event);
        }
        Ok(None)
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn http_publish_cloud_event() {
    require_runtime!();
    let topic = unique_topic("http-ce-pub");
    ensure_topic(&topic).await;
    let (tx, mut receiver) = mpsc::unbounded_channel();
    let consumer = grpc_client()
        .stream_consumer(
            grpc_consumer_options(),
            [Subscription::new(&topic)],
            CloudEventListener(tx),
        )
        .await
        .expect("open gRPC CloudEvent consumer");
    let_stream_settle().await;

    let event = EventBuilderV10::new()
        .id("http-ce-e2e-1")
        .source("https://eventmesh.apache.org/rust-sdk")
        .ty("com.example.rust.http")
        .subject(&topic)
        .data(
            "application/json",
            r#"{"msg":"hello from HTTP CloudEvents"}"#,
        )
        .build()
        .expect("valid CloudEvent");
    let receipt = http_producer()
        .publish(Message::from(event))
        .await
        .expect("publish HTTP CloudEvent");
    assert_eq!(receipt.code, 0);

    let received = tokio::time::timeout(Duration::from_secs(20), receiver.recv())
        .await
        .expect("timed out waiting for HTTP CloudEvent delivery")
        .expect("CloudEvent handler channel closed");
    assert_eq!(received.subject(), Some(topic.as_str()));
    assert!(serde_json::to_string(&received)
        .expect("serialize received CloudEvent")
        .contains("hello from HTTP CloudEvents"));
    consumer.shutdown().await;
}
