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
//! A client library for the [Apache EventMesh](https://eventmesh.apache.org)
//! serverless event-driven middleware. The SDK speaks the EventMesh wire
//! protocols and normalizes everything onto a simple [`EventMeshMessage`]
//! model (with optional native CloudEvents interop behind the `cloud_events`
//! feature).
//!
//! # Transports
//!
//! The SDK ships three transports, each gated by its own feature flag:
//!
//! - **gRPC** (default, `grpc` feature) — [`grpc::GrpcProducer`] for
//!   publish / batch / request-reply and [`grpc::GrpcStreamConsumer`] /
//!   [`grpc::GrpcWebhookConsumer`] for stream and webhook subscription.
//! - **HTTP** (`http` feature) — [`http::HttpProducer`] for publish /
//!   request-reply and [`http::HttpConsumer`] for subscribe / heartbeat;
//!   receive pushes via the built-in [`http::WebhookServer`] or your own
//!   endpoint built on the [`http::codec`] helpers.
//! - **TCP** (`tcp` feature) — [`tcp::TcpProducer`] for publish / broadcast /
//!   request-reply and [`tcp::TcpConsumer`] for subscribe + receive loop,
//!   over the native binary wire protocol with auto-reconnect.
//!
//! # Quick example (gRPC producer)
//!
//! Requires the `grpc` feature. The example is compiled by rustdoc only when
//! `grpc` is enabled; on HTTP-only builds it is marked `ignore` so that
//! `cargo test --no-default-features --features http` does not try to compile
//! the `eventmesh::grpc` re-export.
//!
#![cfg_attr(feature = "grpc", doc = "```no_run")]
#![cfg_attr(not(feature = "grpc"), doc = "```ignore")]
//! use eventmesh::{
//!     config::GrpcClientConfig, grpc::GrpcProducer, model::EventMeshMessage,
//!     transport::Publisher,
//! };
//!
//! #[tokio::main]
//! async fn main() -> eventmesh::Result<()> {
//!     let config = GrpcClientConfig::builder()
//!         .server_addr("127.0.0.1")
//!         .server_port(10205)
//!         .env("env").idc("idc").sys("sys")
//!         .producer_group("test-producerGroup")
//!         .build();
//!     let mut producer = GrpcProducer::connect(config)?;
//!     let msg = EventMeshMessage::builder()
//!         .topic("test-topic")
//!         .content("hello from rust")
//!         .build();
//!     let resp = producer.publish(msg).await?;
//!     println!("published: {resp:?}");
//!     Ok(())
//! }
//! ```

#![deny(unsafe_code)]

pub mod common;
pub mod config;
pub mod error;
pub mod model;

#[cfg(feature = "grpc")]
pub mod proto_gen;

#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
pub mod transport;

/// gRPC transport re-exported at the crate root (`eventmesh::grpc`).
#[cfg(feature = "grpc")]
pub use transport::grpc;

/// HTTP transport re-exported at the crate root (`eventmesh::http`).
#[cfg(feature = "http")]
pub use transport::http;

/// TCP transport re-exported at the crate root (`eventmesh::tcp`).
#[cfg(feature = "tcp")]
pub use transport::tcp;

pub use error::{EventMeshError, Result};

use std::future::Future;

/// Convenience trait alias for an async listener of delivered messages.
///
/// A listener returns `Some(message)` to send a reply back to the broker
/// (request-reply semantics), or `None` for plain async consumption.
pub trait MessageListener: Send + Sync + 'static {
    /// The message type this listener accepts.
    type Message: Send;

    /// Handle a delivered message. Return `Some` to reply, `None` to ack only.
    fn handle(&self, message: Self::Message) -> impl Future<Output = Option<Self::Message>> + Send;
}
