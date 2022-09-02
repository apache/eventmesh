use std::{net::SocketAddr, sync::Arc};

use anyhow::Result;
use axum::{
    http::{HeaderMap, StatusCode},
    routing::post,
    Router,
};
use eventmesh::grpc::{
    config::EventMeshGrpcConfig,
    consumer::EventMeshMessageConsumer,
    eventmesh_grpc::subscription::{
        subscription_item::{SubscriptionMode, SubscriptionType},
        SubscriptionItem,
    },
};
use tokio::{select, signal, sync::Mutex};
async fn handler(headers: HeaderMap, body: String) -> StatusCode {
    println!("{:#?} {:#?}", headers, body);
    StatusCode::OK
}
#[tokio::main]
async fn main() -> Result<()> {
    let app = Router::new().route("/", post(handler));
    let addr = SocketAddr::from(([127, 0, 0, 1], 8088));
    println!("listening on {}", addr);
    let consumer = Arc::new(Mutex::new(EventMeshMessageConsumer::new(&EventMeshGrpcConfig {
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
    .await?));
    let item = vec![SubscriptionItem {
        topic: String::from("TEST-TOPIC-GRPC-BROADCAST"),
        mode: SubscriptionMode::Broadcasting.into(),
        r#type: SubscriptionType::Sync.into(),
    }];
    let consumer2 = consumer.clone();
    let item2 = item.clone();
    let url = "http://host.docker.internal:8088/";
    let server = tokio::spawn(async move {
        select! {
            _ = axum::Server::bind(&addr).serve(app.into_make_service()) => {},
            _ = signal::ctrl_c() => {
                println!("shutting down");
                let mut consumer = consumer2.lock().await;
                let resp = consumer.unsubscribe(&item2, url).await;
                println!("unsubscribe {:?}", resp);
            }
        };
    });

    let resp = consumer.lock().await.subscribe(&item, url).await?;
    println!("subscribe {:?}", resp);
    server.await?;
    Ok(())
}
