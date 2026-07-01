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
use std::collections::HashMap;
use std::fmt;
use std::fmt::{Display, Formatter};
use std::str::FromStr;

use serde::{Deserialize, Serialize};

use crate::error::EventMeshError;

#[derive(Debug, Deserialize, Serialize, PartialEq, Eq, Hash, Clone)]
pub struct SubscriptionItem {
    pub topic: String,
    pub mode: SubscriptionMode,
    #[serde(rename = "type")]
    pub type_: SubscriptionType,
}

impl SubscriptionItem {
    pub fn new(topic: impl Into<String>, mode: SubscriptionMode, type_: SubscriptionType) -> Self {
        SubscriptionItem {
            topic: topic.into(),
            mode,
            type_,
        }
    }
}

impl fmt::Display for SubscriptionItem {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(
            f,
            "SubscriptionItem {{ topic: {}, mode: {}, type: {} }}",
            self.topic, self.mode, self.type_
        )
    }
}

#[derive(Debug, PartialEq, Eq, Clone, Deserialize, Serialize, Hash)]
pub enum SubscriptionMode {
    BROADCASTING,
    CLUSTERING,
    UNRECOGNIZED,
}

impl Display for SubscriptionMode {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        writeln!(f, "{}", self.to_string())
    }
}

impl SubscriptionMode {
    pub fn to_string(&self) -> &'static str {
        match self {
            SubscriptionMode::BROADCASTING => "BROADCASTING",
            SubscriptionMode::CLUSTERING => "CLUSTERING",
            SubscriptionMode::UNRECOGNIZED => "UNRECOGNIZED",
        }
    }

    fn from_str_inner(input: &str) -> Result<Self, EventMeshError> {
        match input {
            "BROADCASTING" => Ok(SubscriptionMode::BROADCASTING),
            "CLUSTERING" => Ok(SubscriptionMode::CLUSTERING),
            "UNRECOGNIZED" => Ok(SubscriptionMode::UNRECOGNIZED),
            _ => Err(EventMeshError::EventMeshFromStrError(format!(
                "{} can not parse to SubscriptionMode",
                input
            ))),
        }
    }
}

impl FromStr for SubscriptionMode {
    type Err = EventMeshError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Self::from_str_inner(s)
    }
}

impl TryFrom<String> for SubscriptionMode {
    type Error = EventMeshError;

    fn try_from(value: String) -> Result<Self, Self::Error> {
        Self::from_str_inner(value.as_str())
    }
}

impl TryFrom<&'static str> for SubscriptionMode {
    type Error = EventMeshError;

    fn try_from(value: &'static str) -> Result<Self, Self::Error> {
        Self::from_str_inner(value)
    }
}

#[derive(Debug, PartialEq, Eq, Clone, Deserialize, Serialize, Hash)]
pub enum SubscriptionType {
    SYNC,
    ASYNC,
    UNRECOGNIZED,
}

impl Display for SubscriptionType {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        writeln!(f, "{}", self.to_string())
    }
}

impl SubscriptionType {
    pub fn to_string(&self) -> &'static str {
        match self {
            SubscriptionType::SYNC => "SYNC",
            SubscriptionType::ASYNC => "ASYNC",
            SubscriptionType::UNRECOGNIZED => "UNRECOGNIZED",
        }
    }

    fn from_str_inner(s: &str) -> Result<SubscriptionType, EventMeshError> {
        match s {
            "SYNC" => Ok(SubscriptionType::SYNC),
            "ASYNC" => Ok(SubscriptionType::ASYNC),
            "UNRECOGNIZED" => Ok(SubscriptionType::UNRECOGNIZED),
            _ => Err(EventMeshError::EventMeshFromStrError(format!(
                "{} can not parse to SubscriptionMode",
                s
            ))),
        }
    }
}

impl FromStr for SubscriptionType {
    type Err = EventMeshError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Self::from_str_inner(s)
    }
}

impl TryFrom<String> for SubscriptionType {
    type Error = EventMeshError;

    fn try_from(value: String) -> Result<Self, Self::Error> {
        Self::from_str_inner(value.as_str())
    }
}

impl TryFrom<&'static str> for SubscriptionType {
    type Error = EventMeshError;

    fn try_from(value: &'static str) -> Result<Self, Self::Error> {
        Self::from_str_inner(value)
    }
}

#[derive(Debug)]
pub(crate) struct SubscriptionItemWrapper {
    pub(crate) subscription_item: SubscriptionItem,
    pub(crate) url: String,
}

impl Display for SubscriptionItemWrapper {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        writeln!(
            f,
            "SubscriptionItem={},url={}",
            self.subscription_item, self.url
        )
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SubscriptionReply {
    #[serde(rename = "producerGroup")]
    pub(crate) producer_group: String,
    pub(crate) topic: String,
    pub(crate) content: String,
    pub(crate) ttl: String,
    #[serde(rename = "uniqueId")]
    pub(crate) unique_id: String,
    #[serde(rename = "seqNum")]
    pub(crate) seq_num: String,
    pub(crate) tag: Option<String>,
    pub(crate) properties: HashMap<String, String>,
}

impl SubscriptionReply {
    pub const SUB_TYPE: &'static str = "subscription_reply";

    pub fn new(
        producer_group: String,
        topic: String,
        content: String,
        ttl: String,
        unique_id: String,
        seq_num: String,
        tag: Option<String>,
        properties: HashMap<String, String>,
    ) -> Self {
        Self {
            producer_group,
            topic,
            content,
            ttl,
            unique_id,
            seq_num,
            tag,
            properties,
        }
    }
}

impl ToString for SubscriptionReply {
    fn to_string(&self) -> String {
        format!(
            "SubscriptionReply {{
                            producer_group: {:?},
                            topic: {:?},
                            content: {:?},
                            ttl: {:?},
                            unique_id: {:?},
                            seq_num: {:?},
                            tag: {:?},
                            properties: {:?}
                        }}",
            self.producer_group,
            self.topic,
            self.content,
            self.ttl,
            self.unique_id,
            self.seq_num,
            self.tag,
            self.properties
        )
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HeartbeatItem {
    pub(crate) topic: String,
    pub(crate) url: String,
}

impl HeartbeatItem {
    pub fn new(topic: String, url: String) -> Self {
        Self { topic, url }
    }
}

impl Display for HeartbeatItem {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        writeln!(
            f,
            "HeartbeatItem {{
                            topic: {},
                            url: {}
                        }}",
            self.url, self.topic
        )
    }
}
