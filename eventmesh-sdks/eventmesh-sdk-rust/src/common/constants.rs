//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  The ASF
// licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//

//! Shared protocol/SDK constants.

/// Default message TTL in milliseconds (mirrors the Java SDK).
pub const DEFAULT_MESSAGE_TTL: i32 = 4_000;

/// Placeholder URL recorded for stream-mode subscriptions (server treats the
/// stream itself as the delivery channel).
pub const SDK_STREAM_URL: &str = "grpc_stream";

/// CloudEvents spec versions understood by this SDK.
pub struct SpecVersion;
impl SpecVersion {
    /// CloudEvents 1.0 (the version EventMesh normalizes to).
    pub const V1: &str = "1.0";
    /// CloudEvents 0.3 (legacy).
    pub const V03: &str = "0.3";
}

/// Common `datacontenttype` values.
pub struct DataContentType;
impl DataContentType {
    pub const TEXT_PLAIN: &str = "text/plain";
    pub const JSON: &str = "application/json";
    pub const XML: &str = "application/xml";
    pub const PROTOBUF: &str = "application/protobuf";
    pub const CLOUDEVENTS_JSON: &str = "application/cloudevents+json";
}

/// Client role reported to the server (gRPC `clienttype` attribute / TCP purpose).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClientType {
    Pub = 1,
    Sub = 2,
}

impl ClientType {
    pub fn as_i32(self) -> i32 {
        self as i32
    }
}
