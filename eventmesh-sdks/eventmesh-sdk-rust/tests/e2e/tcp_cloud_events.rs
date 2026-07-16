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

use cloudevents::{AttributesReader, Event, EventBuilder, EventBuilderV10};
use eventmesh::{message::Message, MessageHandler, Result};
use tokio::sync::mpsc;

use crate::harness::{consumer_options, ensure_topic, tcp_client, tcp_producer, unique_topic};
use crate::require_runtime;

struct CloudEventListener {
    tx: mpsc::UnboundedSender<Event>,
}

impl MessageHandler for CloudEventListener {
    async fn handle(&self, message: Message) -> Result<Option<Message>> {
        if let Message::CloudEvent(event) = message {
            let _ = self.tx.send(event);
        }
        Ok(None)
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_publish_cloud_event() {
    require_runtime!();
    let topic = unique_topic("tcp-ce-pub");
    ensure_topic(&topic).await;
    let (tx, mut receiver) = mpsc::unbounded_channel();
    let consumer = tcp_client()
        .consumer(consumer_options(), CloudEventListener { tx })
        .await
        .expect("open TCP CloudEvent consumer");
    consumer
        .subscribe(eventmesh::subscription::Subscription::new(&topic))
        .await
        .expect("subscribe TCP CloudEvent consumer");
    tokio::time::sleep(Duration::from_millis(800)).await;
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
    assert_eq!(received.subject(), Some(topic.as_str()));
    assert!(serde_json::to_string(&received)
        .expect("serialize received CloudEvent")
        .contains("hello from rust tcp cloudevents e2e"));

    producer.shutdown().await;
    consumer.shutdown().await;
}
