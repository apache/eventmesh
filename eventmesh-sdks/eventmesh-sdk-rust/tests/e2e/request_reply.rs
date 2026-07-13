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

//! E2e: synchronous request/reply round-trip.

use std::time::Duration;

use eventmesh::{
    grpc::{GrpcProducer, GrpcStreamConsumer},
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    transport::Publisher,
};

use crate::harness::{
    consumer_config, ensure_topic, let_stream_settle, producer_config, unique_topic,
    ReplyingListener,
};
use crate::require_runtime;

const REPLY: &str = "pong";

#[tokio::test(flavor = "multi_thread")]
async fn request_reply_roundtrip() {
    require_runtime!();
    let topic = unique_topic("req-reply");
    ensure_topic(&topic).await;

    // SYNC consumer: receives the request and echoes a fixed reply.
    let listener = ReplyingListener {
        reply_content: REPLY.to_string(),
    };
    let consumer = GrpcStreamConsumer::subscribe_stream(
        consumer_config(),
        listener,
        vec![SubscriptionItem::new(
            &topic,
            SubscriptionMode::CLUSTERING,
            SubscriptionType::SYNC,
        )],
        None::<std::future::Ready<()>>,
    )
    .await
    .expect("subscribe_stream");
    let_stream_settle().await;

    let producer = GrpcProducer::connect(producer_config()).expect("connect producer");
    let request = EventMeshMessage::builder()
        .topic(&topic)
        .content("ping")
        .ttl_millis(10_000)
        .build();

    let reply = producer
        .request_reply(request, Duration::from_secs(15))
        .await
        .expect("request_reply RPC should complete");

    // The standalone (in-memory) broker does not implement synchronous
    // request/reply: it bounces the request back with a "Request is not
    // supported" message rather than forwarding it to the consumer. Treat that
    // specific broker-capability gap as a skip, not a failure, so the suite
    // stays green on standalone while still asserting the full round-trip on a
    // durable backend (RocketMQ). Any other non-zero status (e.g. a request
    // timeout, 10006) is a real failure and must surface here.
    let unsupported = reply
        .get_prop("responsemessage")
        .map(|m| m.to_lowercase().contains("not supported"))
        .unwrap_or(false);
    if unsupported {
        eprintln!(
            "[e2e] skipping request_reply assertion: broker does not support \
             sync request/reply (standalone). reply status={:?} msg={:?}",
            reply.get_prop("statuscode"),
            reply.get_prop("responsemessage")
        );
        return;
    }

    assert_eq!(
        reply.content.as_deref(),
        Some(REPLY),
        "reply content mismatch: {reply}"
    );

    drop(consumer);
}
