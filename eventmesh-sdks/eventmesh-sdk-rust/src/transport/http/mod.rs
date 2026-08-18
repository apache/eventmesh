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

//! HTTP transport for EventMesh.
//!
//! Provides an HTTP-based [`Publisher`](crate::transport::Publisher) and
//! [`HttpConsumer`] for webhook subscription, plus a built-in
//! [`WebhookServer`] for receiving pushed messages from the EventMesh runtime.
//!
//! # Wire format
//!
//! All requests use `application/x-www-form-urlencoded` bodies with JSON
//! payloads inside the `content` field, mirroring the Java SDK. The runtime
//! pushes messages to the consumer's registered webhook URL in the same
//! format, expecting a JSON reply `{"retCode": <int>}`.
//!
//! # Receiving pushed messages
//!
//! The protocol-level HTTP consumer registers a webhook URL with the runtime
//! and sends heartbeats. The public façade normally combines it with a bound,
//! background axum server; applications that own their endpoint can use the
//! registration API and these lower-level codec helpers.
//!
//! 1. **Built-in server** — [`WebhookServer`] can bind before its URL is
//!    registered, eliminating the callback startup race.
//! 2. **Your own endpoint** — host any HTTP server (axum, actix, plain hyper,
//!    …) and decode pushes with the framework-agnostic
//!    [`codec`](crate::transport::http::codec) helpers
//!    ([`codec::parse_push_body`], [`codec::PushMessageRequestBody`],
//!    [`codec::WebhookReply`]). See the `http_consumer_custom` example.

pub mod client;
pub mod codec;
pub mod consumer;
pub mod producer;
pub mod server;
mod webhook;

pub use client::EventMeshHttpClient;
pub use consumer::HttpConsumer;
pub use producer::HttpProducer;
pub use server::WebhookServer;
