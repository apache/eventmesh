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

use eventmesh::{
    message::{EventMeshMessage, Message},
    subscription::{DeliveryType, Subscription},
};

use crate::harness::{
    ensure_topic, grpc_client, grpc_consumer_options, grpc_producer, let_stream_settle,
    unique_topic, ReplyingListener,
};
use crate::require_runtime;

#[tokio::test(flavor = "multi_thread")]
async fn request_reply_roundtrip() {
    require_runtime!();
    let topic = unique_topic("req-reply");
    ensure_topic(&topic).await;
    let consumer = grpc_client()
        .stream_consumer(
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
