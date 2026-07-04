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

//! HTTP publish + request-reply against a running EventMesh server.
//!
//! Assumes `docker compose --profile standalone up` is running (HTTP on
//! `127.0.0.1:10105`).
//!
//! Batch publish is not yet supported over HTTP — see `publish_batch` docs.

use std::time::Duration;

use eventmesh::{
    config::HttpClientConfig, http::HttpProducer, model::EventMeshMessage, transport::Publisher,
};

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    let config = HttpClientConfig::builder()
        .servers("127.0.0.1:10105")
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .producer_group("test-producerGroup")
        .build()?;

    let producer = HttpProducer::new(config)?;

    let topic = "test-topic-rust-http";

    // 1) single publish
    let msg = EventMeshMessage::builder()
        .topic(topic)
        .content("hello from rust http sdk")
        .build();
    let resp = producer.publish(msg).await?;
    println!("[publish]     {resp}");

    // 2) multiple single publishes (HTTP batch is not yet supported)
    for i in 0..3 {
        let msg = EventMeshMessage::builder()
            .topic(topic)
            .content(format!("message #{i}"))
            .build();
        let resp = producer.publish(msg).await?;
        println!("[publish-{i}]   {resp}");
    }

    // 3) request-reply (needs a SYNC consumer subscribed to the topic)
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
