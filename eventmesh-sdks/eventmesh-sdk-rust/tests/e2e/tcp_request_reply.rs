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

//! E2e: TCP synchronous request/reply through the v2 facade.

use eventmesh::{
    message::{EventMeshMessage, Message},
    subscription::{DeliveryType, Subscription},
};

use crate::harness::{
    consumer_options, ensure_topic, let_tcp_subscription_settle, tcp_client, tcp_producer,
    unique_topic, ReplyingListener,
};
use crate::require_runtime;
use crate::runtime::{mode, Mode};

#[tokio::test(flavor = "multi_thread")]
async fn tcp_request_reply_roundtrip() {
    require_runtime!();
    let topic = unique_topic("tcp-req-reply");
    ensure_topic(&topic).await;
    let consumer = tcp_client()
        .consumer(
            consumer_options(),
            ReplyingListener {
                reply_content: "pong".into(),
            },
        )
        .await
        .expect("open TCP request/reply consumer");
    consumer
        .subscribe(Subscription::new(&topic).with_delivery_type(DeliveryType::Sync))
        .await
        .expect("subscribe TCP request/reply consumer");
    let_tcp_subscription_settle().await;

    let producer = tcp_producer().await;
    let reply = producer
        .request_reply(Message::from(EventMeshMessage::new(&topic, "ping")))
        .await;
    producer.shutdown().await;
    consumer.shutdown().await;

    match reply {
        Ok(Message::EventMesh(message)) => assert_eq!(message.content.as_deref(), Some("pong")),
        Ok(other) => panic!("expected native reply, got {other:?}"),
        Err(error) if mode() != Some(Mode::Started) => {
            eprintln!("[e2e] external broker may not support TCP request/reply: {error}");
        }
        Err(error) => panic!("TCP request/reply failed on harness runtime: {error}"),
    }
}
