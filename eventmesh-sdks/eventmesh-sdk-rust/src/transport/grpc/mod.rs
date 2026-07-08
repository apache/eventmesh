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

//! gRPC transport for the EventMesh server.
//!
//! - [`GrpcProducer`] implements [`crate::transport::Publisher`].
//! - [`GrpcConsumer`] implements [`crate::transport::Subscriber`] plus a
//!   streaming receive loop driven by a [`crate::MessageListener`].
//!
//! Wire format is CloudEvents-protobuf; [`EventMeshMessage`] is converted at
//! the boundary by [`codec`].

pub mod client;
pub mod codec;
pub mod consumer;
pub mod heartbeat;
pub mod producer;

pub use client::GrpcClient;
pub use consumer::{GrpcConsumer, StreamServe};
pub use producer::GrpcProducer;
