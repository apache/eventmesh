//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  The ASF
// licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//

//! E2e: TCP synchronous request/reply round-trip.

use std::time::Duration;

use eventmesh::{
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    tcp::{TcpConsumer, TcpProducer},
    transport::{Publisher, Subscriber},
};

use crate::harness::{
    ensure_topic, let_stream_settle, tcp_consumer_config, tcp_producer_config, unique_topic,
    ReplyingListener,
};
use crate::runtime::{ensure_runtime, mode, Mode};

const REPLY: &str = "pong";

#[tokio::test]
async fn tcp_request_reply_roundtrip() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("tcp-req-reply");
    ensure_topic(&topic).await;

    // SYNC consumer: receives the request and echoes a fixed reply.
    let listener = ReplyingListener {
        reply_content: REPLY.to_string(),
    };
    let consumer = TcpConsumer::connect(tcp_consumer_config(), listener)
        .await
        .expect("connect consumer");
    consumer
        .subscribe(vec![SubscriptionItem::new(
            &topic,
            SubscriptionMode::CLUSTERING,
            SubscriptionType::SYNC,
        )])
        .await
        .expect("subscribe");
    let_stream_settle().await;

    let producer = TcpProducer::connect(tcp_producer_config())
        .await
        .expect("connect producer");
    let request = EventMeshMessage::builder()
        .topic(&topic)
        .content("ping")
        .ttl_millis(10_000)
        .build();

    let reply = producer
        .request_reply(request, Duration::from_secs(15))
        .await;

    producer.shutdown().await;
    consumer.shutdown().await;

    // The harness itself always starts the `rocketmq` profile, where sync
    // request/reply is expected to work. Only an externally-provided server
    // (set via `EVENTMESH_E2E_EXTERNAL=1` or pre-started by the user) can be
    // the standalone (in-memory) broker, which does not implement RR. So fail
    // on any error when we launched the stack, and only skip for the
    // standalone case on an external server — a timeout, codec regression,
    // bad ACK, or connection failure must surface instead of being silently
    // swallowed as a skip.
    match reply {
        Ok(reply) => {
            assert_eq!(
                reply.content.as_deref(),
                Some(REPLY),
                "reply content mismatch: {reply}"
            );
        }
        Err(e) => match mode() {
            Some(Mode::Started) => {
                panic!(
                    "tcp request/reply failed on the harness-launched (rocketmq) \
                     broker, where it is expected to work: {e}"
                );
            }
            _ => {
                eprintln!(
                    "[e2e] skipping tcp_request_reply assertion: externally-provided \
                     server may not support sync request/reply (standalone). error: {e}"
                );
            }
        },
    }
}
