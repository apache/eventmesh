use std::{collections::HashMap, time::Duration};

use tokio::time::{interval, sleep};
use tonic::transport::{Channel, Endpoint};

use super::{
    config::EventMeshGrpcConfig,
    eventmesh_grpc::{
        consumer_service_client::ConsumerServiceClient,
        consumer_service_server::ConsumerServiceServer,
        heartbeat::{ClientType, HeartbeatItem},
        heartbeat_service_client::HeartbeatServiceClient,
        Heartbeat, RequestHeader,
    },
};
use anyhow::Result;
#[derive(Clone, Debug)]
pub struct EventMeshMessageConsumer {
    client: ConsumerServiceClient<Channel>,
    heartbeat_client: HeartbeatServiceClient<Channel>,
    default_header: RequestHeader,
    // producer_group: String,
    consumer_group: String,
    subscription_map: HashMap<String, String>,
}
impl EventMeshMessageConsumer {
    pub async fn new(config: &EventMeshGrpcConfig) -> Result<Self> {
        let channel = Endpoint::new(config.eventmesh_addr.clone())?
            .connect()
            .await?;
        let client = ConsumerServiceClient::new(channel.clone());
        let heartbeat_client = HeartbeatServiceClient::new(channel);
        Ok(EventMeshMessageConsumer {
            client,
            heartbeat_client,
            default_header: config.build_header(),
            consumer_group: config.consumer_group.to_string(),
            subscription_map: Default::default(),
        })
    }
    async fn heartbeat(&mut self) -> Result<()> {
        let resp = self
            .heartbeat_client
            .heartbeat(Heartbeat {
                header: Some(self.default_header.clone()),
                client_type: ClientType::Sub.into(),
                // not set in java
                producer_group: String::from(""),
                consumer_group: self.consumer_group.to_string(),
                heartbeat_items: self
                    .subscription_map
                    .iter()
                    .map(|(key, value)| HeartbeatItem {
                        topic: key.to_string(),
                        url: value.to_string(),
                    })
                    .collect(),
            })
            .await?;
        println!("heartbear: {:?}", resp.into_inner());
        Ok(())
    }
    // todo cancel?
    async fn start_heartbeat(&mut self) -> Result<()> {
        // EventMeshCommon.HEARTBEAT 30000
        let mut interval = interval(Duration::from_millis(30000));
        sleep(Duration::from_millis(10000)).await;
        loop {
            self.heartbeat().await?;
            interval.tick().await;
        }
    }
}
