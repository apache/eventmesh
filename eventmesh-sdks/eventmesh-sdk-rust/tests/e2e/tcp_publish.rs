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

//! E2e: TCP producer-side operations (publish / broadcast).

use eventmesh::{model::EventMeshMessage, tcp::TcpProducer, transport::Publisher};

use crate::harness::{ensure_topic, tcp_producer_config, tcp_warm_topic, unique_topic};
use crate::runtime::ensure_runtime;

#[tokio::test]
async fn tcp_publish_single() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("tcp-pub-single");
    ensure_topic(&topic).await;
    let (_consumer, _rx) = tcp_warm_topic(&topic).await;

    let producer = TcpProducer::connect(tcp_producer_config())
        .await
        .expect("connect producer");

    let msg = EventMeshMessage::builder()
        .topic(&topic)
        .content("hello from rust tcp e2e")
        .build();
    let resp = producer.publish(msg).await.expect("publish");
    assert!(resp.is_success(), "publish should succeed: {resp}");

    producer.shutdown().await;
}

#[tokio::test]
async fn tcp_broadcast() {
    if !ensure_runtime() {
        return;
    }
    let topic = unique_topic("tcp-pub-broadcast");
    ensure_topic(&topic).await;
    let (_consumer, _rx) = tcp_warm_topic(&topic).await;

    let producer = TcpProducer::connect(tcp_producer_config())
        .await
        .expect("connect producer");

    let msg = EventMeshMessage::builder()
        .topic(&topic)
        .content("broadcast from rust tcp e2e")
        .build();
    producer.broadcast(msg).await.expect("broadcast");

    producer.shutdown().await;
}
