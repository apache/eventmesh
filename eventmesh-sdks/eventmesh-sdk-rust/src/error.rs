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
/// The type is intentionally public and pattern-matchable.  Protocol adapters
/// translate their implementation-specific failures into these variants so a
/// caller never needs to depend on tonic, reqwest, or the TCP frame format.
#[derive(Debug, thiserror::Error)]
pub enum Error {
    /// A client configuration problem (missing field, bad URL, ...).
    #[error("config error: {0}")]
    Config(String),

    /// The caller supplied an invalid message or argument.
    #[error("invalid argument: {0}")]
    InvalidArgument(String),

    /// A gRPC transport / status error.
    #[cfg(feature = "grpc")]
    #[error("grpc error ({code}): {message}")]
    Grpc {
        /// gRPC status code rendered by the transport.
        code: String,
        /// Status description returned by the peer.
        message: String,
    },

    /// A gRPC transport layer (channel/connect) error.
    #[cfg(feature = "grpc")]
    #[error("grpc transport error: {0}")]
    GrpcTransport(String),

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

    /// A protocol adapter rejected or could not encode a wire-level value.
    #[error("{transport} protocol error: {message}")]
    Protocol {
        /// The protocol that produced the error (for example `grpc` or `tcp`).
        transport: &'static str,
        /// A stable, human-readable explanation.
        message: String,
    },

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
}

/// Convenience `Result` alias used throughout the SDK.
pub type Result<T> = std::result::Result<T, Error>;

// The protocol implementation is migrated in stages.  Keep this alias crate
// private so the old spelling remains available to internal modules without
// becoming part of the 2.0 public API.
pub(crate) use Error as EventMeshError;

#[cfg(feature = "grpc")]
impl From<tonic::Status> for Error {
    fn from(status: tonic::Status) -> Self {
        Self::Grpc {
            code: status.code().to_string(),
            message: status.message().to_owned(),
        }
    }
}

#[cfg(feature = "grpc")]
impl From<tonic::transport::Error> for Error {
    fn from(err: tonic::transport::Error) -> Self {
        Self::GrpcTransport(err.to_string())
    }
}
