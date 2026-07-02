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

//! Subscription model: topics, modes, types, heartbeat items and reply.

use std::collections::HashMap;
use std::fmt;
use std::str::FromStr;

use serde::{Deserialize, Serialize};

use crate::error::{EventMeshError, Result};

/// One topic the consumer wants delivered, with its delivery mode/type.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Hash)]
pub struct SubscriptionItem {
    pub topic: String,
    pub mode: SubscriptionMode,
    #[serde(rename = "type")]
    pub r#type: SubscriptionType,
}

impl SubscriptionItem {
    pub fn new(topic: impl Into<String>, mode: SubscriptionMode, r#type: SubscriptionType) -> Self {
        Self {
            topic: topic.into(),
            mode,
            r#type,
        }
    }
}

impl fmt::Display for SubscriptionItem {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "SubscriptionItem(topic={}, mode={}, type={})",
            self.topic, self.mode, self.r#type
        )
    }
}

/// Delivery distribution: cluster (competing consumers) vs broadcast (all).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum SubscriptionMode {
    BROADCASTING,
    CLUSTERING,
}

impl fmt::Display for SubscriptionMode {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl SubscriptionMode {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::BROADCASTING => "BROADCASTING",
            Self::CLUSTERING => "CLUSTERING",
        }
    }
}

impl FromStr for SubscriptionMode {
    type Err = EventMeshError;
    fn from_str(s: &str) -> Result<Self> {
        match s {
            "BROADCASTING" => Ok(Self::BROADCASTING),
            "CLUSTERING" => Ok(Self::CLUSTERING),
            other => Err(EventMeshError::InvalidArgument(format!(
                "unknown SubscriptionMode: {other}"
            ))),
        }
    }
}

/// Delivery style.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum SubscriptionType {
    /// Asynchronous push.
    ASYNC,
    /// Synchronous request/reply.
    SYNC,
}

impl fmt::Display for SubscriptionType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl SubscriptionType {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::ASYNC => "ASYNC",
            Self::SYNC => "SYNC",
        }
    }
}

impl FromStr for SubscriptionType {
    type Err = EventMeshError;
    fn from_str(s: &str) -> Result<Self> {
        match s {
            "ASYNC" => Ok(Self::ASYNC),
            "SYNC" => Ok(Self::SYNC),
            other => Err(EventMeshError::InvalidArgument(format!(
                "unknown SubscriptionType: {other}"
            ))),
        }
    }
}

/// Reply sent back over the stream for request/reply consumption.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SubscriptionReply {
    #[serde(rename = "producerGroup")]
    pub producer_group: String,
    pub topic: String,
    pub content: String,
    pub ttl: String,
    #[serde(rename = "uniqueId")]
    pub unique_id: String,
    #[serde(rename = "seqNum")]
    pub seq_num: String,
    pub tag: Option<String>,
    pub properties: HashMap<String, String>,
}

impl SubscriptionReply {
    /// Marker value of the `submessagetype` attribute for a reply message.
    pub const SUB_TYPE: &'static str = "subscription_reply";
}

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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn subscription_item_serializes_type_field() {
        let item =
            SubscriptionItem::new("t", SubscriptionMode::CLUSTERING, SubscriptionType::ASYNC);
        let json = serde_json::to_string(&item).unwrap();
        assert!(json.contains(r#""type":"ASYNC""#));
        let back: SubscriptionItem = serde_json::from_str(&json).unwrap();
        assert_eq!(item, back);
    }
}
