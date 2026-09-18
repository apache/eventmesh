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

//! Cross-protocol constants, protocol keys, helpers and load-balancing.

#[cfg(any(feature = "grpc", feature = "http"))]
pub mod constants;
#[cfg(feature = "http")]
pub mod loadbalance;
#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
pub mod protocol_key;
#[cfg(any(feature = "grpc", feature = "http"))]
pub mod status_code;
pub mod util;

#[cfg(feature = "http")]
pub use constants::DEFAULT_MESSAGE_TTL;
#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
pub use protocol_key::ProtocolKey;
pub use util::local_ip_v4;
#[cfg(any(feature = "grpc", feature = "tcp"))]
pub use util::RandomStringUtils;
