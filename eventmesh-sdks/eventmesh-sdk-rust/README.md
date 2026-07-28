# Apache EventMesh Rust SDK

`eventmesh` is the Rust SDK for [Apache EventMesh](https://eventmesh.apache.org). It provides separate, feature-gated gRPC, HTTP, and TCP clients over a shared message and configuration API.

## Requirements

- Rust 1.86 or newer
- `protoc` 3.15 or newer when enabling `grpc` (including `full` or `e2e`)
- A compatible EventMesh runtime for network operations

## Features

The default feature set is empty. Enable the transport(s) your application uses; `full` is primarily convenient for local verification.

| Feature | Provides |
| --- | --- |
| `grpc` | `GrpcClient`, producer, stream consumer, and webhook registration |
| `http` | `HttpClient`, managed HTTP consumer, external webhook registration, and webhook codec helpers |
| `tcp` | `TcpClient`, connected producer/consumer, broadcast, and reconnect |
| `cloud_events` | `Message::CloudEvent(cloudevents::Event)` support |
| `full` | All transports and CloudEvents support |
| `e2e` | Live-runtime integration tests; implies all runtime features |

```toml
[dependencies]
eventmesh = { version = "2", features = ["grpc"] }
```

## Quick start

The same `Message`, `EventMeshMessage`, `Subscription`, and role options are used by every transport. Only client construction changes.

```rust
use eventmesh::{
    config::{Endpoint, GrpcConfig, ProducerOptions},
    EventMeshMessage, GrpcClient, Message,
};

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let client = GrpcClient::new(GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?))?;
    let producer = client.producer(ProducerOptions::new("orders-producer"))?;
    let receipt = producer
        .publish(Message::from(EventMeshMessage::new("orders.created", r#"{"id": 42}"#)))
        .await?;
    println!("accepted with code {}", receipt.code);
    Ok(())
}
```

For a consumer, implement `MessageHandler`. Return `Ok(None)` to acknowledge an asynchronous delivery, `Ok(Some(reply))` for request/reply, and `Err(_)` to report application failure to the transport.

```rust,ignore
struct Log;

impl eventmesh::MessageHandler for Log {
    async fn handle(&self, message: eventmesh::Message) -> eventmesh::Result<Option<eventmesh::Message>> {
        println!("received: {message:?}");
        Ok(None)
    }
}
```

See the runnable transport-specific consumer programs in [examples/README.md](examples/README.md).

## Transport guide

| Transport | Client | Consumer model | Notable operations |
| --- | --- | --- | --- |
| gRPC | `GrpcClient` | `stream_consumer` invokes a `MessageHandler` | batch publish, request/reply, live subscribe/unsubscribe |
| HTTP | `HttpClient` | `consumer` binds and runs an axum callback server; `webhook_registration` supports application-owned endpoints | publish, weighted endpoint selection |
| TCP | `TcpClient` | connected `consumer` invokes a `MessageHandler` | broadcast, request/reply, automatic reconnect |

`HttpClient::consumer` binds its callback socket before registering subscriptions, then owns the axum server, heartbeat, and registration lifecycle. For an application-owned endpoint, use `HttpClient::webhook_registration` with `eventmesh::http::codec::{parse_push_body, WebhookReply}`. TCP unsubscribe is session-wide, so its API is `unsubscribe_all()`.

`HttpProducer::request_reply` encodes EventMesh's HTTP synchronous-publish request, but the current stock Runtime cannot route an HTTP-originated synchronous message through a gRPC stream consumer and return its reply to the HTTP request. The corresponding real-Runtime e2e is retained but ignored to make this compatibility gap visible. Use gRPC or TCP for request/reply unless the target deployment provides a compatible HTTP synchronous-reply path.

`Message` is a public dialect envelope, not a wire format. The selected transport owns protobuf, HTTP form, or TCP frame serialization. With `cloud_events`, CloudEvents remain CloudEvents; `Message::into_event_mesh()` does not silently flatten them into the native EventMesh model.

`EventMeshMessage` is likewise a business model rather than a stable serde JSON contract. gRPC, HTTP, and TCP convert it into private transport-specific wire DTOs. Native messages require non-blank topics and content; an explicit `ttl` must be a positive millisecond value no greater than `i32::MAX`. EventMesh does not define a never-expire TTL sentinel.

## Configuration and errors

All configurations require a validated `Endpoint`; HTTP uses a non-empty `EndpointSet`. Use `with_*` methods to set optional identity, credentials, timeouts, HTTP TLS, proxy, and reconnect settings. EventMesh Runtime's gRPC endpoint is plaintext and the gRPC client intentionally does not expose TLS configuration. `Debug` output redacts secrets.

Default request timeouts are 5 seconds (gRPC), 15 seconds (HTTP), and 20 seconds (TCP). `ClientOptions::with_request_timeout` changes a client's default; every producer also has `request_reply_with_timeout` for one call. TCP separately has a 1-second connect timeout and a 20-second control timeout.

Operations return the pattern-matchable `eventmesh::Error`; common variants include `Config`, `InvalidArgument`, `InvalidMessage`, `Timeout`, `Server`, `Protocol`, `Unsupported`, and transport-specific errors.

## API documentation

Generate and open the API documentation for every supported feature:

```bash
cargo doc --features full --no-deps --open
```

The crate root documents the public API map. Module-level rustdoc documents configuration, message models, subscriptions, and each transport. Keep these comments current when changing public behavior; see [CONTRIBUTING.md](CONTRIBUTING.md).

## Development

The authoritative contributor workflow and live-runtime test instructions are in [CONTRIBUTING.md](CONTRIBUTING.md). In short:

```bash
cargo fmt --check
cargo clippy --no-default-features --lib -- -D warnings
cargo clippy --features full --all-targets -- -D warnings
cargo test --features full
cargo doc --features full --no-deps
```

## License

Apache License 2.0.
