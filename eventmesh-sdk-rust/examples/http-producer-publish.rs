use anyhow::Result;
use eventmesh::{
    http::{config::EventMeshHttpConfig, producer::EventMeshMessageProducer},
    message::EventMeshMessage,
};
use serde::Serialize;

#[derive(Serialize)]
struct Content {
    content: String,
}

#[tokio::main]
async fn main() -> Result<()> {
    let producer = EventMeshMessageProducer::new(&EventMeshHttpConfig {
        eventmesh_addr: "http://127.0.0.1:10105".to_string(),
        env: "env".to_string(),
        idc: "idc".to_string(),
        ip: "127.0.0.1".to_string(),
        pid: "1234".to_string(),
        sys: "1234".to_string(),
        user_name: "eventmesh".to_string(),
        password: "pass".to_string(),
        producergroup: "EventMeshTest-producerGroup".to_string(),
    })?;
    let resp = producer
        .publish(EventMeshMessage::new(
            "123456789012345678901234567891",
            "123456789012345678901234567892",
            "TEST-TOPIC-HTTP-ASYNC",
            &serde_json::to_string(&Content {
                content: "testPublishMessage".to_string(),
            })?,
            5,
        ))
        .await?;
    println!("{:#?}", resp);
    Ok(())
}
