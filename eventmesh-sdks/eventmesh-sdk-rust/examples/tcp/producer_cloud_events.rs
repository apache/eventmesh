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

//! TCP CloudEvents producer — publish a native CloudEvent over TCP.
//!
//! Requires `docker compose --profile standalone up` running (TCP on
//! `127.0.0.1:10000`). The existing `tcp_consumer` example receives
//! CloudEvents transparently (they are converted to `EventMeshMessage` at the
//! boundary).

use std::time::Duration;

use cloudevents::{EventBuilder, EventBuilderV10};
use eventmesh::{config::TcpClientConfig, tcp::TcpProducer};

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
        .producer_group("test-producerGroup")
        .build();

    let producer = TcpProducer::connect(config).await?;

    let topic = "test-topic-rust-tcp-cloudevents";

    // 1) publish a CloudEvent
    let event = EventBuilderV10::new()
        .id("rust-tcp-ce-1")
        .source("https://eventmesh.apache.org/rust-sdk")
        .ty("com.example.someevent")
        .subject(topic)
        .data(
            "application/json",
            serde_json::json!({"msg": "hello from rust tcp cloudevents"}),
        )
        .build()
        .expect("valid CloudEvent");

    match producer.publish_cloud_event(event).await {
        Ok(resp) => println!("[publish]     {resp}"),
        Err(e) => println!("[publish]     error: {e}"),
    }

    // 2) broadcast a CloudEvent (fire-and-forget)
    let event = EventBuilderV10::new()
        .id("rust-tcp-ce-broadcast")
        .source("https://eventmesh.apache.org/rust-sdk")
        .ty("com.example.someevent")
        .subject(topic)
        .data("text/plain", "broadcast from rust tcp cloudevents")
        .build()
        .expect("valid CloudEvent");

    producer.broadcast_cloud_event(event).await?;
    println!("[broadcast]   sent");

    // 3) request-reply with a CloudEvent (needs a SYNC consumer)
    let event = EventBuilderV10::new()
        .id("rust-tcp-ce-rr")
        .source("https://eventmesh.apache.org/rust-sdk")
        .ty("com.example.someevent")
        .subject(format!("{topic}-rr"))
        .data("text/plain", "ping")
        .build()
        .expect("valid CloudEvent");

    match producer
        .request_reply_cloud_event(event, Duration::from_secs(6))
        .await
    {
        Ok(reply) => println!("[request-reply] got reply: {reply}"),
        Err(e) => println!("[request-reply] no reply (is a SYNC consumer running?): {e}"),
    }

    producer.shutdown().await;
    Ok(())
}
