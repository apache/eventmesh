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

use std::collections::HashMap;

use crate::constants;
use crate::message::{EventMeshMessage, EventMeshMessageResp};

use super::config::EventMeshGrpcConfig;

use super::eventmesh_grpc::batch_message::MessageItem;
use super::eventmesh_grpc::BatchMessage;
use super::eventmesh_grpc::{
    publisher_service_client::PublisherServiceClient, RequestHeader, SimpleMessage,
};
use anyhow::{bail, Result};
use tonic::transport::Channel;
pub struct EventMeshMessageProducer {
    client: PublisherServiceClient<Channel>,
    producer_group: String,
    default_header: RequestHeader,
}
impl EventMeshMessageProducer {
    pub async fn new(config: &EventMeshGrpcConfig) -> Result<Self> {
        let client = PublisherServiceClient::connect(config.eventmesh_addr.clone()).await?;
        Ok(EventMeshMessageProducer {
            client,
            producer_group: config.producer_group.clone(),
            default_header: config.build_header(),
        })
    }
    pub async fn batch_publish(
        &mut self,
        messages: &Vec<EventMeshMessage>,
    ) -> Result<EventMeshMessageResp> {
        if messages.is_empty() {
            bail!("empty messages")
        }
        let resp = self
            .client
            .batch_publish(BatchMessage {
                header: Some(self.default_header.clone()),
                producer_group: self.producer_group.to_string(),
                topic: messages[0].topic.to_string(),
                message_item: messages
                    .iter()
                    .map(
                        |EventMeshMessage {
                             content,
                             ttl,
                             unique_id,
                             biz_seq_no,
                             topic: _,
                         }| MessageItem {
                            content: content.clone(),
                            ttl: ttl.to_string(),
                            unique_id: unique_id.clone(),
                            seq_num: biz_seq_no.clone(),
                            tag: String::from(""),
                            properties: HashMap::new(),
                        },
                    )
                    .collect(),
            })
            .await?
            .into_inner();
        Ok(EventMeshMessageResp {
            ret_code: resp.resp_code.parse()?,
            ret_msg: resp.resp_msg,
            res_time: resp.resp_time.parse()?,
        })
    }
    pub async fn publish(&mut self, message: EventMeshMessage) -> Result<EventMeshMessageResp> {
        let resp = self
            .client
            .publish(SimpleMessage {
                header: Some(self.default_header.clone()),
                producer_group: self.producer_group.to_string(),
                topic: message.topic,
                content: message.content,
                ttl: message.ttl.to_string(),
                unique_id: message.unique_id,
                seq_num: message.biz_seq_no,
                tag: "".to_string(),
                properties: Default::default(),
            })
            .await?
            .into_inner();
        Ok(EventMeshMessageResp {
            ret_code: resp.resp_code.parse()?,
            ret_msg: resp.resp_msg,
            res_time: resp.resp_time.parse()?,
        })
    }
    pub async fn request(&mut self, message: EventMeshMessage) -> Result<SimpleMessage> {
        let resp = self
            .client
            .request_reply(SimpleMessage {
                header: Some(self.default_header.clone()),
                producer_group: self.producer_group.to_string(),
                topic: message.topic,
                content: message.content,
                ttl: message.ttl.to_string(),
                unique_id: message.unique_id,
                seq_num: message.biz_seq_no,
                tag: "".to_string(),
                properties: Default::default(),
            })
            .await?
            .into_inner();
        Ok(resp)
    }
}
