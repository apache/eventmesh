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

//! E2e: gRPC synchronous request/reply through the v2 facade.

use std::time::{Duration, Instant};

use eventmesh::{
    grpc::GrpcStreamConsumer,
    message::{EventMeshMessage, Message},
    subscription::{DeliveryType, Subscription},
    Error, MessageHandler, Result,
};

use crate::harness::{
    ensure_topic, grpc_channel, grpc_consumer_options, grpc_producer, let_stream_settle,
    unique_topic, ReplyingListener,
};
use crate::require_runtime;

#[tokio::test(flavor = "multi_thread")]
async fn request_reply_roundtrip() {
    require_runtime!();
    let topic = unique_topic("req-reply");
    ensure_topic(&topic).await;
    let consumer = GrpcStreamConsumer::open(
        grpc_channel().await,
        grpc_consumer_options(),
        [Subscription::new(&topic).with_delivery_type(DeliveryType::Sync)],
        ReplyingListener {
            reply_content: "pong".into(),
        },
    )
    .await
    .expect("open request/reply consumer");
    let_stream_settle().await;

    let reply = grpc_producer()
        .await
        .request_reply(Message::from(
            EventMeshMessage::new(&topic, "ping").unwrap(),
        ))
        .await
        .expect("gRPC request/reply");
    match reply {
        Message::EventMesh(message) => assert_eq!(message.content(), "pong"),
        #[cfg(feature = "cloud_events")]
        other => panic!("expected native reply, got {other:?}"),
    }
    consumer.shutdown();
    consumer.join().await.expect("join gRPC consumer");
}

/// A replying listener that sleeps before answering, so callers hit their
/// deadline while the reply is still in flight.
struct SlowReplyingListener {
    delay: Duration,
}

impl MessageHandler for SlowReplyingListener {
    async fn handle(&self, message: Message) -> Result<Option<Message>> {
        let request = message.into_event_mesh()?;
        tokio::time::sleep(self.delay).await;
        Ok(Some(
            EventMeshMessage::new(request.topic(), "late-pong")?.into(),
        ))
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn request_reply_deadline_times_out() {
    require_runtime!();
    let topic = unique_topic("req-reply-timeout");
    ensure_topic(&topic).await;
    // Concurrent handlers so the follow-up request is not queued behind the
    // still-sleeping first one.
    let consumer = GrpcStreamConsumer::open(
        grpc_channel().await,
        grpc_consumer_options().with_max_concurrent_handlers(4),
        [Subscription::new(&topic).with_delivery_type(DeliveryType::Sync)],
        SlowReplyingListener {
            delay: Duration::from_secs(5),
        },
    )
    .await
    .expect("open slow-reply consumer");
    let_stream_settle().await;

    let producer = grpc_producer().await;
    let timeout = Duration::from_millis(1500);
    let started = Instant::now();
    let error = producer
        .request_reply_with_timeout(Message::from(request_message(&topic)), timeout)
        .await
        .expect_err("request/reply must hit the deadline");
    let elapsed = started.elapsed();
    assert!(
        matches!(error, Error::Timeout(actual) if actual == timeout),
        "expected Error::Timeout({timeout:?}), got {error:?}"
    );
    assert!(
        elapsed >= timeout,
        "deadline cannot fire before {timeout:?}, fired at {elapsed:?}"
    );
    assert!(
        elapsed < Duration::from_secs(4),
        "deadline fired suspiciously late: {elapsed:?}"
    );

    // The cancelled HTTP/2 stream must not poison the shared channel: a
    // follow-up request on the same producer still gets its (late) reply.
    let reply = producer
        .request_reply_with_timeout(
            Message::from(request_message(&topic)),
            Duration::from_secs(15),
        )
        .await
        .expect("request/reply after a timed-out call");
    match reply {
        Message::EventMesh(message) => assert_eq!(message.content(), "late-pong"),
        #[cfg(feature = "cloud_events")]
        other => panic!("expected native reply, got {other:?}"),
    }
    consumer.shutdown();
    consumer.join().await.expect("join gRPC consumer");
}

/// The server-side reply wait is bounded by the message TTL (the SDK defaults
/// it to 4s), so the late replies in the deadline test need a longer one.
fn request_message(topic: &str) -> EventMeshMessage {
    EventMeshMessage::new(topic, "ping")
        .expect("build request message")
        .with_property("ttl", "15000")
}
