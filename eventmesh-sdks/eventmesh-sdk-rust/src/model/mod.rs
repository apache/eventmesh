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

pub mod message;
pub mod response;
pub mod subscription;

pub use message::{EventMeshMessage, EventMeshMessageBuilder};
pub use response::PublishResponse;
pub use subscription::HeartbeatItem;

/// Wire protocol the SDK advertises to the server (`protocoltype` attribute).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EventMeshProtocolType {
    /// Native CloudEvents (`io.cloudevents`).
    CloudEvents,
    /// The SDK's lightweight `EventMeshMessage`.
    EventMeshMessage,
}

impl EventMeshProtocolType {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::CloudEvents => "cloudevents",
            Self::EventMeshMessage => "eventmeshmessage",
        }
    }
}
