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

use eventmesh::{
    config::{ConsumerOptions, Endpoint, EndpointSet, HttpConfig},
    message::Message,
    subscription::Subscription,
    webhook::WebhookOptions,
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
    let endpoints = EndpointSet::new([Endpoint::new("127.0.0.1", 10_105)?])?;
    let client = HttpClient::new(HttpConfig::new(endpoints))?;
    let consumer = client
        .consumer(
            ConsumerOptions::new("test-consumerGroup"),
            WebhookOptions::new("0.0.0.0:8080".parse().unwrap())
                .with_advertise_url("http://127.0.0.1:8080/eventmesh/callback"),
            [Subscription::new("test-topic-rust-sdk")],
            PrintHandler,
        )
        .await?;
    consumer.join().await
}
