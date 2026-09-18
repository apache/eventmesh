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

//! Message, subscription and response types.

pub(crate) mod delivery;
pub mod message;
#[cfg(any(test, feature = "grpc", feature = "http", feature = "tcp"))]
pub mod response;
#[cfg(any(feature = "grpc", feature = "http"))]
pub mod subscription;

pub use delivery::DeliveryContext;
pub use message::{EventMeshMessage, EventMeshMessageBuilder};
#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
pub use response::PublishResponse;
#[cfg(any(feature = "grpc", feature = "http"))]
pub use subscription::HeartbeatItem;

/// Wire protocol the SDK advertises to the server (`protocoltype` attribute).
#[cfg(any(feature = "grpc", feature = "http"))]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EventMeshProtocolType {
    /// Native CloudEvents (`io.cloudevents`).
    CloudEvents,
    /// The SDK's lightweight `EventMeshMessage`.
    EventMeshMessage,
}

#[cfg(any(feature = "grpc", feature = "http"))]
impl EventMeshProtocolType {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::CloudEvents => "cloudevents",
            Self::EventMeshMessage => "eventmeshmessage",
        }
    }
}
