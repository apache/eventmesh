/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::fmt::{Display, Formatter};
use thiserror::Error;

#[allow(dead_code)]
#[derive(Debug, Error)]
pub enum EventMeshError {
    /// Invalid arguments
    InvalidArgs(String),

    /// gRpc status
    #[cfg(feature = "grpc")]
    GRpcStatus(#[from] tonic::Status),

    EventMeshLocal(String),

    EventMeshRemote(String),

    EventMeshFromStrError(String),
}

impl Display for EventMeshError {
    #[inline]
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            #[cfg(feature = "grpc")]
            EventMeshError::GRpcStatus(e) => write!(f, "grpc request error: {}", e),
            EventMeshError::EventMeshLocal(ref err_msg) => {
                write!(f, "EventMesh client error: {}", err_msg)
            }
            EventMeshError::EventMeshRemote(ref err_msg) => {
                write!(f, "EventMesh remote error: {}", err_msg)
            }
            EventMeshError::EventMeshFromStrError(ref err_msg) => {
                write!(f, "EventMesh Parse from String error: {}", err_msg)
            }
            EventMeshError::InvalidArgs(ref err_msg) => {
                write!(f, "Invalid args: {}", err_msg)
            }
        }
    }
}
