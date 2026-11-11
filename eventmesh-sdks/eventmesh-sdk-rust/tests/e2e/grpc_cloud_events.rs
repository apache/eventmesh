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

use cloudevents::{AttributesReader, AttributesWriter, Data, Event, EventBuilder, EventBuilderV10};
use eventmesh::{
    config::{Endpoint, GrpcConfig},
    grpc::GrpcStreamConsumer,
    message::Message,
    subscription::Subscription,
    webhook::WebhookOptions,
    DeliveryType, GrpcChannel, GrpcProducer, MessageHandler, Result,
};
use tokio::sync::mpsc;

use crate::harness::{
    consumer_options, ensure_topic, free_port, grpc_channel, grpc_consumer_options, grpc_producer,
    http_client, http_producer, let_stream_settle, unique_topic,
};
use crate::require_runtime;
use crate::runtime::webhook_host;

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

#[tokio::test(flavor = "multi_thread")]
async fn grpc_cloud_event_payload_variants_match_runtime_validation() {
    use prost::Message as _;

    require_runtime!();
    let topic = unique_topic("grpc-ce-payload");
    ensure_topic(&topic).await;
    let producer = grpc_producer().await;
    let any = prost_types::Any {
        type_url: "type.googleapis.com/example.Payload".into(),
        value: vec![0, 128, 255],
    };
    for (id, content_type, data) in [
        (
            "json-bytes",
            "application/json",
            Data::Binary(br#"{"value":1}"#.to_vec()),
        ),
        ("text-bytes", "text/plain", Data::Binary(b"hello".to_vec())),
        (
            "binary-string",
            "application/octet-stream",
            Data::String("hello".into()),
        ),
        (
            "parameterized-json",
            "application/json; charset=utf-8",
            Data::Json(serde_json::json!({"value": 1})),
        ),
        (
            "protobuf-bytes",
            "application/protobuf",
            Data::Binary(any.encode_to_vec()),
        ),
    ] {
        let event = EventBuilderV10::new()
            .id(id)
            .source("urn:rust:grpc-payload")
            .ty("test.payload")
            .subject(&topic)
            .data(content_type, data)
            .build()
            .unwrap();
        let receipt = producer
            .publish(event.into())
            .await
            .unwrap_or_else(|error| panic!("Runtime rejected {id}: {error}"));
        assert_eq!(receipt.code, 0);
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn grpc_received_cloud_event_can_be_rerouted_over_http() {
    require_runtime!();
    let source_topic = unique_topic("grpc-ce-forward-source");
    let destination_topic = unique_topic("grpc-ce-forward-destination");
    ensure_topic(&source_topic).await;
    ensure_topic(&destination_topic).await;
    let (tx, mut receiver) = mpsc::unbounded_channel();
    let consumer = GrpcStreamConsumer::open(
        grpc_channel().await,
        grpc_consumer_options(),
        [Subscription::new(&source_topic)],
        CloudEventListener(tx),
    )
    .await
    .expect("open gRPC forwarding consumer");
    let (destination_tx, mut destination_receiver) = mpsc::unbounded_channel();
    let port = free_port();
    let bind_address = format!("0.0.0.0:{port}").parse().expect("webhook address");
    let advertise_url = format!("http://{}:{port}/eventmesh/callback", webhook_host());
    let destination_consumer = http_client()
        .consumer(
            consumer_options(),
            WebhookOptions::new(bind_address).with_advertise_url(advertise_url),
            [Subscription::new(&destination_topic)],
            CloudEventListener(destination_tx),
        )
        .await
        .expect("open HTTP forwarding destination consumer");
    let_stream_settle().await;

    let event = EventBuilderV10::new()
        .id("before-forwarding")
        .source("urn:rust:grpc-forward")
        .ty("test.forward")
        .subject(&source_topic)
        .data("application/json", serde_json::json!({"forwarded": true}))
        .build()
        .unwrap();
    grpc_producer()
        .await
        .publish(event.into())
        .await
        .expect("publish original CloudEvent");
    let mut received = tokio::time::timeout(Duration::from_secs(20), receiver.recv())
        .await
        .expect("original CloudEvent delivery timed out")
        .expect("CloudEvent handler channel closed");
    assert_eq!(received.subject(), Some(source_topic.as_str()));
    received.set_subject(Some(destination_topic.clone()));
    received.set_id("after-forwarding");
    http_producer()
        .publish(received.into())
        .await
        .expect("forward received CloudEvent through HTTP");
    let forwarded = tokio::time::timeout(Duration::from_secs(20), destination_receiver.recv())
        .await
        .expect("forwarded CloudEvent delivery timed out")
        .expect("CloudEvent handler channel closed");

    consumer.shutdown();
    consumer
        .join()
        .await
        .expect("join gRPC forwarding consumer");
    destination_consumer
        .close()
        .await
        .expect("close HTTP forwarding destination consumer");
    assert_eq!(forwarded.id(), "after-forwarding");
    assert_eq!(forwarded.subject(), Some(destination_topic.as_str()));
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
        Endpoint::new(crate::runtime::host(), crate::runtime::GRPC_PORT).unwrap(),
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
