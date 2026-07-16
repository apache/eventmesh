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

//! E2e: TCP producer operations through the v2 facade.

use eventmesh::message::{EventMeshMessage, Message};

use crate::harness::{ensure_topic, tcp_producer, tcp_warm_topic, unique_topic};
use crate::require_runtime;

#[tokio::test(flavor = "multi_thread")]
async fn tcp_publish_single() {
    require_runtime!();
    let topic = unique_topic("tcp-pub-single");
    ensure_topic(&topic).await;
    let (_consumer, _receiver) = tcp_warm_topic(&topic).await;

    let producer = tcp_producer().await;
    let receipt = producer
        .publish(Message::from(EventMeshMessage::new(
            &topic,
            "hello from rust TCP e2e",
        )))
        .await
        .expect("TCP publish");
    assert_eq!(receipt.code, 0, "TCP publish should succeed: {receipt:?}");
    producer.shutdown().await;
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_broadcast() {
    require_runtime!();
    let topic = unique_topic("tcp-broadcast");
    ensure_topic(&topic).await;
    let (_consumer, _receiver) = tcp_warm_topic(&topic).await;

    let producer = tcp_producer().await;
    producer
        .broadcast(Message::from(EventMeshMessage::new(
            &topic,
            "broadcast from rust TCP e2e",
        )))
        .await
        .expect("TCP broadcast");
    producer.shutdown().await;
}
