// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

//! End-to-end tests for the EventMesh Rust SDK.
//!
//! These tests spin up the EventMesh runtime via `docker compose` (rocketmq
//! profile) and exercise the **gRPC**, **HTTP** and **TCP** producer/consumer
//! against a real server.
//!
//! Gated behind the `e2e` feature so a plain `cargo test` never touches Docker:
//!
//! ```bash
//! cargo test --features e2e
//! ```
//!
//! The `e2e` feature implies all transports (`grpc`, `http`, `tcp`), so the
//! full suite compiles from a single flag.
//!
//! To run against an already-running server instead of auto-starting one, set
//! `EVENTMESH_E2E_EXTERNAL=1`. When neither Docker nor a server is available the
//! tests fail by default so a missing runtime cannot produce a false green.
//!
//! For local compile/smoke checks where skipping is intentional, set
//! `EVENTMESH_E2E_ALLOW_SKIP=1`. Release CI must never set this escape hatch.

#![cfg(feature = "e2e")]

mod grpc_cloud_events;
mod grpc_concurrent_dispatch;
mod grpc_webhook;
mod harness;
mod http_cloud_events;
mod http_publish;
mod http_request_reply;
mod http_subscribe;
#[cfg(feature = "interop_e2e")]
mod interop;
mod publish;
mod request_reply;
mod runtime;
mod subscribe;
mod tcp_cloud_events;
mod tcp_publish;
mod tcp_reconnect;
mod tcp_request_reply;
mod tcp_subscribe;
mod tls;

/// Guard clause for e2e tests: ensures a runtime is available before
/// proceeding.
///
/// A missing runtime is a failure by default. `EVENTMESH_E2E_ALLOW_SKIP=1` is
/// an explicit local-only escape hatch for environments that only want to
/// compile the e2e suite.
macro_rules! require_runtime {
    () => {
        if !crate::runtime::ensure_runtime() {
            if !crate::runtime::allow_skip() {
                panic!("EventMesh runtime is not available; set EVENTMESH_E2E_ALLOW_SKIP=1 only when skipping is intentional");
            }
            return;
        }
    };
}
pub(crate) use require_runtime;
