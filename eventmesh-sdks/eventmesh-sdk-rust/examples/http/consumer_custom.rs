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

//! Host an EventMesh webhook in an application-owned axum router.

use axum::{body::Bytes, response::IntoResponse, routing::post, Json, Router};
use eventmesh::{
    config::{ConsumerOptions, Endpoint, EndpointSet, HttpConfig},
    http::codec::{parse_push_body, WebhookReply},
    subscription::Subscription,
    HttpClient,
};

async fn webhook(body: Bytes) -> impl IntoResponse {
    let body = match std::str::from_utf8(&body) {
        Ok(body) => body,
        Err(_) => return Json(WebhookReply::retry("invalid UTF-8")),
    };
    match parse_push_body(body).and_then(|push| push.to_event_mesh_message()) {
        Ok(message) => {
            println!("received: {message:?}");
            Json(WebhookReply::ok())
        }
        Err(error) => {
            eprintln!("invalid webhook delivery: {error}");
            Json(WebhookReply::retry("invalid delivery"))
        }
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let address: std::net::SocketAddr = "0.0.0.0:8081".parse().expect("valid bind address");
    let webhook_url = "http://127.0.0.1:8081/eventmesh/callback";
    let listener = tokio::net::TcpListener::bind(address).await?;
    let app = Router::new().route("/eventmesh/callback", post(webhook));

    let client = HttpClient::new(HttpConfig::new(EndpointSet::new([Endpoint::new(
        "127.0.0.1",
        10_105,
    )?])?))?;
    let consumer = client.webhook_consumer(ConsumerOptions::new("test-consumerGroup"))?;
    consumer
        .subscribe(Subscription::new("test-topic-rust-sdk"), webhook_url)
        .await?;
    axum::serve(listener, app).await?;
    Ok(())
}
