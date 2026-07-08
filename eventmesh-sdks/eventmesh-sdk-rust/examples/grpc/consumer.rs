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

//! Stream subscription: receive delivered messages via a listener.
//!
//! Assumes `docker compose --profile standalone up` is running (gRPC on
//! `127.0.0.1:10205`). Run the producer example in another terminal to feed
//! this consumer.

use std::sync::atomic::{AtomicU64, Ordering};

use eventmesh::{
    config::GrpcClientConfig,
    grpc::GrpcConsumer,
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    MessageListener,
};

struct PrintingListener {
    count: AtomicU64,
}

impl MessageListener for PrintingListener {
    type Message = EventMeshMessage;

    async fn handle(&self, message: Self::Message) -> Option<Self::Message> {
        let n = self.count.fetch_add(1, Ordering::Relaxed) + 1;
        println!(
            "[received #{n}] topic={:?} content={:?}",
            message.topic, message.content
        );
        None // async ack, no reply
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    let config = GrpcClientConfig::builder()
        .server_addr("127.0.0.1")
        .server_port(10205)
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .consumer_group("test-consumerGroup")
        .build();

    let listener = PrintingListener {
        count: AtomicU64::new(0),
    };
    let consumer = GrpcConsumer::new(config, listener)?;

    let items = vec![SubscriptionItem::new(
        "test-topic-rust-sdk",
        SubscriptionMode::CLUSTERING,
        SubscriptionType::ASYNC,
    )];
    println!("subscribed; waiting for messages (Ctrl-C to stop)...");
    consumer
        .subscribe_stream(items)?
        .with_graceful_shutdown(async {
            tokio::signal::ctrl_c().await.ok();
        })
        .await?;
    Ok(())
}
