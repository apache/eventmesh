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

//! Publish native CloudEvents (`cloudevents::Event`) via the gRPC transport.
//!
//! Requires features `grpc` + `cloud_events`. Assumes a standalone EventMesh
//! server is reachable on `127.0.0.1:10205`.

use cloudevents::{EventBuilder, EventBuilderV10};
use eventmesh::{config::GrpcClientConfig, grpc::GrpcProducer};

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

    let event = EventBuilderV10::new()
        .id("1")
        .source("https://eventmesh.apache.org/rust-sdk/demo")
        .ty("com.example.ping")
        .subject("test-topic-rust-sdk")
        .data(
            "application/json",
            serde_json::json!({"msg": "cloudevents hello"}),
        )
        .build()
        .map_err(|e| eventmesh::EventMeshError::Other(format!("cloudevents build: {e}")))?;

    let resp = producer.publish_cloud_event(event).await?;
    println!("[cloudevents publish] {resp}");
    Ok(())
}
