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

//! The built-in server is the v2 customisation boundary: applications provide
//! a [`MessageHandler`] and keep their web framework private to the app.

use eventmesh::{
    config::{ConsumerOptions, Endpoint, EndpointSet, HttpConfig},
    message::Message,
    subscription::Subscription,
    webhook::WebhookServer,
    HttpClient, MessageHandler,
};

struct PrintHandler;

impl MessageHandler for PrintHandler {
    async fn handle(&self, message: Message) -> eventmesh::Result<Option<Message>> {
        println!("received: {message:?}");
        Ok(None)
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let server = WebhookServer::new("0.0.0.0:8081".parse().unwrap(), PrintHandler)
        .with_advertise_url("http://127.0.0.1:8081/eventmesh/callback");
    let client = HttpClient::new(HttpConfig::new(EndpointSet::new([Endpoint::new(
        "127.0.0.1",
        10_105,
    )?])?))?;
    let consumer = client.webhook_consumer(ConsumerOptions::new("test-consumerGroup"))?;
    consumer
        .subscribe(Subscription::new("test-topic-rust-sdk"), server.url())
        .await?;
    server.await
}
