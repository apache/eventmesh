use std::{collections::HashMap, time::Duration, sync::Arc};

use tokio::{
    sync::{oneshot, Mutex},
    time::{interval, sleep},
};
use tonic::transport::{channel, Channel, Endpoint};

use super::{
    config::EventMeshGrpcConfig,
    eventmesh_grpc::{
        consumer_service_client::ConsumerServiceClient,
        consumer_service_server::ConsumerServiceServer,
        heartbeat::{ClientType, HeartbeatItem},
        heartbeat_service_client::HeartbeatServiceClient,
        Heartbeat, RequestHeader, subscription::SubscriptionItem,
    }, SDK_STREAM_URL,
};
use anyhow::Result;
#[derive(Debug)]
struct HeartbeatInterval {
    heartbeat_client: HeartbeatServiceClient<Channel>,
    subscription_map: Arc<Mutex<HashMap<String, String>>>,
    default_header: RequestHeader,
    consumer_group: String,
}
impl HeartbeatInterval {
    async fn send_heartbeat(&mut self) -> Result<()> {
        let map = self.subscription_map.lock().await;
        let resp = self
            .heartbeat_client
            .heartbeat(Heartbeat {
                header: Some(self.default_header.clone()),
                client_type: ClientType::Sub.into(),
                // not set in java
                producer_group: String::from(""),
                consumer_group: self.consumer_group.to_string(),
                heartbeat_items: map.iter()
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

    async fn heartbeat(&mut self) -> Result<()> {
        // EventMeshCommon.HEARTBEAT 30000
        let mut interval = interval(Duration::from_millis(30000));
        sleep(Duration::from_millis(10000)).await;
        loop {
            self.send_heartbeat().await?;
            interval.tick().await;
        }
    }
}
#[derive(Debug)]
struct HeartbeatController {
    receiver: oneshot::Receiver<()>,
}
impl HeartbeatController {
    fn new(
        map: Arc<Mutex<HashMap<String, String>>>,
        channel: Channel,
        default_header: &RequestHeader,
        consumer_group: &str,
    ) -> Self {
        let (mut sender, receiver) = oneshot::channel();
        let mut interval = HeartbeatInterval {
            heartbeat_client: HeartbeatServiceClient::new(channel),
            subscription_map: map,
            default_header: default_header.clone(),
            consumer_group: consumer_group.to_string(),
        };
        tokio::spawn(async move {
            tokio::select! {
                _ = interval.heartbeat() => {},
                _ = sender.closed() => {}
            }
        });
        HeartbeatController { receiver }
    }
}
#[derive(Debug)]
pub struct EventMeshMessageConsumer {
    client: ConsumerServiceClient<Channel>,
    heartbeat: HeartbeatController,
    default_header: RequestHeader,
    // producer_group: String,
    consumer_group: String,
    subscription_map: Arc<Mutex<HashMap<String, String>>>,
}
impl EventMeshMessageConsumer {
    pub async fn new(
        config: &EventMeshGrpcConfig,
    ) -> Result<Self> {
        let channel = Endpoint::new(config.eventmesh_addr.clone())?
            .connect()
            .await?;
        let client = ConsumerServiceClient::new(channel.clone());
        let header = config.build_header();
        let subscription_map = Arc::new(Mutex::new(HashMap::new()));
        let heartbeat =
            HeartbeatController::new(subscription_map.clone(), channel, &header, &config.consumer_group);
        Ok(EventMeshMessageConsumer {
            client,
            heartbeat,
            default_header: header,
            consumer_group: config.consumer_group.to_string(),
            subscription_map,
        })
    }
    pub async fn subscribe(&mut self, mut subscription_items: Vec<SubscriptionItem>){
        let mut map = self.subscription_map.lock().await;
        subscription_items.drain(..).for_each(|item|{
            map.insert(item.topic, SDK_STREAM_URL.to_string());
        })
    }
}
