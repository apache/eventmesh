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
//! All transport configurations require an explicit endpoint. HTTP and TCP
//! still use crate-private transport adapters while their implementations are
//! migrated to the shared API.

mod client;
#[cfg(feature = "http")]
#[allow(dead_code)]
mod http;
#[allow(dead_code)]
mod identity;
#[allow(dead_code)]
mod tcp;

pub use client::{
    ClientOptions, ConsumerOptions, Credentials, Endpoint, EndpointSet, GrpcConfig,
    GrpcConsumerOptions, HttpConfig, Identity, LoadBalance, ProducerOptions, ReconnectPolicy,
    TcpConfig, DEFAULT_GRPC_REQUEST_TIMEOUT, DEFAULT_HTTP_REQUEST_TIMEOUT,
    DEFAULT_TCP_CONNECT_TIMEOUT, DEFAULT_TCP_CONTROL_TIMEOUT, DEFAULT_TCP_REQUEST_TIMEOUT,
};

// Legacy adapters used by the private protocol implementations. Do not make
// these public again.
#[cfg(feature = "http")]
pub(crate) use http::HttpClientConfig;
pub(crate) use identity::ClientIdentity;
pub(crate) use tcp::ReconnectConfig;
#[cfg(feature = "tcp")]
pub(crate) use tcp::TcpClientConfig;
