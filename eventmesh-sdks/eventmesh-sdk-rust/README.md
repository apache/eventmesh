<!--
~ Licensed to the Apache Software Foundation (ASF) under one or more
~ contributor license agreements.  See the NOTICE file distributed with this
~ work for additional information regarding copyright ownership.  The ASF
~ licenses this file to You under the Apache License, Version 2.0 (the
~ "License"); you may not use this file except in compliance with the
~ License.  You may obtain a copy of the License at
~
~     http://www.apache.org/licenses/LICENSE-2.0
-->

# eventmesh-rust-sdk

A Rust client SDK for [Apache EventMesh](https://eventmesh.apache.org), the
serverless event-driven middleware.

This crate (`eventmesh`) speaks the EventMesh **gRPC** protocol (HTTP and TCP
transports are planned for later phases). Messages are modeled with the simple
[`EventMeshMessage`](src/model/message.rs) type, with optional native
[CloudEvents](https://cloudevents.io) interop behind the `cloud_events` feature.

> Phase 1 (this release): **gRPC transport** — publish, batch publish,
> request-reply, stream subscription, webhook subscription, heartbeat, and
> CloudEvents interop.

## Requirements

- Rust toolchain **>= 1.75.0**
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
# eventmesh = { version = "1.9", features = ["grpc", "cloud_events", "tls"] }
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
    config::GrpcClientConfig, grpc::GrpcConsumer, model::{EventMeshMessage,
        SubscriptionItem, SubscriptionMode, SubscriptionType},
    transport::Subscriber, MessageListener,
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

    let consumer = GrpcConsumer::new(config, MyListener { n: AtomicU64::new(0) })?;
    consumer.subscribe(vec![SubscriptionItem::new(
        "test-topic-rust-sdk", SubscriptionMode::CLUSTERING, SubscriptionType::ASYNC,
    )]).await?;

    // keep the process alive so the stream + heartbeat keep running
    tokio::signal::ctrl_c().await.ok();
    Ok(())
}
```

A webhook subscription (`consumer.subscribe_webhook(items, url)`) is also
available — the server POSTs delivered events to the given URL.

## Features

| Feature | Description |
|---|---|
| `grpc` (default) | gRPC transport (producer, consumer, heartbeat) |
| `cloud_events` | Native `cloudevents::Event` interop |
| `tls` | TLS for the gRPC channel |
| `full` | `grpc` + `cloud_events` + `tls` |

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
> PROTOC=$HOME/.local/bin/protoc cargo run --features grpc --example grpc_consumer
> # terminal 2 — send
> PROTOC=$HOME/.local/bin/protoc cargo run --features grpc --example grpc_producer
> ```
>
> (With the RocketMQ backend, topics are auto-created and this dance is not
> needed.)

## Development

```bash
PROTOC=$HOME/.local/bin/protoc cargo fmt
PROTOC=$HOME/.local/bin/protoc cargo clippy --features full --all-targets -- -D warnings
PROTOC=$HOME/.local/bin/protoc cargo test --features full
```

## End-to-end tests

The `e2e` test suite (`tests/e2e/`) exercises the full gRPC producer/consumer
against a live EventMesh runtime. It is gated behind the `e2e` feature so a
plain `cargo test` never touches Docker.

```bash
# Auto-start the standalone stack via docker compose, run the suite, then stop it:
PROTOC=$HOME/.local/bin/protoc cargo test --features e2e

# ...or run against a server you already started yourself:
EVENTMESH_E2E_EXTERNAL=1 \
PROTOC=$HOME/.local/bin/protoc cargo test --features e2e
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
