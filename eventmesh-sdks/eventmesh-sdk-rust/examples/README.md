# Rust SDK examples

Each example is a small executable with one responsibility. They use the default EventMesh ports and the topic `test-topic-rust-sdk`; start a compatible runtime before running them.

| Transport | Example | What it demonstrates | Command |
| --- | --- | --- | --- |
| gRPC | `grpc_producer` | Publish a native EventMesh message | `cargo run --example grpc_producer --features grpc` |
| gRPC | `grpc_consumer` | Stream consumption with `MessageHandler` | `cargo run --example grpc_consumer --features grpc` |
| gRPC | `grpc_webhook_consumer` | Register an application-owned webhook and explicitly unregister it on exit | `cargo run --example grpc_webhook_consumer --features grpc` |
| gRPC | `grpc_producer_cloud_events` | Publish a CloudEvent | `cargo run --example grpc_producer_cloud_events --features grpc,cloud_events` |
| gRPC | `grpc_batch` | Publish several native messages with one batch RPC | `cargo run --example grpc_batch --features grpc` |
| gRPC | `grpc_request_reply` | Run a synchronous subscriber and complete request/reply | `cargo run --example grpc_request_reply --features grpc` |
| HTTP | `http_producer` | Publish over HTTP | `cargo run --example http_producer --features http` |
| HTTP | `http_producer_cloud_events` | Publish a CloudEvent over HTTP | `cargo run --example http_producer_cloud_events --features http,cloud_events` |
| HTTP | `http_consumer_server` | SDK-managed axum callback server | `cargo run --example http_consumer_server --features http` |
| HTTP | `http_consumer_custom` | Application-owned axum webhook endpoint | `cargo run --example http_consumer_custom --features http` |
| TCP | `tcp_producer` | Connected TCP publish | `cargo run --example tcp_producer --features tcp` |
| TCP | `tcp_consumer` | Connected TCP subscribe | `cargo run --example tcp_consumer --features tcp` |
| TCP | `tcp_producer_cloud_events` | Publish a CloudEvent over TCP | `cargo run --example tcp_producer_cloud_events --features tcp,cloud_events` |
| TCP | `tcp_broadcast` | Send a fire-and-forget broadcast | `cargo run --example tcp_broadcast --features tcp` |
| TCP | `tcp_request_reply` | Run a synchronous subscriber and complete request/reply | `cargo run --example tcp_request_reply --features tcp` |

Run a consumer first, then run its corresponding producer. The HTTP examples listen on ports 8080 (built-in server) and 8081 (custom endpoint); change the advertised callback URL when EventMesh cannot reach `127.0.0.1`.

The examples intentionally use minimal configuration. For timeouts, identity, credentials, HTTP TLS, endpoint weights, and TCP reconnect tuning, consult the public rustdoc with `cargo doc --features full --no-deps --open`.

The two HTTP consumer examples handle Ctrl-C and call `close().await`, which unregisters their remote subscriptions before stopping local background work. Use the same shutdown pattern in long-running applications.

The gRPC webhook consumer has no automatic remote cleanup. Its example retains the subscription and URL, calls `unsubscribe().await` after Ctrl-C, and then stops and joins the local heartbeat task.
