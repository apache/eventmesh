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

//! E2e: HTTP producer-side operations.
//!
//! HTTP batch publish is intentionally not supported by the transport (see
//! `HttpProducer::publish_batch`), so only single-message publish is tested
//! here.

use eventmesh::{http::HttpProducer, model::EventMeshMessage, transport::Publisher};

use crate::harness::{ensure_topic, http_producer_config, http_warm_topic, unique_topic};
use crate::require_runtime;

#[tokio::test(flavor = "multi_thread")]
async fn http_publish_single() {
    require_runtime!();
    let topic = unique_topic("http-pub-single");
    ensure_topic(&topic).await;
    let (_handle, _rx) = http_warm_topic(&topic).await;

    let producer = HttpProducer::new(http_producer_config()).expect("build http producer");

    let msg = EventMeshMessage::builder()
        .topic(&topic)
        .content("hello from rust http e2e")
        .build();
    let resp = producer.publish(msg).await.expect("http publish");
    assert!(resp.is_success(), "http publish should succeed: {resp}");
}

/// Verify that batch publish surfaces a clear `Unsupported` error rather than
/// silently succeeding or panicking. This documents the known limitation.
#[tokio::test(flavor = "multi_thread")]
async fn http_publish_batch_unsupported() {
    require_runtime!();
    let producer = HttpProducer::new(http_producer_config()).expect("build http producer");

    let batch: Vec<EventMeshMessage> = (0..2)
        .map(|i| {
            EventMeshMessage::builder()
                .topic("n/a")
                .content(format!("batch-{i}"))
                .build()
        })
        .collect();

    let err = producer
        .publish_batch(batch)
        .await
        .expect_err("batch publish should be unsupported over HTTP");
    assert!(
        err.to_string().contains("not supported"),
        "expected an unsupported error, got: {err}"
    );
}
