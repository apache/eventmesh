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

use std::{net::SocketAddr, time::Duration};

use cloudevents::{AttributesReader, Event, EventBuilder, EventBuilderV10};
use eventmesh::{
    message::Message, subscription::Subscription, webhook::WebhookOptions, MessageHandler, Result,
};
use tokio::sync::mpsc;

use crate::harness::{
    consumer_options, ensure_topic, free_port, http_client, http_producer, let_stream_settle,
    unique_topic,
};
use crate::require_runtime;
use crate::runtime::webhook_host;

struct CloudEventListener(mpsc::UnboundedSender<Event>);

impl MessageHandler for CloudEventListener {
    async fn handle(&self, message: Message) -> Result<Option<Message>> {
        let event = match message {
            Message::CloudEvent(event) => event,
            Message::EventMesh(message) => {
                panic!("expected HTTP CloudEvent to preserve its dialect, got {message:?}")
            }
        };
        let _ = self.0.send(event);
        Ok(None)
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn http_publish_cloud_event() {
    require_runtime!();
    let topic = unique_topic("http-ce-pub");
    ensure_topic(&topic).await;
    let (tx, mut receiver) = mpsc::unbounded_channel();
    let port = free_port();
    let bind_address: SocketAddr = format!("0.0.0.0:{port}").parse().expect("webhook address");
    let advertise_url = format!("http://{}:{port}/eventmesh/callback", webhook_host());
    let consumer = http_client()
        .consumer(
            consumer_options(),
            WebhookOptions::new(bind_address).with_advertise_url(advertise_url),
            [Subscription::new(&topic)],
            CloudEventListener(tx),
        )
        .await
        .expect("open HTTP CloudEvent consumer");
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
    consumer.close().await.expect("close HTTP consumer");
}
