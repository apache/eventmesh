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

//! Native TCP transport for the EventMesh Rust SDK.
//!
//! The TCP transport uses the EventMesh binary wire protocol (length-prefixed
//! frames with a `"EventMesh"` magic prefix) and is fully interoperable with
//! the Java runtime's TCP endpoint (default port `10000`).
//!
//! Like the gRPC and HTTP transports, it normalizes everything onto the
//! [`EventMeshMessage`](crate::model::EventMeshMessage) model and implements
//! the [`Publisher`](crate::transport::Publisher) / [`Subscriber`](crate::transport::Subscriber)
//! traits.
//!
//! # Quick example (producer)
//!
//! ```ignore
//! use eventmesh::{
//!     config::TcpClientConfig, tcp::TcpProducer,
//!     model::EventMeshMessage, transport::Publisher,
//! };
//!
//! #[tokio::main]
//! async fn main() -> eventmesh::Result<()> {
//!     let config = TcpClientConfig::builder()
//!         .server_addr("127.0.0.1").server_port(10000)
//!         .producer_group("g")
//!         .build();
//!     let producer = TcpProducer::connect(config).await?;
//!     let msg = EventMeshMessage::builder().topic("t").content("hi").build();
//!     producer.publish(msg).await?;
//!     Ok(())
//! }
//! ```
//!
//! # Quick example (consumer)
//!
//! ```ignore
//! use eventmesh::{
//!     config::TcpClientConfig, tcp::TcpConsumer,
//!     model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
//!     MessageListener,
//! };
//!
//! struct MyListener;
//! impl MessageListener for MyListener {
//!     type Message = EventMeshMessage;
//!     async fn handle(&self, msg: EventMeshMessage) -> Option<EventMeshMessage> {
//!         println!("received: {:?}", msg.content);
//!         None
//!     }
//! }
//!
//! #[tokio::main]
//! async fn main() -> eventmesh::Result<()> {
//!     let config = TcpClientConfig::builder()
//!         .server_addr("127.0.0.1").server_port(10000)
//!         .consumer_group("g")
//!         .build();
//!     let consumer = TcpConsumer::connect(config, MyListener).await?;
//!     let items = vec![SubscriptionItem::new("t", SubscriptionMode::CLUSTERING, SubscriptionType::ASYNC)];
//!     consumer.listen(items)?
//!         .with_graceful_shutdown(async { tokio::signal::ctrl_c().await.ok(); })
//!         .await?;
//!     Ok(())
//! }
//! ```

pub mod codec;
pub mod connection;
pub mod consumer;
pub mod frame;
pub mod message;
pub mod producer;

pub use consumer::{ListenServe, TcpConsumer};
pub use producer::TcpProducer;
