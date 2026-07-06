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

//! TCP subscription: receive delivered messages via a listener.
//!
//! Assumes `docker compose --profile standalone up` is running (TCP on
//! `127.0.0.1:10000`). Run the TCP producer example in another terminal to
//! feed this consumer.

use std::sync::atomic::{AtomicU64, Ordering};

use eventmesh::{
    config::TcpClientConfig,
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    tcp::TcpConsumer,
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
        None
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    let config = TcpClientConfig::builder()
        .server_addr("127.0.0.1")
        .server_port(10000)
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .consumer_group("test-consumerGroup")
        .build();

    let consumer = TcpConsumer::connect(
        config,
        PrintingListener {
            count: AtomicU64::new(0),
        },
    )
    .await?;

    let items = vec![SubscriptionItem::new(
        "test-topic-rust-tcp",
        SubscriptionMode::CLUSTERING,
        SubscriptionType::ASYNC,
    )];
    println!("listening (Ctrl-C to stop)...");
    consumer
        .listen(items)?
        .with_graceful_shutdown(async {
            tokio::signal::ctrl_c().await.ok();
        })
        .await?;

    consumer.shutdown().await;
    Ok(())
}
