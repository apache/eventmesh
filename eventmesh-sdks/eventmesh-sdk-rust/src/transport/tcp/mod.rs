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

//! Native TCP transport for the EventMesh Rust SDK.
//!
//! The TCP transport uses the EventMesh binary wire protocol (length-prefixed
//! frames with a `"EventMesh"` magic prefix) and is fully interoperable with
//! the Java runtime's TCP endpoint (default port `10000`).
//!
//! Producers provide publishing, request/reply, and broadcast operations.
//! Consumers decode deliveries into [`crate::Message`] and dispatch them to
//! [`crate::MessageHandler`]. See [`crate::tcp`] for the public client API.
//!
//! # CloudEvents over TCP
//!
//! With the `cloud_events` feature, the TCP producer can send native
//! [`cloudevents::Event`] values via [`TcpProducer::publish_cloud_event`],
//! [`TcpProducer::broadcast_cloud_event`], and
//! [`TcpProducer::request_reply_cloud_event`]. Consumers preserve inbound
//! CloudEvents as `Message::CloudEvent` values.
//!
//! **Important:** the event's `datacontenttype` must be set to
//! `application/cloudevents+json`. The Java runtime's downlink codec
//! (`CloudEventsProtocolAdaptor.fromCloudEvent`) uses `datacontenttype` to
//! look up the CloudEvents serializer; only `application/cloudevents+json`
//! is registered. Any other value causes an NPE and the message is silently
//! dropped before reaching consumers.
//!
//! ```ignore
//! use cloudevents::EventBuilderV10;
//!
//! let event = EventBuilderV10::new()
//!     .id("1")
//!     .source("https://example.com")
//!     .ty("com.example.event")
//!     .subject(topic)
//!     .data("application/cloudevents+json", serde_json::json!({"msg": "hi"}))
//!     .build()?;
//! ```

pub mod codec;
pub mod connection;
pub mod consumer;
pub mod frame;
pub mod message;
pub mod producer;

pub use consumer::{ShutdownReason, TcpConsumer};
pub use producer::TcpProducer;
