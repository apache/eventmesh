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

use std::fmt;

use anyhow::Result;
use eventmesh::{
    grpc::{
        config::EventMeshGrpcConfig,
        consumer::EventMeshMessageConsumer,
        eventmesh_grpc::subscription::{
            subscription_item::{SubscriptionMode, SubscriptionType},
            SubscriptionItem,
        },
    },
    message::EventMeshMessage,
};
use serde::Serialize;
use tokio::{select, signal};
use tracing::{info, Level};
#[derive(Serialize)]
struct Content {
    content: String,
}
#[tokio::main]
async fn main() -> Result<()> {
    let collector = tracing_subscriber::fmt().init();
    info!("start!");
    let mut consumer = EventMeshMessageConsumer::new(&EventMeshGrpcConfig {
        eventmesh_addr: "http://127.0.0.1:10205".to_string(),
        env: "env".to_string(),
        idc: "idc".to_string(),
        ip: "127.0.0.1".to_string(),
        pid: "1234".to_string(),
        sys: "1234".to_string(),
        user_name: "eventmesh".to_string(),
        password: "pass".to_string(),
        producer_group: "EventMeshTest-producerGroup".to_string(),
        consumer_group: "EventMeshTest-consumerGroup".to_string(),
    })
    .await?;
    let item = vec![SubscriptionItem {
        topic: String::from("TEST-TOPIC-GRPC-BROADCAST"),
        mode: SubscriptionMode::Broadcasting.into(),
        r#type: SubscriptionType::Sync.into(),
    }];
    let mut stream = consumer.subscribe_stream(&item).await.unwrap().unwrap();
    let item2 = item.clone();

    select! {
        _ = signal::ctrl_c() => {
            info!("shutting down");
            let resp = consumer.unsubscribe_stream(&item2).await;
            info!("unsubscribe {:?}", resp);
        },
        _ = async {
            while let Ok(msg) = stream.message().await{
                match msg{
                    Some(msg) => {
                        info!("receive: {:#?}", msg);
                        consumer.stream_reply(&EventMeshMessage::new(
                            "123456789012345678901234567893",
                            "123456789012345678901234567894",
                            "TEST-TOPIC-GRPC-BROADCAST",
                            &serde_json::to_string(&Content {
                                content: format!("testReplyMessage: {}",msg.content).to_string(),
                            }).unwrap(),
                            5,
                        )).await.unwrap();
                    },
                    None => { info!("stream end"); break; },
                }
            };
            let resp = consumer.unsubscribe_stream(&item2).await;
            info!("unsubscribe {:?}", resp);
        } => {}
    }
    Ok(())
}
