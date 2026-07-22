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

//! Apache EventMesh Rust SDK.
//!
//! # API map
//!
//! Start with a feature-gated protocol client: [`GrpcClient`] (`grpc`),
//! [`HttpClient`] (`http`), or [`TcpClient`] (`tcp`). Each client creates a
//! producer and its transport-specific consumer. Producers accept [`Message`],
//! while consumers deliver it to [`MessageHandler`]. Use [`config`] for
//! endpoints, identity, credentials, timeouts, TLS, and reconnect settings;
//! use [`Subscription`] to declare what a consumer receives.
//!
//! `Message` preserves native EventMesh messages and, with `cloud_events`,
//! `cloudevents::Event`. It is not a serialization format: gRPC protobuf,
//! HTTP form, and TCP frame encoding remain transport implementation details.
//!
//! # Features
//!
//! The default feature set is empty. Enable `grpc`, `http`, or `tcp` for a
//! transport; `cloud_events` adds CloudEvents support and `tls` adds gRPC TLS.
//! `full` enables every runtime feature. See the repository README and
//! `examples/` for runnable programs.
//!
//! # Delivery and lifecycle
//!
//! A [`MessageHandler`] returns `Ok(None)` to acknowledge an asynchronous
//! delivery, `Ok(Some(reply))` to reply to a synchronous delivery, or `Err(_)`
//! to report application failure. Long-lived consumers expose `shutdown` and
//! `join`; HTTP consumers manage webhook registration and heartbeat, so an
//! HTTP server is provided separately by [`webhook::WebhookServer`].

#![deny(unsafe_code)]

// These modules are wire adapters retained behind the v2 public façade. Some
// protocol-specific compatibility paths are intentionally feature-dependent,
// so not every helper is referenced in every feature combination.
#[allow(dead_code, unused_imports)]
mod common;
pub mod config;
pub mod discovery;
mod error;
mod handler;
pub mod message;
#[allow(dead_code, unused_imports)]
mod model;
pub mod subscription;
pub mod webhook;

#[cfg(feature = "grpc")]
mod proto_gen;

/// Catalog service client, available with the `grpc` feature.
#[cfg(feature = "grpc")]
pub mod catalog;

/// Workflow service client, available with the `grpc` feature.
#[cfg(feature = "grpc")]
pub mod workflow;

#[cfg(feature = "grpc")]
mod service;

#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
#[allow(dead_code, unused_imports)]
mod transport;

/// gRPC client API.
#[cfg(feature = "grpc")]
pub mod grpc;

/// HTTP client API.
#[cfg(feature = "http")]
pub mod http;

/// TCP client API.
#[cfg(feature = "tcp")]
pub mod tcp;

pub use error::{Error, Result};
pub use handler::MessageHandler;
pub use message::{EventMeshMessage, Message, MessageKind, PublishReceipt};
pub use subscription::{DeliveryMode, DeliveryType, Subscription};

#[cfg(feature = "grpc")]
pub use grpc::{GrpcClient, GrpcWebhookConsumer};

#[cfg(feature = "http")]
pub use http::HttpClient;

#[cfg(feature = "tcp")]
pub use tcp::TcpClient;

#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
use std::future::Future;

/// Convenience trait alias for an async listener of delivered messages.
///
/// A listener returns `Some(message)` to send a reply back to the broker
/// (request-reply semantics), `None` for plain async consumption, or an error
/// to tell the adapter that the delivery was not handled successfully.
#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
pub(crate) trait MessageListener: Send + Sync + 'static {
    /// The message type this listener accepts.
    type Message: Send;

    /// Handle a delivered message. Return `Some` to reply, `None` to ack only.
    fn handle(
        &self,
        message: Self::Message,
    ) -> impl Future<Output = Result<Option<Self::Message>>> + Send;
}
