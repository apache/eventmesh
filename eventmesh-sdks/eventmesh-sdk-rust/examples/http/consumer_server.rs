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

//! HTTP webhook consumer using the built-in [`WebhookServer`].
//!
//! The SDK starts an axum server to receive pushed messages from the
//! EventMesh runtime. This is the "batteries-included" mode.
//!
//! Assumes `docker compose --profile standalone up` is running (HTTP on
//! `127.0.0.1:10105`). Run the HTTP producer example in another terminal.

use std::net::SocketAddr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

use eventmesh::{
    config::HttpClientConfig,
    http::{HttpConsumer, WebhookServer},
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    MessageListener,
};

struct PrintingListener {
    count: AtomicU64,
}

impl MessageListener for PrintingListener {
    type Message = EventMeshMessage;

    async fn handle(&self, message: Self::Message) -> Option<Self::Message> {
        let n = self.count.fetch_add(1, Ordering::Relaxed) + 1;
        println!(
            "[received #{n}] topic={:?} content={:?}",
            message.topic, message.content
        );
        None
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    let listener = Arc::new(PrintingListener {
        count: AtomicU64::new(0),
    });

    // Start the built-in webhook server on port 9090.
    // Bind to 0.0.0.0 so Docker-hosted runtimes can reach us, but advertise
    // 127.0.0.1 since that's where the standalone runtime (on the same host)
    // can POST back to.
    let addr: SocketAddr = "0.0.0.0:9090".parse().expect("valid addr");
    let server = WebhookServer::new(addr, listener.clone())
        .with_advertise_url("http://127.0.0.1:9090/eventmesh/callback");

    // Register the webhook URL with the EventMesh runtime.
    let config = HttpClientConfig::builder()
        .servers("127.0.0.1:10105")
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .consumer_group("test-consumerGroup-http")
        .build()?;

    let consumer = HttpConsumer::new(config, None::<std::future::Ready<()>>)?;

    let items = vec![SubscriptionItem::new(
        "test-topic-rust-http",
        SubscriptionMode::CLUSTERING,
        SubscriptionType::ASYNC,
    )];
    let webhook_url = server.url();
    println!("webhook URL: {webhook_url}");
    consumer.subscribe_webhook(items, webhook_url).await?;
    println!("subscribed; waiting for messages (Ctrl-C to stop)...");

    // Run the server until Ctrl-C.
    server
        .with_graceful_shutdown(async {
            tokio::signal::ctrl_c().await.ok();
        })
        .await?;

    consumer.shutdown().await;
    Ok(())
}
