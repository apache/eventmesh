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

//! Public client configuration.
//!
//! All transport configurations require an explicit endpoint. The old
//! transport configuration types remain crate-private adapters while Catalog
//! and Workflow keep their established public configuration contracts.

mod client;
#[allow(dead_code)]
mod grpc;
#[allow(dead_code)]
mod http;
#[allow(dead_code)]
mod identity;
#[allow(dead_code)]
mod tcp;
pub mod tls;

#[cfg(feature = "grpc")]
pub mod catalog;

#[cfg(feature = "grpc")]
pub mod workflow;

pub use client::{
    ClientOptions, ConsumerOptions, Credentials, Endpoint, EndpointSet, GrpcConfig,
    GrpcConsumerOptions, HttpConfig, Identity, LoadBalance, ProducerOptions, ReconnectPolicy,
    TcpConfig,
};
pub use tls::{TlsClientIdentity, TlsConfig, TlsConfigBuilder};

#[cfg(feature = "grpc")]
pub use catalog::{CatalogClientConfig, CatalogClientConfigBuilder};

#[cfg(feature = "grpc")]
pub use workflow::{WorkflowClientConfig, WorkflowClientConfigBuilder};

// Legacy adapters used by the private protocol implementations and retained
// Catalog/Workflow clients.  Do not make these public again.
#[cfg(feature = "grpc")]
pub(crate) use grpc::GrpcClientConfig;
#[cfg(feature = "http")]
pub(crate) use http::HttpClientConfig;
pub(crate) use identity::ClientIdentity;
pub(crate) use tcp::ReconnectConfig;
#[cfg(feature = "tcp")]
pub(crate) use tcp::TcpClientConfig;
