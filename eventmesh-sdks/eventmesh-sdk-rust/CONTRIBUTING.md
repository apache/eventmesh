# Contributing to the EventMesh Rust SDK

This guide covers `eventmesh-sdks/eventmesh-sdk-rust`. Repository-wide Apache EventMesh contribution requirements still apply.

## Prerequisites

- Rust 1.86.0 or newer (the crate MSRV)
- `protoc` on `PATH` for builds that enable `grpc`, `full`, or `e2e`
- Docker and a compatible EventMesh runtime only for live end-to-end tests

`build.rs` invokes `tonic-build`; generated protobuf code lives in `OUT_DIR` and must not be edited or committed.

## Local checks

Run these before submitting a Rust SDK change:

```bash
cargo fmt --check
cargo clippy --no-default-features --lib -- -D warnings
cargo clippy --features full --all-targets -- -D warnings
cargo test --features full
cargo doc --features full --no-deps
```

Use `cargo test --features full --test codec_test` for the codec test binary. Examples are feature-gated in `Cargo.toml`; compile the one you changed with its documented `cargo run --example ... --features ...` command, or compile all supported paths with `cargo check --examples --features full`.

## End-to-end tests

The e2e suite is opt-in so a normal `cargo test` never requires Docker:

```bash
cargo test --features e2e
```

The harness starts the `rocketmq` docker-compose profile unless `EVENTMESH_E2E_EXTERNAL=1` points it at an already running runtime. An absent runtime is a failure by default. `EVENTMESH_E2E_ALLOW_SKIP=1` is only for an intentional local skip and must not be used for release verification.

The destructive TCP reconnect test is ignored by default because it restarts the compose-managed runtime:

```bash
cargo test --features e2e --test e2e \
  tcp_reconnect_replays_subscription_after_runtime_restart -- --ignored
```

## Documentation responsibilities

Keep each document in its intended layer.

| Change | Update |
| --- | --- |
| Installation, feature choice, or common behavior | `README.md` |
| Public type, method, feature-gated API, or behavior | rustdoc in `src/` |
| Runnable workflow or transport use | the matching file in `examples/` and `examples/README.md` |
| Validation, e2e, or contributor workflow | this file and `AGENTS.md` when agent guidance changes |

Public rustdoc should state feature requirements, ownership/lifecycle rules, and error or acknowledgement behavior where relevant. Prefer an executable doctest when it has no runtime dependency; otherwise mark the snippet `rust,ignore` and point users to a runnable example.

## Code conventions

- Add the Apache license header to every new `.rs` file.
- Keep `#![deny(unsafe_code)]` intact; unsafe Rust is not permitted.
- Mirror the established consuming builder style for configuration additions.
- Keep transport wire formats behind the public v2 API. Do not expose private legacy adapters merely to reuse an implementation detail.
- `Publisher` uses async functions in traits and is not object-safe; use concrete protocol producers instead of `dyn Publisher`.

See [AGENTS.md](AGENTS.md) for architecture details and precise e2e behavior.
