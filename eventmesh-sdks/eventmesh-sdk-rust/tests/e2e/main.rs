//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  The ASF
// licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//

//! End-to-end tests for the EventMesh Rust SDK.
//!
//! These tests spin up the EventMesh runtime via `docker compose` (rocketmq
//! profile) and exercise the gRPC producer/consumer against a real server.
//!
//! Gated behind the `e2e` feature so a plain `cargo test` never touches Docker:
//!
//! ```bash
//! cargo test --features e2e
//! ```
//!
//! To run against an already-running server instead of auto-starting one, set
//! `EVENTMESH_E2E_EXTERNAL=1`. When neither Docker nor a server is available the
//! tests skip themselves (rather than fail).

#![cfg(feature = "e2e")]

mod harness;
mod publish;
mod request_reply;
mod runtime;
mod subscribe;
