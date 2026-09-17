# EventMesh Rust SDK architecture

This document records implementation constraints and protocol boundaries. For public usage, see [README.md](README.md); for build and test commands, see [CONTRIBUTING.md](CONTRIBUTING.md).

## Public API boundaries

- `src/lib.rs` denies unsafe code.
- Consumers and webhook servers invoke the public `MessageHandler` directly with `Message`. Transport-private helpers decode and encode that envelope; there is no separate listener trait or handler adapter with an associated message type.
- Producers use concrete transport methods behind the public `GrpcProducer`, `HttpProducer`, and `TcpProducer` APIs. Message dialect selection remains in the public producer facade, while each transport owns its wire encoding and supported operations. There is no internal `Publisher` or `RequestReply` trait requiring unused or unsupported methods.
- Subscription is intentionally transport-specific. Each consumer owns its receive loop where applicable and exposes lifecycle methods suited to its protocol.
- `src/common/` contains protocol keys, status codes, constants, and the shared `LoadBalanceSelector`.

## Generated protobuf code

`build.rs` uses `tonic-build` to compile `proto/eventmesh-{service,cloudevents}.proto` into Cargo's `OUT_DIR`. It creates client stubs only and enables `--experimental_allow_proto3_optional`. The two `.proto` inputs and the hand-written `src/proto_gen.rs` wrapper are checked in. The generated Rust files remain in `OUT_DIR` and are loaded by `tonic::include_proto!`; under the current build setup, those generated files are not checked in. Add convenience aliases to `proto_gen.rs` rather than editing build output.

## Wire formats

`EventMeshMessage` is a business model, not a shared wire DTO. Each transport owns its serialization:

| Transport | Boundary | Encoding |
| --- | --- | --- |
| gRPC | `src/transport/grpc/codec.rs` | CloudEvents protobuf |
| HTTP | `src/transport/http/codec.rs` | Form URL encoding, with JSON in `content` |
| TCP | `src/transport/tcp/message.rs` | Length-prefixed binary frames with `EventMesh` magic |

Native messages separate business data from delivery context:

- `EventMeshMessage` owns topic, content, business/unique IDs, TTL, content type, and business properties.
- `DeliveryContext` in `src/model/delivery.rs` owns received protocol descriptors and known identity/routing attributes. Its public API is read-only; only SDK decoders can attach it. Credentials are redacted in Debug output.
- `decode_native_message` in `src/transport/mod.rs` separates wire attributes for every native decoder. HTTP form fields take precedence over duplicated IDs in `extFields`. Protocol-specific wire representations remain private.
- Property builders and setters reject reserved names using the shared classification in `src/model/delivery.rs`. Encoders retain the same guard as defense in depth and never serialize a delivery context during normal publish, broadcast, or a new request.
- TCP `RESPONSE_TO_SERVER` encoding restores known reply-routing attributes from the original request context, including Runtime `req0*`/`rsp0*`, cluster, and RocketMQ `correlation99id`/`reply99to99client`. The consumer attaches the original request context even if the handler returns a message received elsewhere. gRPC replies retain routing from their original protobuf request. ACK correlation continues to use the original wire frame.

TTL has one business source: its dedicated field. HTTP/gRPC retain their 4000 ms outbound default; TCP leaves an unset TTL to the Runtime. Content type is also a dedicated field, encoded as a gRPC attribute, TCP message header, or HTTP `extFields` entry. Decoders reject malformed or out-of-i64-range TTL while preserving numeric values until outbound validation. CloudEvents keep their standard attributes/extensions; native-to-CloudEvents reply conversion maps the dedicated fields and reply context explicitly.

TCP CloudEvents use `protocoltype=cloudevents` and raw `application/cloudevents+json` bytes, matching the Java runtime codec path.

## Configuration

- Every transport consumes the public configuration types directly:
  `GrpcConfig`, `HttpConfig`, and `TcpConfig`, together with the role
  options (`ProducerOptions`, `ConsumerOptions`) passed to each role
  factory. There are no transport-private configuration adapters.
- `GrpcChannel::connect` creates the tonic channel on the current Tokio
  runtime. Roles receive the channel explicitly and clones share its
  multiplexed HTTP/2 connection. Applications using multiple Tokio runtimes
  create a separate channel in each runtime.
- `HttpConfig` carries an `EndpointSet`; endpoint weights feed the shared
  load balancer and identity/credentials ride as HTTP headers.
- `TcpConfig` keeps connect, protocol-control, business request, heartbeat,
  and reconnect timeouts separate for Java compatibility. Heartbeats and
  GOODBYE are fire-and-forget.

## HTTP lifecycle and routing

The managed `HttpConsumer` binds its axum callback server before registration, then owns registration, heartbeat, and shutdown. Applications that host their own endpoint use `WebhookRegistration` and the public codec helpers `parse_push_body`, `PushMessageRequestBody::to_message`, and `WebhookReply`. `to_message` resolves the dialect from the HTTP headers and `extFields`; the built-in handler calls the same decoder. `WebhookHandler` and `WebhookState` in `src/transport/http/webhook.rs` are internal implementation details.

The consumer owns the spawned server and heartbeat before awaiting registration, so dropping the startup future runs the same local cleanup as dropping an active consumer.

All SDK HTTP operations use code-header routing at `/`. The bodies are `application/x-www-form-urlencoded`, so sending them to a Runtime path-based handler can select an incompatible JSON model. The heartbeat runs every 30 seconds in a background Tokio task tied to a `CancellationToken`.

## TCP connection lifecycle

The consumer invokes each handler inside an asynchronous `catch_unwind` boundary covering both future construction and polling. An unwinding handler panic is logged with the delivery sequence, skips reply/ACK for that delivery, and keeps the receive loop running. The boundary does not restore application state. Explicit handler errors and reply encoding/enqueue failures still close the connection without ACK.

In `src/transport/tcp/connection.rs`, `establish()` performs the socket and HELLO handshake. `run()` wraps `io_loop()` in the reconnect loop. With reconnect enabled, I/O failures trigger exponential backoff and re-establishment. `take_reconnect_rx()` notifies consumers after successful reconnects so they can replay subscriptions.

Broadcasts use a driver completion channel to await `Framed::send`, including its socket flush, without waiting for a server ACK. Queue reservation and completion share one control-timeout deadline. A cancelled broadcast still waiting in the outbound queue is skipped; a write already in progress may have reached the server.
