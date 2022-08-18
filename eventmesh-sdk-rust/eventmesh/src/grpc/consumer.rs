use std::{collections::HashMap, sync::Arc, time::Duration};

use futures::{stream, StreamExt};
use futures_util::Stream;
use tokio::{
    sync::{oneshot, Mutex},
    time::{interval, sleep},
};
use tonic::{
    transport::{channel, Channel, Endpoint},
    Request,
};

use super::{
    config::EventMeshGrpcConfig,
    eventmesh_grpc::{
        consumer_service_client::ConsumerServiceClient,
        consumer_service_server::ConsumerServiceServer,
        heartbeat::{ClientType, HeartbeatItem},
        heartbeat_service_client::HeartbeatServiceClient,
        subscription::SubscriptionItem,
        Heartbeat, RequestHeader, Response, Subscription,
    },
    SDK_STREAM_URL,
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
                heartbeat_items: map
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
    pub async fn new(config: &EventMeshGrpcConfig) -> Result<Self> {
        let channel = Endpoint::new(config.eventmesh_addr.clone())?
            .connect()
            .await?;
        let client = ConsumerServiceClient::new(channel.clone());
        let header = config.build_header();
        let subscription_map = Arc::new(Mutex::new(HashMap::new()));
        let heartbeat = HeartbeatController::new(
            subscription_map.clone(),
            channel,
            &header,
            &config.consumer_group,
        );
        Ok(EventMeshMessageConsumer {
            client,
            heartbeat,
            default_header: header,
            consumer_group: config.consumer_group.to_string(),
            subscription_map,
        })
    }
    pub async fn subscribe(
        &mut self,
        subscription_items: &Vec<SubscriptionItem>,
        url: &str,
    ) -> Result<Response> {
        self.add_subscription(subscription_items, url).await;
        let resp = self
            .client
            .subscribe(Subscription {
                header: Some(self.default_header.clone()),
                consumer_group: self.consumer_group.clone(),
                subscription_items: subscription_items.clone(),
                url: url.to_string(),
                reply: None,
            })
            .await?;
        Ok(resp.into_inner())
    }
    pub async fn unsubscribe(
        &mut self,
        subscription_items: &Vec<SubscriptionItem>,
        url: &str,
    ) -> Result<Response> {
        self.remove_subscription(subscription_items).await;
        let resp = self
            .client
            .unsubscribe(Subscription {
                header: Some(self.default_header.clone()),
                consumer_group: self.consumer_group.clone(),
                subscription_items: subscription_items.clone(),
                url: url.to_string(),
                reply: None,
            })
            .await?;
        Ok(resp.into_inner())
    }
    // pub async fn create_stream(&mut self) {

    // }
    // async fn subscribe<T>(&mut self, subscription_items_stream: &mut T)
    // where
    //     T: Stream<Item = Vec<SubscriptionItem>> + std::marker::Unpin,
    // {
    //     let subscription_stream = subscription_items_stream.then(|subscription_items| {
    //         let map = self.subscription_map.clone();
    //         let header = self.default_header.clone();
    //         let consumer_group=self.consumer_group.clone();

    //         async move {
    //             let mut map = map.lock().await;
    //             subscription_items.iter().for_each(|item| {
    //                 map.insert(item.topic.to_string(), SDK_STREAM_URL.to_string());
    //             });
    //             Subscription{
    //                 header: Some(header),
    //                 consumer_group,
    //                 subscription_items,
    //                 url: SDK_STREAM_URL.to_string(),
    //                 reply: None,
    //             }
    //         }
    //     });
    //     self.client.subscribe_stream(Request::new(tonic::codec::Streaming::from(subscription_stream)));
    //     while let Some(subscription_items) = subscription_items_stream.next().await {
    //         self.store_subscribes(&subscription_items, SDK_STREAM_URL).await;
    //     }
    //     self.store_subscribes(&subscription_items, SDK_STREAM_URL)
    //         .await;
    //     todo!()
    // }
    async fn add_subscription(&mut self, subscription_items: &Vec<SubscriptionItem>, url: &str) {
        let mut map = self.subscription_map.lock().await;
        subscription_items.iter().for_each(|item| {
            map.insert(item.topic.to_string(), url.to_string());
        });
    }
    async fn remove_subscription(&self, subscription_items: &Vec<SubscriptionItem>) {
        let mut map = self.subscription_map.lock().await;
        subscription_items.iter().for_each(|item| {
            map.remove(&item.topic);
        });
    }
}
