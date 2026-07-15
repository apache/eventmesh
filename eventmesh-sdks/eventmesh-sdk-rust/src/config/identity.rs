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

//! Client identity fields shared by every transport.

/// Who the client is. Every protocol carries these to the server as either
/// CloudEvent attributes (gRPC), HTTP headers, or `UserAgent` fields (TCP).
#[derive(Clone)]
pub struct ClientIdentity {
    /// Environment tag (e.g. `"prod"`).
    pub env: String,
    /// Data-center id (e.g. `"default"`).
    pub idc: String,
    /// Subsystem / application name.
    pub sys: String,
    /// OS process id, as a string.
    pub pid: String,
    /// Local IP (auto-detected if you use [`ClientIdentity::detect`]).
    pub ip: String,
    /// Language tag (defaults to `"RUST"`).
    pub language: String,
    /// Optional ACL username.
    pub username: String,
    /// Optional ACL password.
    pub password: String,
    /// Optional auth token (JWT etc.).
    pub token: Option<String>,
    /// Producer group name.
    pub producer_group: String,
    /// Consumer group name.
    pub consumer_group: String,
}

impl ClientIdentity {
    /// Detect local IP + pid and fill in sensible defaults.
    pub fn detect() -> Self {
        Self {
            env: "env".into(),
            idc: "default".into(),
            sys: "sys".into(),
            pid: std::process::id().to_string(),
            ip: crate::common::local_ip_v4(),
            language: "RUST".into(),
            username: String::new(),
            password: String::new(),
            token: None,
            producer_group: "DefaultProducerGroup".into(),
            consumer_group: "DefaultConsumerGroup".into(),
        }
    }
}

impl Default for ClientIdentity {
    fn default() -> Self {
        Self::detect()
    }
}

impl std::fmt::Debug for ClientIdentity {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ClientIdentity")
            .field("env", &self.env)
            .field("idc", &self.idc)
            .field("sys", &self.sys)
            .field("pid", &self.pid)
            .field("ip", &self.ip)
            .field("language", &self.language)
            .field("username", &self.username)
            .field("password", &"***")
            .field("token", &self.token.as_ref().map(|_| "***"))
            .field("producer_group", &self.producer_group)
            .field("consumer_group", &self.consumer_group)
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn debug_redacts_secrets() {
        let id = ClientIdentity {
            password: "hunter2".into(),
            token: Some("jwt-token".into()),
            ..ClientIdentity::detect()
        };
        let s = format!("{id:?}");
        assert!(!s.contains("hunter2"), "password leaked: {s}");
        assert!(!s.contains("jwt-token"), "token leaked: {s}");
        assert!(s.contains("***"), "redaction marker missing: {s}");
    }

    #[test]
    fn debug_redacts_empty_secrets() {
        let id = ClientIdentity::detect();
        let s = format!("{id:?}");
        assert!(
            !s.contains("password") || s.contains("\"***\""),
            "password field should be redacted: {s}"
        );
    }
}
