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

//! Transport-agnostic async traits and transport modules.
//!
//! Only the publish side is abstracted into a trait ([`Publisher`]). Each
//! transport exposes its own consumer type with transport-specific subscribe /
//! unsubscribe methods and a background receive loop — see the `grpc`,
//! `http`, and `tcp` modules for details.
//!
//! These traits use native Rust-1.86 `async fn in trait` and are therefore
//! **not object-safe** — use concrete types (`GrpcProducer`, etc.) directly,
//! never `dyn`.

use std::future::Future;
use std::time::Duration;

use crate::model::{EventMeshMessage, PublishResponse};

/// Publish-side capability.
pub trait Publisher {
    /// Fire-and-forget publish; returns the broker ack.
    fn publish(
        &self,
        message: EventMeshMessage,
    ) -> impl Future<Output = crate::Result<PublishResponse>> + Send;

    /// Publish many messages in one RPC.
    fn publish_batch(
        &self,
        messages: Vec<EventMeshMessage>,
    ) -> impl Future<Output = crate::Result<PublishResponse>> + Send;
}

/// Request/reply capability implemented only by transports with a complete
/// responder path.
pub trait RequestReply {
    /// Synchronous request/reply. `timeout` bounds how long we wait for the
    /// consumer reply.
    fn request_reply(
        &self,
        message: EventMeshMessage,
        timeout: Duration,
    ) -> impl Future<Output = crate::Result<EventMeshMessage>> + Send;
}

#[cfg(feature = "grpc")]
pub mod grpc;

#[cfg(feature = "http")]
pub mod http;

#[cfg(feature = "tcp")]
pub mod tcp;
