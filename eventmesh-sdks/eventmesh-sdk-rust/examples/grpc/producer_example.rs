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
use chrono::Utc;
use cloudevents::{EventBuilder, EventBuilderV10};
use std::time::{SystemTime, UNIX_EPOCH};
use tracing::info;

use eventmesh::config::EventMeshGrpcClientConfig;
use eventmesh::grpc::grpc_producer::EventMeshGrpcProducer;
use eventmesh::grpc::GrpcEventMeshProducer;
use eventmesh::log;
use eventmesh::model::message::EventMeshMessage;

#[eventmesh::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    log::init_logger();

    //Publish Message
    #[cfg(feature = "eventmesh_message")]
    {
        let grpc_client_config = EventMeshGrpcClientConfig::new();
        let mut producer = GrpcEventMeshProducer::new(grpc_client_config);
        info!("Publish Message to EventMesh........");
        let message = EventMeshMessage::default()
            .with_biz_seq_no("1")
            .with_content("123")
            .with_create_time(SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis() as u64)
            .with_topic("123")
            .with_unique_id("1111");
        let response = producer.publish(message.clone()).await?;
        info!("Publish Message to EventMesh return result: {}", response);

        //Publish batch message
        info!("Publish batch message to EventMesh........");
        let messages = vec![message.clone(), message.clone(), message.clone()];
        let response = producer.publish_batch(messages).await?;
        info!(
            "Publish batch message to EventMesh return result: {}",
            response
        );

        //Publish batch message
        info!("Publish request reply message to EventMesh........");
        let response = producer.request_reply(message.clone(), 1000).await?;
        info!(
            "Publish request reply message to EventMesh return result: {}",
            response
        );
    }

    #[cfg(feature = "cloud_events")]
    {
        let grpc_client_config = EventMeshGrpcClientConfig::new();
        let mut producer = GrpcEventMeshProducer::new(grpc_client_config);
        info!("Publish Message to EventMesh........");
        let message = EventBuilderV10::new()
            .id("my_event.my_application")
            .source("http://localhost:8080")
            .ty("example.demo")
            .time(Utc::now())
            .build()?;
        let response = producer.publish(message.clone()).await?;
        info!("Publish Message to EventMesh return result: {}", response);

        //Publish batch message
        info!("Publish batch message to EventMesh........");
        let messages = vec![message.clone(), message.clone(), message.clone()];
        let response = producer.publish_batch(messages).await?;
        info!(
            "Publish batch message to EventMesh return result: {}",
            response
        );

        //Publish batch message
        info!("Publish request reply message to EventMesh........");
        let response = producer.request_reply(message.clone(), 1000).await?;
        info!(
            "Publish request reply message to EventMesh return result: {}",
            response
        );
    }

    Ok(())
}
