# eventmesh-rust-sdk

The Rust SDK for [Apache EventMesh](https://eventmesh.apache.org). Version 2
uses explicit protocol clients, Rust-style configuration values, and one
non-generic `Message` enum that preserves EventMesh and optional CloudEvents
messages.

## Requirements

- Rust 1.86 or newer
- `protoc` 3.15 or newer when building with the `grpc` feature
- A compatible EventMesh runtime for network operations

## Features

Transports are opt-in; the default feature set is empty.

| Feature | Capability |
| --- | --- |
| `grpc` | `GrpcClient`, producer and stream consumer, Catalog and Workflow |
| `http` | `HttpClient`, webhook registration, and `WebhookServer` |
| `tcp` | `TcpClient`, producer, consumer, broadcast, and reconnect |
| `cloud_events` | `Message::CloudEvent(cloudevents::Event)` |
| `tls` | TLS support for gRPC |
| `full` | All supported transports, CloudEvents, and TLS |

```toml
[dependencies]
eventmesh = { version = "2", features = ["grpc"] }
```

## Messages

`Message` is intentionally not generic and is not an SDK serialization
format. It only selects the public message dialect; the chosen transport owns
protobuf, HTTP-form, or TCP-frame serialization.

```rust
use eventmesh::message::{EventMeshMessage, Message};

let native = Message::from(EventMeshMessage::new("orders.created", "{\"id\": 42}"));

// With `cloud_events` enabled:
// let cloud_event = Message::from(event);
```

`Message::into_event_mesh()` never silently flattens CloudEvents into the
native EventMesh model.

## gRPC

```rust
use eventmesh::{
    config::{Endpoint, GrpcConfig, ProducerOptions},
    message::{EventMeshMessage, Message},
    GrpcClient,
};

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let endpoint = Endpoint::new("127.0.0.1", 10_205)?;
    let client = GrpcClient::new(GrpcConfig::new(endpoint))?;
    let producer = client.producer(ProducerOptions::new("orders-producer"))?;
    let receipt = producer
        .publish(Message::from(EventMeshMessage::new("orders.created", "{\"id\": 42}")))
        .await?;
    println!("accepted with code {}", receipt.code);
    Ok(())
}
```

To receive messages, implement `MessageHandler`. `Ok(None)` acknowledges an
asynchronous message; `Ok(Some(reply))` sends a reply for a synchronous
delivery. A handler error is not acknowledged: HTTP asks for redelivery, while
gRPC and TCP close the current delivery stream/connection.

```rust
use eventmesh::{
    config::{Endpoint, GrpcConfig, GrpcConsumerOptions},
    message::Message,
    subscription::Subscription,
    GrpcClient, MessageHandler,
};

struct Log;

impl MessageHandler for Log {
    async fn handle(&self, message: Message) -> eventmesh::Result<Option<Message>> {
        println!("received: {message:?}");
        Ok(None)
    }
}

#[tokio::main]
async fn main() -> eventmesh::Result<()> {
    let client = GrpcClient::new(GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205)?))?;
    let consumer = client
        .stream_consumer(
            GrpcConsumerOptions::new("orders-consumer"),
            [Subscription::new("orders.created")],
            Log,
        )
        .await?;
    consumer.join().await
}
```

`GrpcConsumerOptions::with_max_concurrent_handlers` controls bounded concurrent
handler dispatch on a gRPC stream. `GrpcConsumer::subscribe` and `unsubscribe`
update a live stream. For gRPC
webhook registration, create `client.webhook_consumer(...)` and register one
or more subscriptions with a webhook URL. gRPC batch publishing accepts a
native EventMesh batch or a homogeneous CloudEvents batch; mixed
native/CloudEvents batches are rejected.

## HTTP and TCP

HTTP uses a long-lived webhook-registration consumer plus an optional built-in
webhook server. TCP has a connected producer/consumer and exposes broadcast as
a TCP-specific producer operation. `producer_with_handler` enables the TCP
publisher-side response handler equivalent to Java's `registerPubBusiHandler`.
TCP Runtime unsubscribe is session-wide, so the Rust API names it
`TcpConsumer::unsubscribe_all()` rather than accepting a misleading topic.
See `examples/http` and `examples/tcp` for runnable programs.

```rust
use eventmesh::{
    config::{ConsumerOptions, Endpoint, EndpointSet, HttpConfig},
    subscription::Subscription,
    webhook::WebhookServer,
    HttpClient,
};

// Build WebhookServer with a MessageHandler, then register server.url():
// let server = WebhookServer::new("0.0.0.0:8080".parse()?, handler);
let endpoints = EndpointSet::new([Endpoint::new("127.0.0.1", 10_105)?])?;
let client = HttpClient::new(HttpConfig::new(endpoints))?;
let consumer = client.webhook_consumer(ConsumerOptions::new("orders-http"))?;
// consumer.subscribe(Subscription::new("orders.created"), server.url()).await?;
// consumer.unsubscribe(Subscription::new("orders.created"), server.url()).await?;
# let _ = (consumer, Subscription::new("orders.created"));
```

## Configuration and errors

Every protocol configuration starts with a validated `Endpoint` (HTTP takes a
non-empty `EndpointSet`). Use `Identity`, `Credentials`, `ClientOptions`, and
the transport-specific builder-style `with_*` methods to set optional values.
Secrets are redacted in `Debug` output.

HTTP connects directly by default, matching the Java SDK. Enable process
environment proxy settings explicitly with `HttpConfig::with_proxy_from_env(true)`;
reqwest will then honor `HTTP_PROXY`, `HTTPS_PROXY`, and `NO_PROXY`.

The default request timeout is protocol-specific: 5 seconds for gRPC, 15
seconds for HTTP, and 20 seconds for TCP. `ClientOptions::with_request_timeout`
overrides that protocol default and rejects a zero duration when the client is
created. When one request needs a different deadline, each producer exposes
`request_reply_with_timeout(message, Duration)`, matching the Java SDK's
per-call timeout without changing the client's default.

TCP additionally separates its 1-second socket connection timeout from its
20-second protocol-control timeout. Configure them with
`TcpConfig::with_connect_timeout` and `TcpConfig::with_control_timeout`; the
latter applies to HELLO, LISTEN, subscribe, unsubscribe, and subscription
replay after reconnect. The heartbeat interval and reconnect backoff remain
independent settings. As in the existing Rust implementation, heartbeat and
GOODBYE frames are fire-and-forget rather than response-waiting Java-style
control calls.

HTTP endpoint weights must each be positive and no greater than `i32::MAX`;
their combined weight must also fit in `i32`. This rejects configurations that
would overflow the Java SDK's weighted load-balancing counters.

Operations return the public, pattern-matchable `eventmesh::Error`; relevant
variants include `Config`, `InvalidArgument`, `InvalidMessage`, `Timeout`,
`Server`, `Protocol`, `Unsupported`, and transport errors. There is no public
catch-all error variant.

## Catalog, Workflow, and discovery

`catalog`, `workflow`, and `discovery` remain public gRPC APIs. Catalog now
attaches to the public `GrpcConsumer<H>` returned by `GrpcClient`:

```rust,ignore
catalog.init(&consumer).await?;
// ...
catalog.destroy(&consumer).await?;
```

## Development

```bash
cargo fmt --check
cargo clippy --features full --all-targets -- -D warnings
cargo test --features full
cargo test --features e2e --no-run
```

The `e2e` feature compiles the live gRPC, HTTP, TCP, webhook, request/reply,
and CloudEvents integration suite. It also includes an explicitly ignored,
destructive TCP reconnect case that restarts the compose-managed runtime; run
it with `cargo test --features e2e --test e2e tcp_reconnect_replays_subscription_after_runtime_restart -- --ignored`.
TLS is self-contained: its e2e starts a real TLS gRPC service using a freshly
generated CA and server certificate, then verifies CA trust and SNI through
the SDK's Workflow client.
Running the suite against a live runtime is documented in `tests/e2e/main.rs`.
An unavailable runtime fails by default; set `EVENTMESH_E2E_ALLOW_SKIP=1` only
for an intentional local skip, never for release verification.

## License

Apache License 2.0.
