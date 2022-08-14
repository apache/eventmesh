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
            producer_group: config.producergroup.clone(),
            default_header: RequestHeader {
                env: config.env.to_string(),
                region: String::from(""),
                idc: config.idc.to_string(),
                ip: config.ip.to_string(),
                pid: config.pid.to_string(),
                sys: config.sys.to_string(),
                username: config.user_name.to_string(),
                password: config.password.to_string(),
                language: String::from("RUST"),
                protocol_type: String::from(constants::EM_MESSAGE_PROTOCOL),
                protocol_version: String::from("1.0"),
                protocol_desc: String::from("grpc"),
            },
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
}
