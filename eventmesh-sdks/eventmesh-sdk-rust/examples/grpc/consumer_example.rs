/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
use std::time::Duration;

use tracing::info;

use eventmesh::common::ReceiveMessageListener;
use eventmesh::config::EventMeshGrpcClientConfig;
use eventmesh::grpc::grpc_consumer::EventMeshGrpcConsumer;
use eventmesh::log;
use eventmesh::model::message::EventMeshMessage;
use eventmesh::model::subscription::{SubscriptionItem, SubscriptionMode, SubscriptionType};

struct EventMeshListener;

impl ReceiveMessageListener for EventMeshListener {
    type Message = EventMeshMessage;

    fn handle(&self, msg: Self::Message) -> eventmesh::Result<std::option::Option<Self::Message>> {
        info!("Receive message from eventmesh================{:?}", msg);
        Ok(None)
    }
}

#[eventmesh::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    log::init_logger();
    let grpc_client_config = EventMeshGrpcClientConfig::new();
    let listener = Box::new(EventMeshListener);
    let mut consumer = EventMeshGrpcConsumer::new(grpc_client_config, listener);
    //send
    let item = SubscriptionItem::new(
        "TEST-TOPIC-GRPC-ASYNC",
        SubscriptionMode::CLUSTERING,
        SubscriptionType::ASYNC,
    );
    info!("==========Start consumer======================\n{}", item);
    let _response = consumer.subscribe(vec![item.clone()]).await?;
    tokio::time::sleep(Duration::from_secs(1000)).await;
    info!("=========Unsubscribe start================");
    let response = consumer.unsubscribe(vec![item.clone()]).await?;
    println!("unsubscribe result:{}", response);
    Ok(())
}
