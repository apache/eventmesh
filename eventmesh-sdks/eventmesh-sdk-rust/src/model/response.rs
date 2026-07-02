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

//! Server response type.

use serde::{Deserialize, Serialize};

/// The response returned by the broker for fire-and-forget publish / batch
/// publish / subscribe / unsubscribe / heartbeat operations.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
pub struct PublishResponse {
    /// Numeric response code (`status_code` attribute). `0` means success.
    #[serde(default, rename = "respCode")]
    pub code: Option<i64>,
    /// Human-readable response message.
    #[serde(default, rename = "respMsg")]
    pub message: Option<String>,
    /// Server-side processing time, milliseconds.
    #[serde(default, rename = "respTime")]
    pub time: Option<i64>,
}

impl PublishResponse {
    pub fn new(code: Option<i64>, message: Option<String>, time: Option<i64>) -> Self {
        Self {
            code,
            message,
            time,
        }
    }

    /// Whether the server reported success (code == 0).
    pub fn is_success(&self) -> bool {
        self.code.unwrap_or(0) == 0
    }
}

impl std::fmt::Display for PublishResponse {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "PublishResponse(code={:?}, msg={:?}, time={:?})",
            self.code, self.message, self.time
        )
    }
}
