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
| gRPC | `GrpcChannel` | `GrpcStreamConsumer` invokes a `MessageHandler` | batch publish, request/reply, stream subscriptions with the limitations below |
| HTTP | `HttpClient` | `consumer` binds and runs an axum callback server; `webhook_registration` supports application-owned endpoints | publish, weighted endpoint selection |
| TCP | `TcpClient` | connected `consumer` invokes a `MessageHandler` | broadcast, request/reply, automatic reconnect |

`HttpClient::consumer` binds its callback socket before registering subscriptions, then owns the axum server, heartbeat, and registration lifecycle. For an application-owned endpoint, use `HttpClient::webhook_registration` with `eventmesh::http::codec::{parse_push_body, WebhookReply}`. Decode each delivery with `parse_push_body(body)?.to_message(&headers)?`, passing its `http::HeaderMap`; this uses the same dialect detection as the built-in server and preserves CloudEvents when `cloud_events` is enabled. TCP unsubscribe is session-wide, so its API is `unsubscribe_all()`.

Cancelling `HttpClient::consumer` during startup stops its local callback server and heartbeat task. A subscription already accepted by the Runtime is not rolled back by cancellation.

TCP `broadcast().await` waits for the local socket write before returning, so a subsequent `shutdown().await` does not discard the broadcast from the SDK queue. It does not wait for a Runtime acknowledgement or guarantee delivery. Queueing and writing share the TCP control timeout.

TCP consumers isolate unwinding handler panics to one delivery: they log the panic, send no reply or ACK for that delivery, and continue processing subsequent messages on the same connection. Redelivery depends on the Runtime's retry policy. The SDK does not restore handler-owned state after a panic, and `panic = "abort"` cannot be caught.

All consumers use the same local lifecycle contract: `shutdown()` only signals background work to stop, while `join().await` waits for it and reports task or transport failures. Cancelling a `join()` wait preserves task ownership and pending failures: call `join()` again to finish waiting, or drop the consumer to abort its tasks. HTTP consumers and webhook registrations additionally provide `close().await`, which unregisters remote subscriptions before signalling shutdown and joining.

Create each `GrpcChannel` inside the Tokio runtime that will drive it. Clone that
channel to share one multiplexed HTTP/2 connection among producers and consumers
in the same runtime. If an application uses another Tokio runtime, call
`GrpcChannel::connect` again from that runtime instead of carrying over an
existing channel. Both current-thread and multi-thread Tokio runtimes are supported;
keep the owning runtime running to drive the channel and consumer tasks. Opening
a subscription stream waits up to 15 seconds for response headers; this timeout
does not limit the lifetime of an established stream.

Known gRPC stream subscription limitations (retained in this SDK revision):

- **Subscription rejection is not reported reliably.** `GrpcStreamConsumer::open()` establishes the stream without waiting for a successful subscription acknowledgement, and `subscribe()` queues the request without waiting for acceptance. The receive loop ignores control frames without `seqnum`, including Runtime ACL and validation errors carried in `statuscode` / `responsemessage`. If the Runtime then closes the stream normally, `join()` can return `Ok(())`. These successful returns do not prove that the subscription was accepted; check Runtime logs when diagnosing missing deliveries. The repository's Java SDK stream consumer also does not propagate these rejection statuses to the caller.
- **Unsubscribing one topic can stop every topic on the stream.** With A and B on one stream, `unsubscribe(A)` removes A locally, but the Java Runtime closes their shared emitter. The Rust receive loop and heartbeat stop, B stops receiving, and subsequent `subscribe()` calls fail with `Error::ChannelClosed` once stream teardown is observed. There is no automatic stream recreation or replay of B. Treat unsubscribe as ending the current stream: wait for it to finish, drop the consumer, and explicitly open a new consumer with the desired remaining subscriptions. Delivery is interrupted during this transition. The repository's Java SDK also does not recreate the closed stream; its heartbeat may continue despite the loss of delivery.

These are documented limitations, not fixes. See [ARCHITECTURE.md](ARCHITECTURE.md#grpc-stream-subscription-limitations) for the Runtime and Java SDK paths behind them. They concern stream subscriptions; gRPC webhook registration uses unary responses.

`GrpcWebhookConsumer` does not automatically unregister remote webhook subscriptions when `shutdown()` or `join()` is called. Retain the subscriptions and webhook URL, call `unsubscribe(...).await` explicitly, and only then call `shutdown()` and `join().await`. See the `grpc_webhook_consumer` example.

HTTP request/reply is not exposed because the current SDK and stock Runtime do not provide a complete HTTP responder path. Use gRPC or TCP for request/reply.

`Message` is a public dialect envelope, not a wire format. The selected transport owns protobuf, HTTP form, or TCP frame serialization. With `cloud_events`, CloudEvents remain CloudEvents; `Message::into_event_mesh()` does not silently flatten them into the native EventMesh model.

`EventMeshMessage` is a business model rather than a stable serde JSON contract. Topic, content, message IDs, TTL, and payload content type have dedicated fields. Set TTL with `EventMeshMessageBuilder::ttl_millis` and content type with `data_content_type`; read them through the matching message accessors. Native decoders preserve numeric TTL until outbound validation and reject malformed or out-of-i64-range TTL. CloudEvents retain their standard attributes and extensions.

`properties()` and `get_prop()` expose only business extensions. Received protocol descriptors, identity, and known routing attributes live in the separate, read-only `DeliveryContext` returned by `message.delivery_context()`. It is `None` on locally built messages. Use `context.protocol_description()` for the source protocol and `context.attribute("cluster")` for received routing metadata. Producers ignore this context and rebuild transport metadata from the destination client. Consumer reply paths automatically restore the original request's routing; ACKs continue to use the received transport frame.

Reserved names cannot be injected through business properties. `builder.prop(...)` and `builder.props(...)` report `Error::InvalidArgument` at `build()` for reserved keys; `set_prop` and `with_property` now return `Result` and reject them immediately. For example:

```rust
use eventmesh::EventMeshMessage;

let mut message = EventMeshMessage::builder()
    .topic("orders")
    .content(r#"{"id":42}"#)
    .ttl_millis(7_000)
    .data_content_type("application/json")
    .prop("tenant", "store-a")
    .build()?;
message.set_prop("tenant", "store-b")?;
assert!(message.set_prop("protocoldesc", "tcp").is_err());
# Ok::<(), eventmesh::Error>(())
```

Migration: replace `get_prop("ttl")`, `get_prop("seqnum")`, `get_prop("uniqueid")`, and `get_prop("datacontenttype")` with the dedicated accessors. Read protocol/routing attributes from `delivery_context()`; do not copy them into a reply's properties. Add `?` to business-property `set_prop`/`with_property` calls. The `MessageHandler` signature is unchanged.

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
