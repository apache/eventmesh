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

//! HTTP webhook consumer with a **user-written handler**.
//!
//! Unlike `consumer_server` (which uses the built-in `WebhookServer`), this
//! example hosts the webhook endpoint on the user's own axum app and decodes
//! pushes with the SDK's framework-agnostic codec helpers:
//!
//! - [`eventmesh::http::codec::parse_push_body`] — parse the form-urlencoded
//!   push body sent by the EventMesh runtime.
//! - [`eventmesh::http::codec::PushMessageRequestBody::to_event_mesh_message`]
//!   — decode it into an `EventMeshMessage`.
//! - [`eventmesh::http::codec::WebhookReply`] — the JSON acknowledgment
//!   (`{"retCode": 0}`) the runtime expects.
//!
//! The same approach works with any framework (actix, hyper, rocket, …) or
//! even a non-Rust server: just POST-decode the form body and reply with the
//! JSON. The SDK does not impose a handler type on you — notice this example
//! doesn't even touch `MessageListener`.
//!
//! Assumes `docker compose --profile standalone up` is running (HTTP on
//! `127.0.0.1:10105`). Run the HTTP producer example in another terminal.

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

use axum::extract::State;
use axum::response::IntoResponse;
use axum::{routing::post, Json, Router};
use bytes::Bytes;
use eventmesh::http::codec::{parse_push_body, WebhookReply};
use eventmesh::{
    config::HttpClientConfig,
    http::HttpConsumer,
    model::{SubscriptionItem, SubscriptionMode, SubscriptionType},
};

/// The user's own webhook handler. It uses only the public codec helpers —
/// no SDK handler/state type or `MessageListener` trait involved. The state
/// here is just a plain counter shared across requests.
async fn webhook(State(count): State<Arc<AtomicU64>>, body: Bytes) -> impl IntoResponse {
    // The runtime sends `application/x-www-form-urlencoded`.
    let text = match std::str::from_utf8(&body) {
        Ok(s) => s,
        Err(_) => return Json(WebhookReply::retry("invalid UTF-8")),
    };

    // Decode the push body into an EventMeshMessage and handle it inline.
    match parse_push_body(text).and_then(|p| p.to_event_mesh_message()) {
        Ok(msg) => {
            let n = count.fetch_add(1, Ordering::Relaxed) + 1;
            println!(
                "[received #{n}] topic={:?} content={:?}",
                msg.topic, msg.content
            );
            // `retCode: 0` tells the runtime the delivery succeeded.
            Json(WebhookReply::ok())
        }
        Err(e) => {
            eprintln!("[webhook] decode error: {e}");
            // A non-zero retCode asks the runtime to retry delivery.
            Json(WebhookReply::retry("decode error"))
        }
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    // The handler state is whatever your app needs — here just a message
    // counter. No SDK type is required.
    let state = Arc::new(AtomicU64::new(0));

    // Build the user's own axum app — the route + handler are entirely
    // user-owned. The SDK contributes only the codec helpers above.
    let app = Router::new()
        .route("/my-eventmesh/callback", post(webhook))
        .with_state(state);

    let webhook_url = "http://127.0.0.1:8080/my-eventmesh/callback";

    // Register the webhook URL with the EventMesh runtime.
    let config = HttpClientConfig::builder()
        .servers("127.0.0.1:10105")
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .consumer_group("test-consumerGroup-http-custom")
        .build()?;

    let consumer = HttpConsumer::new(config, None::<std::future::Ready<()>>)?;
    let items = vec![SubscriptionItem::new(
        "test-topic-rust-http",
        SubscriptionMode::CLUSTERING,
        SubscriptionType::ASYNC,
    )];
    consumer.subscribe_webhook(items, webhook_url).await?;
    println!("subscribed; serving webhook at {webhook_url} (Ctrl-C to stop)...");

    let tcp = tokio::net::TcpListener::bind("0.0.0.0:8080")
        .await
        .map_err(eventmesh::EventMeshError::Io)?;
    axum::serve(tcp, app)
        .with_graceful_shutdown(async {
            tokio::signal::ctrl_c().await.ok();
        })
        .await
        .map_err(|e| eventmesh::EventMeshError::Other(format!("server error: {e}")))?;

    consumer.shutdown().await;
    Ok(())
}
