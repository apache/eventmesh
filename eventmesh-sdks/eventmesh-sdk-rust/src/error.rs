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

//! Public, pattern-matchable error type for the SDK.

use std::time::Duration;

/// All errors produced by the EventMesh SDK.
///
/// Intentionally `pub` (the previous SDK hid this as `pub(crate)` and erased
/// it to `anyhow`), so callers can `match` on concrete variants.
#[derive(Debug, thiserror::Error)]
pub enum EventMeshError {
    /// A client configuration problem (missing field, bad URL, ...).
    #[error("config error: {0}")]
    Config(String),

    /// The caller supplied an invalid message or argument.
    #[error("invalid argument: {0}")]
    InvalidArgument(String),

    /// A gRPC transport / status error.
    #[cfg(feature = "grpc")]
    #[error("grpc error: {0}")]
    Grpc(Box<tonic::Status>),

    /// A gRPC transport layer (channel/connect) error.
    #[cfg(feature = "grpc")]
    #[error("grpc transport error: {0}")]
    GrpcTransport(Box<tonic::transport::Error>),

    /// An HTTP transport error (Phase 2).
    #[error("http error: status {status}: {message}")]
    Http { status: u16, message: String },

    /// A TCP transport error (Phase 3).
    #[error("tcp error: {0}")]
    Tcp(String),

    /// Serialization / deserialization failure.
    #[error("codec error: {0}")]
    Codec(#[from] serde_json::Error),

    /// A message failed validation before it was sent on the wire.
    #[error("invalid message: {0}")]
    InvalidMessage(String),

    /// An operation did not complete within its timeout.
    #[error("operation timed out after {0:?}")]
    Timeout(Duration),

    /// The EventMesh server returned a non-success response code.
    #[error("server error: code={code} message={message}")]
    Server { code: i32, message: String },

    /// Low-level I/O error.
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),

    /// A channel (mpsc/oneshot) was closed, e.g. the connection task exited.
    #[error("channel closed: {0}")]
    ChannelClosed(String),

    /// The operation is not supported by the active transport.
    #[error("unsupported operation: {0}")]
    Unsupported(String),

    /// Anything else, with a free-form message.
    #[error("{0}")]
    Other(String),
}

/// Convenience `Result` alias used throughout the SDK.
pub type Result<T> = std::result::Result<T, EventMeshError>;

#[cfg(feature = "grpc")]
impl From<tonic::Status> for EventMeshError {
    fn from(status: tonic::Status) -> Self {
        Self::Grpc(Box::new(status))
    }
}

#[cfg(feature = "grpc")]
impl From<tonic::transport::Error> for EventMeshError {
    fn from(err: tonic::transport::Error) -> Self {
        Self::GrpcTransport(Box::new(err))
    }
}
