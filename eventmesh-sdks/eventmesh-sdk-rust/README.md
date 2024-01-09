## Eventmesh-rust-sdk

Eventmesh rust sdk

## Quickstart

### Requirements

1. rust toolchain, eventmesh's MSRV is 1.75.
2. protoc 3.15.0+
3. setup eventmesh runtime

### Add Dependency

```toml
[dependencies]
eventmesh = { version = "1.9", features = ["default"] }
```

### Send message

```rust
use std::time::{SystemTime, UNIX_EPOCH};
use tracing::info;

use eventmesh::config::EventMeshGrpcClientConfig;
use eventmesh::grpc::grpc_producer::EventMeshGrpcProducer;
use eventmesh::grpc::GrpcEventMeshMessageProducer;
use eventmesh::log;
use eventmesh::model::message::EventMeshMessage;

#[eventmesh::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    log::init_logger();

    let grpc_client_config = EventMeshGrpcClientConfig::new();
    let mut producer = GrpcEventMeshMessageProducer::new(grpc_client_config);

    //Publish Message
    info!("Publish Message to EventMesh........");
    let message = EventMeshMessage::default()
        .with_biz_seq_no("1")
        .with_content("123")
        .with_create_time(SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis() as u64)
        .with_topic("123")
        .with_unique_id("1111");
    let response = producer.publish(message.clone()).await?;
    info!("Publish Message to EventMesh return result: {}", response);

    //Publish batch message
    info!("Publish batch message to EventMesh........");
    let messages = vec![message.clone(), message.clone(), message.clone()];
    let response = producer.publish_batch(messages).await?;
    info!(
        "Publish batch message to EventMesh return result: {}",
        response
    );

    //Publish batch message
    info!("Publish request reply message to EventMesh........");
    let response = producer.request_reply(message.clone(), 1000).await?;
    info!(
        "Publish request reply message to EventMesh return result: {}",
        response
    );
    Ok(())
}
```

### Subscribe message

```rust
use std::time::Duration;

use tracing::info;

use eventmesh::common::ReceiveMessageListener;
use eventmesh::config::EventMeshGrpcClientConfig;
use eventmesh::grpc::grpc_consumer::EventMeshGrpcConsumer;
use eventmesh::log;
use eventmesh::model::message::EventMeshMessage;
use eventmesh::model::subscription::{SubscriptionItem, SubscriptionMode, SubscriptionType};

struct EventMeshListener;

impl ReceiveMessageListener for EventMeshListener {
    type Message = EventMeshMessage;

    fn handle(&self, msg: Self::Message) -> eventmesh::Result<std::option::Option<Self::Message>> {
        info!("Receive message from eventmesh================{:?}", msg);
        Ok(None)
    }
}

#[eventmesh::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    log::init_logger();
    let grpc_client_config = EventMeshGrpcClientConfig::new();
    let listener = Box::new(EventMeshListener);
    let mut consumer = EventMeshGrpcConsumer::new(grpc_client_config, listener);
    //send
    let item = SubscriptionItem::new(
        "TEST-TOPIC-GRPC-ASYNC",
        SubscriptionMode::CLUSTERING,
        SubscriptionType::ASYNC,
    );
    info!("==========Start consumer======================\n{}", item);
    let _response = consumer.subscribe(vec![item.clone()]).await?;
    tokio::time::sleep(Duration::from_secs(1000)).await;
    info!("=========Unsubscribe start================");
    let response = consumer.unsubscribe(vec![item.clone()]).await?;
    println!("unsubscribe result:{}", response);
    Ok(())
}

```

## Development Guide

### Dependencies

In order to build `tonic` >= 0.8.0, you need the `protoc` Protocol Buffers compiler, along with Protocol Buffers resource files.

#### Ubuntu

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y protobuf-compiler libprotobuf-dev
```

#### Alpine Linux

```sh
sudo apk add protoc protobuf-dev
```

#### macOS

Assuming [Homebrew](https://brew.sh/) is already installed. (If not, see instructions for installing Homebrew on [the Homebrew website](https://brew.sh/).)

```zsh
brew install protobuf
```

#### Windows

- Download the latest version of `protoc-xx.y-win64.zip` from [HERE](https://github.com/protocolbuffers/protobuf/releases/latest)
- Extract the file `bin\protoc.exe` and put it somewhere in the `PATH`
- Verify installation by opening a command prompt and enter `protoc --version`

### Build

```shell
cargo build
```

