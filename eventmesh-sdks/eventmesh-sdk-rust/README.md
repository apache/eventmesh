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
| `grpc` | `GrpcChannel`, producer, stream consumer, and webhook registration |
| `http` | `HttpClient`, managed HTTP consumer, external webhook registration, and webhook codec helpers |
| `tcp` | `TcpClient`, connected producer/consumer, broadcast, and reconnect |
| `cloud_events` | `Message::CloudEvent(cloudevents::Event)` support |
| `full` | All transports and CloudEvents support |
| `e2e` | Live-runtime integration tests; implies all runtime features |
| `interop_e2e` | Bidirectional Rust/Java gRPC, HTTP, and TCP tests against Java SDK 1.12.0; implies `e2e` |

```toml
[dependencies]
eventmesh = { version = "2", features = ["grpc"] }
```

## Quick start

The same `Message`, `EventMeshMessage`, `Subscription`, and role options are used by every transport. gRPC connects an explicit channel and passes it to each role; HTTP and TCP use their transport clients as role factories.

```rust
use eventmesh::{
    config::{Endpoint, GrpcConfig, ProducerOptions},
    EventMeshMessage, GrpcChannel, GrpcProducer, Message,
};

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let channel =
        GrpcChannel::connect(GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?)).await?;
    let producer = GrpcProducer::new(channel, ProducerOptions::new("orders-producer"))?;
    let receipt = producer
        .publish(Message::from(EventMeshMessage::new(
            "orders.created",
            r#"{"id": 42}"#,
        )?))
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
| gRPC | `GrpcChannel` | `GrpcStreamConsumer` invokes a `MessageHandler` | batch publish, request/reply, live subscribe/unsubscribe |
| HTTP | `HttpClient` | `consumer` binds and runs an axum callback server; `webhook_registration` supports application-owned endpoints | publish, weighted endpoint selection |
| TCP | `TcpClient` | connected `consumer` invokes a `MessageHandler` | broadcast, request/reply, automatic reconnect |

`HttpClient::consumer` binds its callback socket before registering subscriptions, then owns the axum server, heartbeat, and registration lifecycle. For an application-owned endpoint, use `HttpClient::webhook_registration` with `eventmesh::http::codec::{parse_push_body, WebhookReply}`. TCP unsubscribe is session-wide, so its API is `unsubscribe_all()`.

All consumers use the same local lifecycle contract: `shutdown()` only signals background work to stop, while `join().await` waits for it and reports task or transport failures. HTTP consumers and webhook registrations additionally provide `close().await`, which unregisters remote subscriptions before signalling shutdown and joining.

Create each `GrpcChannel` inside the Tokio runtime that will drive it. Clone that
channel to share one multiplexed HTTP/2 connection among producers and consumers
in the same runtime. If an application uses another Tokio runtime, call
`GrpcChannel::connect` again from that runtime instead of carrying over an
existing channel.

`GrpcWebhookConsumer` does not automatically unregister remote webhook subscriptions when `shutdown()` or `join()` is called. Retain the subscriptions and webhook URL, call `unsubscribe(...).await` explicitly, and only then call `shutdown()` and `join().await`. See the `grpc_webhook_consumer` example.

HTTP request/reply is not exposed because the current SDK and stock Runtime do not provide a complete HTTP responder path. Use gRPC or TCP for request/reply.

`Message` is a public dialect envelope, not a wire format. The selected transport owns protobuf, HTTP form, or TCP frame serialization. With `cloud_events`, CloudEvents remain CloudEvents; `Message::into_event_mesh()` does not silently flatten them into the native EventMesh model.

`EventMeshMessage` is likewise a business model rather than a stable serde JSON contract. gRPC, HTTP, and TCP convert it into private transport-specific wire DTOs. A topic must be non-blank and content must be present, but empty content is accepted for Java SDK interoperability. Inbound TTL metadata is preserved as received; each transport applies its own outbound content and TTL limits when publishing.

## Configuration and errors

All configurations require a validated `Endpoint`; HTTP uses a non-empty `EndpointSet`. Use `with_*` methods to set optional identity, credentials, timeouts, HTTP TLS, proxy, and reconnect settings. EventMesh Runtime's gRPC endpoint is plaintext and the gRPC client intentionally does not expose TLS configuration. `Debug` output redacts secrets.

Default request timeouts are 5 seconds (gRPC), 15 seconds (HTTP), and 20 seconds (TCP). `ClientOptions::with_request_timeout` changes a client's default; gRPC and TCP producers also have `request_reply_with_timeout` for one call. TCP separately has a 1-second connect timeout and a 20-second control timeout.

Operations return the pattern-matchable `eventmesh::Error`; common variants include `Config`, `InvalidArgument`, `InvalidMessage`, `Timeout`, `Server`, `Protocol`, `Unsupported`, and transport-specific errors.

## API documentation

Generate and open the API documentation for every supported feature:

```bash
cargo doc --features full --no-deps --open
```

The crate root documents the public API map. Module-level rustdoc documents configuration, message models, subscriptions, and each transport. Keep these comments current when changing public behavior; see [CONTRIBUTING.md](CONTRIBUTING.md).

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for prerequisites, required checks, and live-runtime tests. Implementation boundaries and protocol details are recorded in [ARCHITECTURE.md](ARCHITECTURE.md).

## License

Apache License 2.0.
