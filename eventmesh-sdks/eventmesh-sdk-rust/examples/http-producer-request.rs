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

use anyhow::Result;
use eventmesh::{
    http::{config::EventMeshHttpConfig, producer::EventMeshMessageProducer},
    message::EventMeshMessage,
};
use serde::Serialize;

#[derive(Serialize)]
struct Content {
    content: String,
}

#[tokio::main]
async fn main() -> Result<()> {
    let producer = EventMeshMessageProducer::new(&EventMeshHttpConfig {
        eventmesh_addr: "http://127.0.0.1:10105".to_string(),
        env: "env".to_string(),
        idc: "idc".to_string(),
        ip: "127.0.0.1".to_string(),
        pid: "1234".to_string(),
        sys: "1234".to_string(),
        user_name: "".to_string(),
        password: "".to_string(),
        producergroup: "EventMeshTest-producerGroup".to_string(),
    })?;
    let resp = producer
        .request(EventMeshMessage::new(
            "123456789012345678901234567891",
            "123456789012345678901234567892",
            "TEST-TOPIC-GRPC-RR",
            &serde_json::to_string(&Content {
                content: "testPublishMessage".to_string(),
            })?,
            5,
        ))
        .await?;
    println!("{:#?}", resp);
    Ok(())
}
