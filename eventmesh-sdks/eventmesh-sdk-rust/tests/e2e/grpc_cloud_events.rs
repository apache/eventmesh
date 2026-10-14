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

//! E2e: gRPC CloudEvents publishing and stream delivery.

use std::time::Duration;

use cloudevents::{AttributesReader, Data, Event, EventBuilder, EventBuilderV10};
use eventmesh::{
    config::{Endpoint, GrpcConfig},
    grpc::GrpcStreamConsumer,
    message::Message,
    subscription::Subscription,
    DeliveryType, GrpcChannel, GrpcProducer, MessageHandler, Result,
};
use tokio::sync::mpsc;

use crate::harness::{
    ensure_topic, grpc_channel, grpc_consumer_options, grpc_producer, let_stream_settle,
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
async fn grpc_publish_cloud_event() {
    require_runtime!();
    let topic = unique_topic("grpc-ce-pub");
    ensure_topic(&topic).await;
    let (tx, mut receiver) = mpsc::unbounded_channel();
    let consumer = GrpcStreamConsumer::open(
        grpc_channel().await,
        grpc_consumer_options(),
        [Subscription::new(&topic)],
        CloudEventListener(tx),
    )
    .await
    .expect("open gRPC CloudEvent consumer");
    let_stream_settle().await;

    let event = EventBuilderV10::new()
        .id("grpc-ce-e2e-1")
        .source("https://eventmesh.apache.org/rust-sdk")
        .ty("com.example.rust.grpc")
        .subject(&topic)
        .data(
            "application/json",
            r#"{"msg":"hello from gRPC CloudEvents"}"#,
        )
        .build()
        .expect("valid CloudEvent");
    let receipt = grpc_producer()
        .await
        .publish(Message::from(event))
        .await
        .expect("publish gRPC CloudEvent");
    assert_eq!(receipt.code, 0);

    let received = tokio::time::timeout(Duration::from_secs(20), receiver.recv())
        .await
        .expect("timed out waiting for gRPC CloudEvent delivery")
        .expect("CloudEvent handler channel closed");
    assert_eq!(received.subject(), Some(topic.as_str()));
    assert!(serde_json::to_string(&received)
        .expect("serialize received CloudEvent")
        .contains("hello from gRPC CloudEvents"));
    consumer.shutdown();
    consumer.join().await.expect("join gRPC consumer");
}

struct CloudEventReply {
    content_type: &'static str,
    data: Data,
}

impl MessageHandler for CloudEventReply {
    async fn handle(&self, request: Message) -> Result<Option<Message>> {
        let Message::CloudEvent(request) = request else {
            panic!("expected CloudEvent request")
        };
        let reply = EventBuilderV10::new()
            .id("reply-id")
            .source("urn:rust:reply")
            .ty("reply-type")
            .subject(request.subject().unwrap())
            .data(self.content_type, self.data.clone())
            // Current request routing must override metadata from a foreign reply.
            .extension("correlation99id", "foreign-request")
            .extension("reply99to99client", "foreign-client")
            .build()
            .unwrap();
        Ok(Some(reply.into()))
    }
}

async fn cloud_event_request_reply(content_type: &'static str, data: Data) {
    let topic = unique_topic("grpc-ce-reply");
    ensure_topic(&topic).await;
    let consumer = GrpcStreamConsumer::open(
        grpc_channel().await,
        grpc_consumer_options(),
        [Subscription::new(&topic).with_delivery_type(DeliveryType::Sync)],
        CloudEventReply {
            content_type,
            data: data.clone(),
        },
    )
    .await
    .unwrap();
    let_stream_settle().await;
    // No explicit credentials: exercise Java-compatible gRPC defaults on a real Runtime.
    let channel = GrpcChannel::connect(GrpcConfig::new(
        Endpoint::new(crate::runtime::HOST, crate::runtime::GRPC_PORT).unwrap(),
    ))
    .await
    .unwrap();
    let producer = GrpcProducer::new(channel, crate::harness::producer_options()).unwrap();
    let request = EventBuilderV10::new()
        .id("request-id")
        .source("urn:rust:request")
        .ty("request-type")
        .subject(&topic)
        .data("text/plain", "ping")
        .build()
        .unwrap();
    let result = producer.request_reply(request.into()).await;
    consumer.shutdown();
    consumer.join().await.unwrap();
    let Message::CloudEvent(reply) = result.expect("gRPC CloudEvent reply") else {
        panic!("expected CloudEvent reply");
    };
    assert_eq!(reply.id(), "reply-id");
    assert_eq!(reply.datacontenttype(), Some(content_type));
    assert_eq!(reply.data(), Some(&data));
}

#[tokio::test(flavor = "multi_thread")]
async fn grpc_text_cloud_event_reply_with_default_credentials() {
    require_runtime!();
    cloud_event_request_reply("text/plain", Data::String("pong".into())).await;
}

#[tokio::test(flavor = "multi_thread")]
async fn grpc_binary_cloud_event_reply_with_default_credentials() {
    require_runtime!();
    cloud_event_request_reply("application/octet-stream", Data::Binary(vec![0, 128, 255])).await;
}
