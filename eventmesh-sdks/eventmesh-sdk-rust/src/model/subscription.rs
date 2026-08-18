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

//! Heartbeat subscription payload model.

use serde::{Deserialize, Serialize};

/// One entry of the heartbeat payload (`text_data` JSON array).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HeartbeatItem {
    pub topic: String,
    pub url: String,
}

impl HeartbeatItem {
    pub fn new(topic: impl Into<String>, url: impl Into<String>) -> Self {
        Self {
            topic: topic.into(),
            url: url.into(),
        }
    }
}
