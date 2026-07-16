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

//! E2e: HTTP producer operations through the v2 facade.

use eventmesh::message::{EventMeshMessage, Message};

use crate::harness::{ensure_topic, http_producer, http_warm_topic, unique_topic};
use crate::require_runtime;
use std::time::Duration;

async fn receive(
    receiver: &mut tokio::sync::mpsc::UnboundedReceiver<EventMeshMessage>,
) -> EventMeshMessage {
    tokio::time::timeout(Duration::from_secs(15), receiver.recv())
        .await
        .expect("timed out waiting for HTTP delivery")
        .expect("handler channel closed")
}

#[tokio::test(flavor = "multi_thread")]
async fn http_publish_single() {
    require_runtime!();
    let topic = unique_topic("http-pub-single");
    ensure_topic(&topic).await;
    let (_handle, mut receiver) = http_warm_topic(&topic).await;

    let receipt = http_producer()
        .publish(Message::from(EventMeshMessage::new(
            &topic,
            "hello from rust http e2e",
        )))
        .await
        .expect("HTTP publish");
    assert_eq!(receipt.code, 0, "HTTP publish should succeed: {receipt:?}");
    assert_eq!(
        receive(&mut receiver).await.content.as_deref(),
        Some("hello from rust http e2e")
    );
}
