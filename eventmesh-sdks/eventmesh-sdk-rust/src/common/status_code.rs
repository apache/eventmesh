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

//! Status / return codes returned by the EventMesh server.

/// gRPC status codes the EventMesh server returns in the `statuscode`
/// CloudEvent attribute (mirrors `org.apache.eventmesh.common.protocol.grpc.common.StatusCode`).
///
/// A heartbeat status of 30 requests subscription replay.
#[cfg(feature = "grpc")]
pub struct StatusCode;
#[cfg(feature = "grpc")]
impl StatusCode {
    pub const CLIENT_RESUBSCRIBE: i32 = 30;
}

/// HTTP consumer return codes (returned in the webhook response body as
/// `{"retCode": n}`). Mirrors `ClientRetCode`.
#[cfg(feature = "http-client")]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClientRetCode {
    /// Healthy consumption.
    Ok = 1,
    /// Transient failure; broker should retry.
    Retry = 2,
}

/// Request codes for the HTTP `code` header used by root-path routing.
#[cfg(feature = "http-client")]
pub struct RequestCode;
#[cfg(feature = "http-client")]
impl RequestCode {
    pub const MSG_SEND_ASYNC: i32 = 104;
    pub const HEARTBEAT: i32 = 203;
    pub const SUBSCRIBE: i32 = 206;
    pub const UNSUBSCRIBE: i32 = 207;
}
