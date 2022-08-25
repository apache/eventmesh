use crate::constants;
use crate::message::{EventMeshMessage, EventMeshMessageResp};

use super::config::EventMeshGrpcConfig;

use super::eventmesh_grpc::{
    publisher_service_client::PublisherServiceClient, RequestHeader, SimpleMessage,
};
use anyhow::Result;
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
