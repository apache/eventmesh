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

//! E2e: gRPC producer operations through the v2 facade.

use eventmesh::message::{EventMeshMessage, Message};

use crate::harness::{ensure_topic, grpc_producer, unique_topic, warm_topic};
use crate::require_runtime;
use std::time::Duration;

async fn receive(
    receiver: &mut tokio::sync::mpsc::UnboundedReceiver<EventMeshMessage>,
) -> EventMeshMessage {
    tokio::time::timeout(Duration::from_secs(10), receiver.recv())
        .await
        .expect("timed out waiting for gRPC delivery")
        .expect("handler channel closed")
}

#[tokio::test(flavor = "multi_thread")]
async fn publish_single() {
    require_runtime!();
    let topic = unique_topic("pub-single");
    ensure_topic(&topic).await;
    let (_consumer, mut receiver) = warm_topic(&topic).await;

    let receipt = grpc_producer()
        .await
        .publish(Message::from(
            EventMeshMessage::new(&topic, "hello from rust e2e").unwrap(),
        ))
        .await
        .expect("publish");
    assert_eq!(receipt.code, 0, "publish should succeed: {receipt:?}");
    assert_eq!(
        receive(&mut receiver).await.content(),
        "hello from rust e2e"
    );
}

#[tokio::test(flavor = "multi_thread")]
async fn publish_batch() {
    require_runtime!();
    let topic = unique_topic("pub-batch");
    ensure_topic(&topic).await;
    let (_consumer, mut receiver) = warm_topic(&topic).await;

    let messages = (0..3)
        .map(|index| {
            Message::from(EventMeshMessage::new(&topic, format!("batch message #{index}")).unwrap())
        })
        .collect();
    let receipt = grpc_producer()
        .await
        .publish_batch(messages)
        .await
        .expect("batch publish");
    assert_eq!(receipt.code, 0, "batch publish should succeed: {receipt:?}");
    let mut contents = Vec::new();
    for _ in 0..3 {
        contents.push(receive(&mut receiver).await.content().to_owned());
    }
    contents.sort();
    assert_eq!(
        contents,
        ["batch message #0", "batch message #1", "batch message #2"]
    );
}
