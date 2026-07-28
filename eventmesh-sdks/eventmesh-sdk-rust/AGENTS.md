# AGENTS.md — EventMesh Rust SDK

Cargo crate `eventmesh` (`edition = "2021"`, **MSRV 1.86.0**). Speaks the
EventMesh **gRPC**, **HTTP**, and **TCP** protocols. gRPC wire format is
CloudEvents-protobuf; the simple `EventMeshMessage` model is converted at the
gRPC boundary by `codec.rs`. HTTP wire format is
`application/x-www-form-urlencoded` with JSON payloads in the `content` field,
mirroring the Java SDK. TCP uses the EventMesh binary wire protocol
(length-prefixed frames with `"EventMesh"` magic) and supports automatic
reconnect with exponential backoff plus CloudEvents JSON interop.

This crate is **not** part of the Gradle build and has **no GitHub Actions
CI**. All verification is local. The parent repo `AGENTS.md` covers the
docker-compose profiles and runtime ports; this file covers the Rust workflow.

## Build prerequisite: `protoc`

`build.rs` invokes `tonic-build`, which shells out to the `protoc` compiler at
**build time**. `prost-build` finds `protoc` automatically when it is on
`PATH`; only set the `PROTOC` env var to point at a binary that is **not** on
`PATH`. (`brew install protobuf`, `apt-get install protobuf-compiler`, etc. all
put `protoc` on `PATH`, so no prefix is needed.)

```bash
cargo build --features full
```

## Feature matrix

| Feature | Notes |
|---|---|
| `grpc` | gRPC transport — publish, batch, request-reply, stream/webhook subscribe. |
| `http` | HTTP transport — `HttpProducer`, managed `HttpConsumer`, external `WebhookRegistration`, and webhook codec helpers. Uses reqwest + axum. |
| `tcp` | TCP transport — `TcpProducer`, `TcpConsumer`, native binary wire protocol. Auto-reconnect with exponential backoff. CloudEvents interop behind `cloud_events`. |
| `cloud_events` | Native `cloudevents::Event` interop (gRPC, HTTP, and TCP). |
| `full` | `grpc` + `http` + `tcp` + `cloud_events`. Use this for clippy/test so every code path compiles. |
| `e2e` | Gates the live-server integration suite (`tests/e2e/`). A plain `cargo test` never touches Docker. |

## Verification (the order the README mandates)

```bash
cargo fmt --check
cargo clippy --no-default-features --lib -- -D warnings
cargo clippy --features full --all-targets -- -D warnings
cargo test --features full
cargo doc --features full --no-deps
```

Clippy runs with **`-D warnings`** (warnings are errors here). There is no
`rustfmt.toml`/`clippy.toml` — defaults apply. To run a single test binary:
`cargo test --features full --test codec_test`.

## Documentation ownership

- `README.md` is the user-facing overview: installation, feature selection,
  shared behavior, and links to the right entry point.
- Crate and public-item rustdoc in `src/` is the API reference. Update it with
  every public API or observable behavior change; include feature and lifecycle
  requirements where relevant.
- `examples/README.md` classifies runnable examples and their exact feature
  flags. Keep it aligned with `[[example]]` entries in `Cargo.toml`.
- `CONTRIBUTING.md` owns contributor prerequisites and verification; this file
  owns agent-facing architecture and repository workflow.

## Generated proto code (do not hand-edit)

`build.rs` compiles `proto/eventmesh-{service,cloudevents}.proto` into `OUT_DIR`
at build time, generating **client stubs only** (`build_server(false)`), with
`--experimental_allow_proto3_optional`. The generated tree is pulled in via
`src/proto_gen.rs` → `tonic::include_proto!("...")`; it is **not** checked in.
Add convenience aliases in `proto_gen.rs`, not in the generated module.

## Architecture notes

- `src/lib.rs` is `#![deny(unsafe_code)]` — no `unsafe` anywhere.
- `src/transport/mod.rs` defines `Publisher` as an **async-fn-in-trait**
  (Rust 1.86). It is therefore **not object-safe** — use the concrete
  `GrpcProducer` / `TcpProducer` / `HttpProducer` directly, never `dyn`.
  The subscribe side has **no trait** — each transport exposes its own
  consumer type (`GrpcStreamConsumer`, `GrpcWebhookConsumer`, `TcpConsumer`,
  `HttpConsumer`) with transport-specific `subscribe` / `unsubscribe` /
  `subscribe_webhook` methods, a background receive loop (where applicable),
  and `wait_for_shutdown()` for clean exit.
- `src/transport/grpc/codec.rs` is the `EventMeshMessage` ↔ CloudEvents-protobuf
  bridge for the gRPC transport.
- `src/transport/http/codec.rs` is the `EventMeshMessage` ↔ form-urlencoded +
  JSON bridge for the HTTP transport. The wire format is
  `application/x-www-form-urlencoded` (not JSON bodies); payloads go in the
  `content` form field as JSON strings.
