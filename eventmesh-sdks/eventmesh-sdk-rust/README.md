
# eventmesh-rust-sdk

A Rust client SDK for [Apache EventMesh](https://eventmesh.apache.org), the
serverless event-driven middleware.

This crate (`eventmesh`) speaks the EventMesh **gRPC**, **HTTP**, and **TCP**
protocols. Messages are modeled with the simple
[`EventMeshMessage`](src/model/message.rs) type, an OpenMessaging-style
[`OpenMessage`](src/model/open_message.rs) compatibility model, and optional
native [CloudEvents](https://cloudevents.io) interop behind the
`cloud_events` feature.

The gRPC transport supports publish, batch publish, request-reply, stream and
webhook subscription, and heartbeat. HTTP supports publish, request-reply and
webhook subscription. TCP supports publish, broadcast, request-reply,
subscription and automatic reconnection.

## Requirements

- Rust toolchain **>= 1.86.0**
- `protoc` >= 3.15 (the Protocol Buffers compiler) on your `PATH` or pointed to
  by the `PROTOC` env var. `tonic-build` invokes it at build time.
- A running EventMesh runtime (gRPC on port `10205`).

### Installing protoc

```bash
# Ubuntu / Debian
sudo apt-get install -y protobuf-compiler
# Alpine
apk add protobuf-dev protoc
# macOS
brew install protobuf
# or download a release binary from https://github.com/protocolbuffers/protobuf/releases
```

## Usage

Add the dependency:

```toml
[dependencies]
eventmesh = { version = "1.9", features = ["default"] }   # gRPC + EventMeshMessage
# Optional extras:
# eventmesh = { version = "1.9", features = ["grpc", "http", "tcp", "cloud_events", "tls"] }
```

### Publish a message

```rust
use eventmesh::{
    config::GrpcClientConfig, grpc::GrpcProducer, model::EventMeshMessage, transport::Publisher,
};

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let config = GrpcClientConfig::builder()
        .server_addr("127.0.0.1")
        .server_port(10205)
        .env("env").idc("idc").sys("sys")
        .username("eventmesh").password("eventmesh")   // required by the server
        .producer_group("test-producerGroup")
        .build();

    let producer = GrpcProducer::connect(config)?;

    let msg = EventMeshMessage::builder()
        .topic("test-topic-rust-sdk")
        .content("hello from rust")
        .build();

    let resp = producer.publish(msg).await?;
    println!("published: {resp}");
    Ok(())
}
```

`producer.publish_batch(vec![...])` sends many messages in one RPC, and
`producer.request_reply(msg, timeout)` performs a synchronous request/reply
(returns the consumer's reply message).

### Subscribe (stream)

```rust
use std::sync::atomic::{AtomicU64, Ordering};
use eventmesh::{
    config::GrpcClientConfig, grpc::GrpcStreamConsumer, model::{EventMeshMessage,
        SubscriptionItem, SubscriptionMode, SubscriptionType},
    MessageListener,
};

struct MyListener { n: AtomicU64 }

impl MessageListener for MyListener {
    type Message = EventMeshMessage;
    async fn handle(&self, msg: Self::Message) -> Option<Self::Message> {
        println!("[{}] topic={:?} content={:?}", self.n.fetch_add(1, Ordering::Relaxed), msg.topic, msg.content);
        None // None = ack only; Some(msg) = reply (for request/reply topics)
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let config = GrpcClientConfig::builder()
        .server_addr("127.0.0.1").server_port(10205)
        .env("env").idc("idc").sys("sys")
        .username("eventmesh").password("eventmesh")
        .consumer_group("test-consumerGroup")
        .build();

    let consumer = GrpcStreamConsumer::subscribe_stream(
        config,
        MyListener { n: AtomicU64::new(0) },
        vec![SubscriptionItem::new(
            "test-topic-rust-sdk", SubscriptionMode::CLUSTERING, SubscriptionType::ASYNC,
        )],
        Some(async { tokio::signal::ctrl_c().await.ok(); }),
    ).await?;

    // subscribe/unsubscribe can be called at any time on the open stream
    // consumer.subscribe(more_items).await?;
    // consumer.unsubscribe(items).await?;

    // blocks until Ctrl-C or the stream closes
    consumer.wait_for_shutdown().await;
    Ok(())
}
```

A webhook subscription (`GrpcWebhookConsumer::new` / `subscribe_webhook(items, url)`)
is also available — the server POSTs delivered events to the given URL.

### Catalog, Workflow, and service discovery

Catalog and Workflow clients use a caller-supplied `ServiceDiscovery`
implementation. This keeps the SDK independent of a particular registry while
matching the Java SDK's `Selector` behavior. Implement the trait with your
existing Nacos, Consul, Kubernetes, or other registry client:

```rust
use std::future::Future;
use eventmesh::{
    config::WorkflowClientConfig,
    discovery::{ServiceDiscovery, ServiceInstance},
    workflow::{ExecuteRequest, WorkflowClient},
    Result,
};

struct StaticDiscovery;

impl ServiceDiscovery for StaticDiscovery {
    fn select_one(
        &self,
        service_name: String,
    ) -> impl Future<Output = Result<Option<ServiceInstance>>> + Send {
        async move {
            assert_eq!(service_name, "eventmesh-workflow");
            Ok(Some(ServiceInstance::new("127.0.0.1", 9000)))
        }
    }
}

async fn start_workflow() -> Result<()> {
    let client = WorkflowClient::new(WorkflowClientConfig::default(), StaticDiscovery);
    let response = client.execute(ExecuteRequest {
        id: "order-flow".into(),
        instance_id: String::new(),
        task_instance_id: String::new(),
        input: r#"{"order_no":"42"}"#.into(),
    }).await?;
    println!("workflow instance: {}", response.instance_id);
    Ok(())
}
```

To synchronize an existing `GrpcStreamConsumer` with Catalog, construct a
`CatalogClientConfig` with its required `app_server_name`, then call
`catalog.init(&consumer).await?`. It queries `QueryOperations`, subscribes to
`subscribe` operations only, and `catalog.destroy(&consumer).await?` removes
only the subscriptions it created.

## Features

| Feature | Description |
|---|---|
| `grpc` (default) | gRPC transport (producer, consumer, heartbeat), Catalog and Workflow clients |
| `http` | HTTP producer, webhook consumer, and built-in webhook server |
| `tcp` | Native TCP producer and consumer with reconnect support |
| `cloud_events` | Native `cloudevents::Event` interop |
| `tls` | TLS for the gRPC channel |
| `full` | `grpc` + `http` + `tcp` + `cloud_events` + `tls` |

## Running the examples

The examples assume a standalone EventMesh is running on `127.0.0.1`:

```bash
# from this crate's directory (docker-compose.yml ships with the SDK)
docker compose --profile standalone up -d
```

> **Standalone-broker note:** the in-memory broker requires a topic to be
> created **and** a consumer subscribed before a producer can publish. Create
> the topic once, then start the consumer before the producer:
>
> ```bash
> # create the topic via the admin API (port 10106)
> curl -X POST http://127.0.0.1:10106/topic -H 'Content-Type: application/json' -d '{"name":"test-topic-rust-sdk"}'
>
> # terminal 1 — receive
> cargo run --features grpc --example grpc_consumer
> # terminal 2 — send
> cargo run --features grpc --example grpc_producer
> ```
>
> (With the RocketMQ backend, topics are auto-created and this dance is not
> needed.)

## Development

```bash
cargo fmt
cargo clippy --features full --all-targets -- -D warnings
cargo test --features full
```

## End-to-end tests

The `e2e` test suite (`tests/e2e/`) exercises the full gRPC producer/consumer
against a live EventMesh runtime. It is gated behind the `e2e` feature so a
plain `cargo test` never touches Docker.

```bash
# Auto-start the standalone stack via docker compose, run the suite, then stop it:
cargo test --features e2e

# ...or run against a server you already started yourself:
EVENTMESH_E2E_EXTERNAL=1 cargo test --features e2e
```

When neither Docker nor a reachable server is found, every test skips itself
rather than failing. Tests run in parallel by default; each one uses a unique
topic and consumer group so they never collide on the shared broker.

> **Standalone limitations:** the in-memory broker requires a topic to be
> created *and* a consumer subscribed before publishing (the harness does this
> automatically), and it does **not** implement synchronous request/reply. The
> request/reply test detects this and skips the assertion on standalone; switch
> to the RocketMQ profile (`docker compose --profile rocketmq up -d`) to exercise
> it fully.

## License

Apache License 2.0.
