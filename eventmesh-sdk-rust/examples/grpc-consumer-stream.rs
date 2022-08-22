use anyhow::Result;
use eventmesh::grpc::{
    config::EventMeshGrpcConfig,
    consumer::EventMeshMessageConsumer,
    eventmesh_grpc::{
        subscription::{
            subscription_item::{SubscriptionMode, SubscriptionType},
            SubscriptionItem,
        },
    },
};
use tokio::{select, signal};
use tracing::{Level, info};

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
                    Some(msg) => info!("{:#?}",msg),
                    None => { info!("stream end"); break; },
                }
            };
            let resp = consumer.unsubscribe_stream(&item2).await;
            info!("unsubscribe {:?}", resp);
        } => {}
    }
    Ok(())
}
