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
    config::{ConsumerOptions, Endpoint, GrpcConfig},
    GrpcClient, Subscription,
};

const TOPIC: &str = "test-topic-rust-sdk";
const WEBHOOK_URL: &str = "http://127.0.0.1:8080/eventmesh/callback";

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    // Start an HTTP endpoint at WEBHOOK_URL before running this example.
    let client = GrpcClient::new(GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?))?;
    let consumer = client
        .webhook_consumer(ConsumerOptions::new("test-consumerGroup"))
        .await?;
    let subscription = Subscription::new(TOPIC);

    consumer
        .subscribe([subscription.clone()], WEBHOOK_URL)
        .await?;
    println!("registered {TOPIC}; press Ctrl-C to unregister and exit");
    tokio::signal::ctrl_c().await?;

    // shutdown() only stops local heartbeat work. Explicitly remove the
    // remote registration first so it does not linger until server expiry.
    let unsubscribe_result = consumer.unsubscribe([subscription], WEBHOOK_URL).await;
    consumer.shutdown();
    let join_result = consumer.join().await;
    unsubscribe_result?;
    join_result
}
