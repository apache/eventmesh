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

//! E2e: gRPC webhook registration and HTTP callback delivery.

use std::time::{Duration, Instant};

use eventmesh::{
    message::{EventMeshMessage, Message},
    subscription::Subscription,
};

use crate::harness::{
    consumer_options, ensure_topic, grpc_client, http_producer, let_stream_settle,
    start_webhook_server, unique_topic,
};
use crate::require_runtime;

#[tokio::test(flavor = "multi_thread")]
async fn grpc_webhook_consumer_receives_delivery() {
    require_runtime!();
    let topic = unique_topic("grpc-webhook");
    ensure_topic(&topic).await;

    // The server is deliberately not registered through the HTTP client: this
    // leaves gRPC as the only component that owns the runtime subscription.
    let (webhook_server, mut receiver) = start_webhook_server().await;
    let webhook = grpc_client()
        .webhook_consumer(consumer_options())
        .await
        .expect("build gRPC webhook consumer");
    let deadline = Instant::now() + Duration::from_secs(20);
    loop {
        match webhook
            .subscribe([Subscription::new(&topic)], webhook_server.webhook_url())
            .await
        {
            Ok(()) => break,
            Err(error) if Instant::now() < deadline => {
                tracing::debug!(%error, "gRPC webhook registration is waiting for runtime routes");
                tokio::time::sleep(Duration::from_millis(500)).await;
            }
            Err(error) => panic!("register gRPC webhook: {error}"),
        }
    }
    let_stream_settle().await;

    http_producer()
        .publish(Message::from(EventMeshMessage::new(
            &topic,
            "delivered-via-grpc-webhook",
        )))
        .await
        .expect("publish to gRPC webhook");
    let delivered = tokio::time::timeout(Duration::from_secs(15), receiver.recv())
        .await
        .expect("timed out waiting for gRPC webhook callback")
        .expect("webhook handler channel closed");
    assert_eq!(
        delivered.content.as_deref(),
        Some("delivered-via-grpc-webhook")
    );
    webhook.shutdown().await;
}
