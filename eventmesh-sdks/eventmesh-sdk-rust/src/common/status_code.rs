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
/// `SUCCESS` (0) means OK; everything else is an error.
pub struct StatusCode;
#[allow(dead_code)]
impl StatusCode {
    pub const SUCCESS: i32 = 0;
    pub const OVERLOAD: i32 = 1;
    pub const EVENTMESH_REQUESTCODE_INVALID: i32 = 2;
    pub const EVENTMESH_SEND_SYNC_MSG_ERR: i32 = 3;
    pub const EVENTMESH_WAITING_RR_MSG_ERR: i32 = 4;
    pub const EVENTMESH_PROTOCOL_HEADER_ERR: i32 = 6;
    pub const EVENTMESH_PROTOCOL_BODY_ERR: i32 = 7;
    pub const EVENTMESH_STOP: i32 = 8;
    pub const EVENTMESH_REJECT_BY_PROCESSOR_ERROR: i32 = 9;
    pub const EVENTMESH_BATCH_PUBLISH_ERR: i32 = 10;
    pub const EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR: i32 = 11;
    pub const EVENTMESH_PACKAGE_MSG_ERR: i32 = 12;
    pub const EVENTMESH_GROUP_PRODUCER_STOPPED_ERR: i32 = 13;
    pub const EVENTMESH_SEND_ASYNC_MSG_ERR: i32 = 14;
    pub const EVENTMESH_REPLY_MSG_ERR: i32 = 15;
    pub const EVENTMESH_RUNTIME_ERR: i32 = 16;
    pub const EVENTMESH_SEND_BATCHLOG_MSG_ERR: i32 = 17;
    pub const EVENTMESH_SUBSCRIBE_ERR: i32 = 17;
    pub const EVENTMESH_UNSUBSCRIBE_ERR: i32 = 18;
    pub const EVENTMESH_HEARTBEAT_ERR: i32 = 19;
    pub const EVENTMESH_ACL_ERR: i32 = 20;
    pub const EVENTMESH_SEND_MESSAGE_SPEED_OVER_LIMIT_ERR: i32 = 21;
    pub const EVENTMESH_REQUEST_REPLY_MSG_ERR: i32 = 22;
    pub const CLIENT_RESUBSCRIBE: i32 = 30;
}

/// HTTP consumer return codes (returned in the webhook response body as
/// `{"retCode": n}`). Mirrors `ClientRetCode`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClientRetCode {
    /// Remote consumer accepted and handled the message.
    RemoteOk = 0,
    /// Healthy consumption.
    Ok = 1,
    /// Transient failure; broker should retry.
    Retry = 2,
    /// Permanent failure.
    Fail = 3,
    /// No active listener; broker should stop pushing.
    NoListen = 5,
}

impl ClientRetCode {
    pub fn as_i32(self) -> i32 {
        self as i32
    }
}

/// Legacy request-code integer for the old HTTP `code` header (rarely needed
/// with the path-based API, kept for completeness).
pub struct RequestCode;
#[allow(dead_code)]
impl RequestCode {
    pub const MSG_SEND_SYNC: i32 = 101;
    pub const MSG_BATCH_SEND: i32 = 102;
    pub const MSG_SEND_ASYNC: i32 = 104;
    pub const HTTP_PUSH_CLIENT_ASYNC: i32 = 105;
    pub const HTTP_PUSH_CLIENT_SYNC: i32 = 106;
    pub const REPLY_MESSAGE: i32 = 301;
    pub const HEARTBEAT: i32 = 203;
    pub const SUBSCRIBE: i32 = 206;
    pub const UNSUBSCRIBE: i32 = 207;
}
