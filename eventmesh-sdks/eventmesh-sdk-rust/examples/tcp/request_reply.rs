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
    config::{ConsumerOptions, Endpoint, ProducerOptions, TcpConfig},
    DeliveryType, EventMeshMessage, Message, Subscription, TcpClient,
};
use std::time::Duration;

const TOPIC: &str = "test-topic-rust-sdk";

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let client = TcpClient::new(TcpConfig::new(Endpoint::new("127.0.0.1", 10_000)?))?;
    let consumer = client
        .consumer(
            ConsumerOptions::new("test-consumerGroup"),
            |request: Message| async move {
                let request = request.into_event_mesh()?;
                Ok(Some(Message::from(EventMeshMessage::new(
                    request.topic(),
                    "pong",
                )?)))
            },
        )
        .await?;
    consumer
        .subscribe(Subscription::new(TOPIC).with_delivery_type(DeliveryType::Sync))
        .await?;
    let producer = client
        .producer(ProducerOptions::new("test-producerGroup"))
        .await?;

    // The stock EventMesh runtime refreshes TCP subscription routes
    // periodically, so wait for the first refresh before sending a request.
    tokio::time::sleep(Duration::from_secs(45)).await;
    println!(
        "reply: {:?}",
        producer
            .request_reply(Message::from(EventMeshMessage::new(TOPIC, "ping")?))
            .await?
    );

    producer.shutdown().await;
    consumer.shutdown();
    consumer.join().await
}
