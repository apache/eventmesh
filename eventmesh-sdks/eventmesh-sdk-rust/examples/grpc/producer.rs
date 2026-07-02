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

//! Publish + batch publish + request-reply against a running EventMesh server.
//!
//! Assumes `docker compose --profile standalone up` is running (gRPC on
//! `127.0.0.1:10205`).

use std::time::Duration;

use eventmesh::{
    config::GrpcClientConfig, grpc::GrpcProducer, model::EventMeshMessage, transport::Publisher,
};

#[eventmesh::main]
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
        .producer_group("test-producerGroup")
        .build();

    let producer = GrpcProducer::connect(config)?;

    let topic = "test-topic-rust-sdk";

    // 1) single publish
    let msg = EventMeshMessage::builder()
        .topic(topic)
        .content("hello from rust sdk")
        .build();
    let resp = producer.publish(msg).await?;
    println!("[publish]     {resp}");

    // 2) batch publish
    let batch: Vec<EventMeshMessage> = (0..3)
        .map(|i| {
            EventMeshMessage::builder()
                .topic(topic)
                .content(format!("batch message #{i}"))
                .build()
        })
        .collect();
    let resp = producer.publish_batch(batch).await?;
    println!("[batch]       {resp}");

    // 3) request-reply (needs a SYNC consumer subscribed to the topic; will
    //    time out otherwise)
    let rr = EventMeshMessage::builder()
        .topic(format!("{topic}-rr"))
        .content("ping")
        .ttl_millis(4000)
        .build();
    match producer.request_reply(rr, Duration::from_secs(6)).await {
        Ok(reply) => println!("[request-reply] got reply: {reply}"),
        Err(e) => println!("[request-reply] no reply (is a SYNC consumer running?): {e}"),
    }

    Ok(())
}
