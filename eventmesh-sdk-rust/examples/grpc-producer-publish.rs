use anyhow::Result;
use eventmesh::{
    grpc::{config::EventMeshGrpcConfig, producer::EventMeshMessageProducer},
    message::EventMeshMessage,
};
use serde::Serialize;

#[derive(Serialize)]
struct Content {
    content: String,
}

#[tokio::main]
async fn main() -> Result<()> {
    let mut producer = EventMeshMessageProducer::new(&EventMeshGrpcConfig {
        eventmesh_addr: "http://127.0.0.1:10205".to_string(),
        env: "env".to_string(),
        idc: "idc".to_string(),
        ip: "127.0.0.1".to_string(),
        pid: "1234".to_string(),
        sys: "1234".to_string(),
        user_name: "eventmesh".to_string(),
        password: "pass".to_string(),
        producer_group: "EventMeshTest-producerGroup".to_string(),
        consumer_group: "EventMeshTest-consumerGroup".to_string()
    }).await?;
    let resp = producer
        .publish(EventMeshMessage::new(
            "123456789012345678901234567891",
            "123456789012345678901234567892",
            "TEST-TOPIC-GRPC-BROADCAST",
            &serde_json::to_string(&Content {
                content: "testPublishMessage".to_string(),
            })?,
            5,
        ))
        .await?;
    println!("{:#?}", resp);
    Ok(())
}
