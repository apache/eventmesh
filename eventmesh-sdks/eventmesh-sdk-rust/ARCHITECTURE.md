# EventMesh Rust SDK architecture

This document records implementation constraints and protocol boundaries. For public usage, see [README.md](README.md); for build and test commands, see [CONTRIBUTING.md](CONTRIBUTING.md).

## Public API boundaries

- `src/lib.rs` denies unsafe code.
- `src/transport/mod.rs` defines `Publisher` with async functions in the trait. It is not object-safe; use `GrpcProducer`, `HttpProducer`, or `TcpProducer` directly rather than `dyn Publisher`.
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

TCP CloudEvents use `protocoltype=cloudevents` and raw `application/cloudevents+json` bytes, matching the Java runtime codec path.

## Configuration

- gRPC transport code consumes `GrpcConfig` and the matching producer or
  consumer role options directly; it has no transport-private configuration
  adapter.
- `src/config/http.rs` defines `HttpClientConfig` and its builder. Server lists accept comma- or semicolon-separated `host:port[:weight]` entries and use the shared load balancer.
- `src/config/tcp.rs` defines `TcpClientConfig`, `ReconnectConfig`, and their builders. TCP keeps connect, protocol-control, business request, heartbeat, and reconnect timeouts separate for Java compatibility. Heartbeats and GOODBYE are fire-and-forget.

## HTTP lifecycle and routing

The managed `HttpConsumer` binds its axum callback server before registration, then owns registration, heartbeat, and shutdown. Applications that host their own endpoint use `WebhookRegistration` and the public codec helpers `parse_push_body`, `PushMessageRequestBody::to_event_mesh_message`, and `WebhookReply`. `WebhookHandler` and `WebhookState` in `src/transport/http/webhook.rs` are internal implementation details.

All SDK HTTP operations use code-header routing at `/`. The bodies are `application/x-www-form-urlencoded`, so sending them to a Runtime path-based handler can select an incompatible JSON model. The heartbeat runs every 30 seconds in a background Tokio task tied to a `CancellationToken`.

## TCP connection lifecycle

In `src/transport/tcp/connection.rs`, `establish()` performs the socket and HELLO handshake. `run()` wraps `io_loop()` in the reconnect loop. With reconnect enabled, I/O failures trigger exponential backoff and re-establishment. `take_reconnect_rx()` notifies consumers after successful reconnects so they can replay subscriptions.