- `src/config/grpc.rs` — `GrpcClientConfig` + fluent builder.
- `src/config/http.rs` — `HttpClientConfig` + fluent builder. Accepts
  semicolon/comma-separated `host:port[:weight]` server lists; uses the shared
  `LoadBalanceSelector` from `common/loadbalance.rs`.
- `src/transport/http/webhook.rs` — **internal** axum handler (`WebhookHandler`
  + `WebhookState`) used only by `WebhookServer`. Not part of the public API.
- `src/transport/http/server.rs` — built-in `WebhookServer` (axum). Managed
  consumers bind it before registration and run it in a background task.
- `src/transport/tcp/connection.rs` — TCP engine: `establish()` does TCP +
  HELLO handshake; `run()` is an outer reconnect loop that calls `io_loop()`
  (the inner select! over read/write/heartbeat). When `ReconnectConfig::enabled`
  (default `true`), the outer loop re-establishes the connection with
  exponential backoff after I/O errors. `take_reconnect_rx()` delivers a
  notification on each successful reconnect so consumers can replay
  subscriptions.
- `src/transport/tcp/message.rs` — package builders for `EventMeshMessage`
  (`build_message_package`) and CloudEvents (`build_cloud_event_package`,
  behind `cloud_events`). CloudEvents bodies use `protocoltype=cloudevents` and
  are serialized as `application/cloudevents+json` raw bytes (matching the Java
  runtime's codec path).
- `src/config/tcp.rs` — `TcpClientConfig` + `ReconnectConfig` + fluent builders.
- Public `TcpConfig` keeps Java-compatible timeout classes separate: TCP
  connect (1s default), protocol control (20s), business request/response
  (20s), heartbeat interval (30s), and reconnect backoff. Rust heartbeats and
  GOODBYE remain fire-and-forget.
- `src/common/` — `ProtocolKey`, status codes, constants, `LoadBalanceSelector`
  shared across transports.

### HTTP transport specifics

- The public HTTP consumer binds and runs an axum callback server in the
  background, then owns registration, heartbeat, and shutdown as one lifecycle.
  The lower-level `WebhookRegistration` is client-only (like the Java SDK) for
  applications that host their own endpoint. Two receiving modes remain:
  1. **Managed consumer** — `HttpClient::consumer` binds before registration,
     preventing connection-refused startup races.
  2. **Custom endpoint** — the user hosts any HTTP server (axum, actix, hyper,
     …) and decodes pushes with the **public codec helpers** in
     `src/transport/http/codec.rs`: `parse_push_body`,
     `PushMessageRequestBody::to_event_mesh_message`, and `WebhookReply`
     (the `{"retCode": 0}` ack). See the `http_consumer_custom` example.
- There is intentionally **no public `WebhookHandler`/`WebhookLayer`/`WebhookState`**
  type. The old "register the SDK's handler on your own Router" mode was
  removed; users who embed the webhook in their own app write the handler
  themselves on top of the codec utilities.
- Runtime routing: the EventMesh HTTP server has two routing mechanisms —
  path-based (new-style handlers, checked first by URI prefix match) and
  code-header-based (old-style, checked by the `code` header when no path
  matches). Because this SDK sends `application/x-www-form-urlencoded` bodies,
  **all** operations (publish, subscribe, unsubscribe, heartbeat) use
  code-header-based routing via the root path `/` (`uri::ROOT`). Posting to a
  path-based handler (e.g. `/eventmesh/subscribe/local`) with a form body breaks
  body decoding: the `topic` form field is parsed as a string and cannot be
  deserialized as `List<SubscriptionItem>`.
- Heartbeat interval: 30s (mirrors the Java SDK), spawned as a background
  tokio task tied to a `CancellationToken`.

## End-to-end tests (`tests/e2e/`)

Run with: `cargo test --features e2e`.

- The harness (`tests/e2e/runtime.rs`) **auto-starts the `rocketmq` docker-compose
  profile** and tears it down at process exit. Set `EVENTMESH_E2E_EXTERNAL=1` to
  point at a server you started yourself.
- If neither Docker nor a reachable server is found, tests **fail by default**.
  Set `EVENTMESH_E2E_ALLOW_SKIP=1` only for an intentional local skip; release
  verification must not set it.
- Each test generates a unique topic + consumer group (monotonic counter + nanos
  timestamp). gRPC and HTTP cases may run in parallel; TCP cases take a shared
  async test lock and run serially because the runtime's route refresh and
  RocketMQ rebalance cycles are shared even when topics are distinct.
- **Standalone (in-memory) broker limitations:** a topic must be created via the
  admin API **and** a consumer subscribed *before* any publish, and it does not
  implement request/reply. The harness warms topics automatically. Because the
  suite strictly verifies every advertised operation, use a runtime profile that
  supports request/reply when running the complete release suite.
- Topic creation hits the admin API at `POST /topic`, which expects
  **`application/x-www-form-urlencoded`** (not JSON).

## Conventions

- **Apache license header is required on every `.rs` file** — copy the header
  block from a neighbor (e.g. `build.rs`). Consistent with the rest of the repo.
- Builders use the `Option<T> field + fluent setter + consuming build()` idiom
  (see `GrpcClientConfigBuilder`); mirror it for new config types.
