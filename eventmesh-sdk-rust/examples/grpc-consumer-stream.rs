use std::{net::SocketAddr, sync::Arc};

use anyhow::Result;
use eventmesh::grpc::{
    config::EventMeshGrpcConfig,
    consumer::EventMeshMessageConsumer,
    eventmesh_grpc::{
        subscription::{
            subscription_item::{SubscriptionMode, SubscriptionType},
            SubscriptionItem,
        },
        Subscription,
    },
};
use tokio::{select, signal, sync::Mutex};

#[tokio::main]
async fn main() -> Result<()> {
    let mut consumer = 
        EventMeshMessageConsumer::new(&EventMeshGrpcConfig {
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
    let item2 = item.clone();
    // let mut stream = consumer.create_stream().await?;
    // println!("stream created");
    let mut stream = consumer.subscribe_stream(&item).await?.unwrap();
    println!("subscribed");
    // let consumer = Arc::new(Mutex::new(consumer));
    // let consumer2 = consumer.clone();
    // println!("stream");
    // let server = tokio::spawn(async move {
        // println!("stream created");
        select! {
            _ = signal::ctrl_c() => {
                println!("shutting down");
                let resp = consumer.unsubscribe_stream(&item2).await;
                println!("unsubscribe {:?}", resp);
            },
            _ = async{
                while let Ok(msg) = stream.message().await{
                    match msg{
                        Some(msg) => println!("{:#?}",msg),
                        None => break,
                    }
                }
            } => {}
        }
    // });
    // println!("subscribe_stream");
    // server.await?;
    Ok(())
}
