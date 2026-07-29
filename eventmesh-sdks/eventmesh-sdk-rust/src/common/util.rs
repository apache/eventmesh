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

//! Small utility helpers: local IP discovery and random string generation.

use std::time::{SystemTime, UNIX_EPOCH};

#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
use rand::Rng;
#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
use uuid::Uuid;

/// Best-effort local IPv4 (used to populate the `ip` attribute / header).
/// Falls back to `127.0.0.1` when nothing suitable is found.
pub fn local_ip_v4() -> String {
    // Resolve the OS-assigned outbound IP by opening a UDP socket to a public
    // address (no packets are actually sent for UDP connect).
    std::net::UdpSocket::bind("0.0.0.0:0")
        .and_then(|s| {
            s.connect("8.8.8.8:80")?;
            s.local_addr().map(|a| a.ip().to_string())
        })
        .unwrap_or_else(|_| "127.0.0.1".to_string())
}

/// Random string generators used for `bizSeqNo` / `uniqueId` / CloudEvent id.
#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
pub struct RandomStringUtils;
#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
impl RandomStringUtils {
    /// A random UUID v4 (lowercase, hyphenated).
    pub fn generate_uuid() -> String {
        Uuid::new_v4().to_string()
    }

    /// A numeric string of the given length.
    pub fn generate_num(len: usize) -> String {
        let mut rng = rand::thread_rng();
        (0..len)
            .map(|_| char::from_digit(rng.gen_range(0..10), 10).unwrap())
            .collect()
    }

    /// An alphanumeric string of the given length.
    pub fn generate_alphanumeric(len: usize) -> String {
        rand::thread_rng()
            .sample_iter(&rand::distributions::Alphanumeric)
            .take(len)
            .map(char::from)
            .collect::<String>()
    }
}

/// Current time as milliseconds since the Unix epoch.
pub fn now_millis() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    #[test]
    fn uuid_is_unique() {
        assert_ne!(
            RandomStringUtils::generate_uuid(),
            RandomStringUtils::generate_uuid()
        );
    }

    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    #[test]
    fn num_length() {
        let s = RandomStringUtils::generate_num(30);
        assert_eq!(s.len(), 30);
        assert!(s.chars().all(|c| c.is_ascii_digit()));
    }

    #[test]
    fn local_ip_returns_something() {
        let ip = local_ip_v4();
        assert!(!ip.is_empty());
    }
}
