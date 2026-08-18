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

//! Subscription declarations shared by all transports.

use std::fmt;
use std::str::FromStr;

use serde::{Deserialize, Serialize};

use crate::error::{EventMeshError, Result};

/// A requested subscription.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Hash)]
pub struct Subscription {
    /// The topic to receive.
    pub topic: String,
    /// How messages are distributed among consumers.
    #[serde(rename = "mode")]
    pub delivery_mode: DeliveryMode,
    /// Whether delivery is asynchronous or request/reply.
    #[serde(rename = "type")]
    pub delivery_type: DeliveryType,
}

impl Subscription {
    /// Create an asynchronous clustered subscription for `topic`.
    pub fn new(topic: impl Into<String>) -> Self {
        Self {
            topic: topic.into(),
            delivery_mode: DeliveryMode::Cluster,
            delivery_type: DeliveryType::Async,
        }
    }

    /// Set the delivery mode.
    pub fn with_delivery_mode(mut self, delivery_mode: DeliveryMode) -> Self {
        self.delivery_mode = delivery_mode;
        self
    }

    /// Set the delivery type.
    pub fn with_delivery_type(mut self, delivery_type: DeliveryType) -> Self {
        self.delivery_type = delivery_type;
        self
    }

    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    pub(crate) fn validate(&self) -> crate::Result<()> {
        if self.topic.trim().is_empty() {
            return Err(crate::Error::InvalidArgument(
                "subscription topic must not be empty".into(),
            ));
        }
        Ok(())
    }
}

impl fmt::Display for Subscription {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "Subscription(topic={}, mode={}, type={})",
            self.topic, self.delivery_mode, self.delivery_type
        )
    }
}

/// Consumer distribution mode.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Hash)]
#[non_exhaustive]
pub enum DeliveryMode {
    /// Every subscriber receives the event.
    #[serde(rename = "BROADCASTING")]
    Broadcast,
    /// One consumer in the group receives the event.
    #[serde(rename = "CLUSTERING")]
    Cluster,
}

impl DeliveryMode {
    /// Return the EventMesh wire value.
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Broadcast => "BROADCASTING",
            Self::Cluster => "CLUSTERING",
        }
    }
}

impl fmt::Display for DeliveryMode {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for DeliveryMode {
    type Err = EventMeshError;

    fn from_str(value: &str) -> Result<Self> {
        match value {
            "BROADCASTING" => Ok(Self::Broadcast),
            "CLUSTERING" => Ok(Self::Cluster),
            other => Err(EventMeshError::InvalidArgument(format!(
                "unknown DeliveryMode: {other}"
            ))),
        }
    }
}

/// Consumer delivery semantics.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Hash)]
#[non_exhaustive]
pub enum DeliveryType {
    /// Acknowledged asynchronous delivery.
    #[serde(rename = "ASYNC")]
    Async,
    /// Request/reply delivery.
    #[serde(rename = "SYNC")]
    Sync,
}

impl DeliveryType {
    /// Return the EventMesh wire value.
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Async => "ASYNC",
            Self::Sync => "SYNC",
        }
    }
}

impl fmt::Display for DeliveryType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for DeliveryType {
    type Err = EventMeshError;

    fn from_str(value: &str) -> Result<Self> {
        match value {
            "ASYNC" => Ok(Self::Async),
            "SYNC" => Ok(Self::Sync),
            other => Err(EventMeshError::InvalidArgument(format!(
                "unknown DeliveryType: {other}"
            ))),
        }
    }
}

#[cfg(all(test, any(feature = "grpc", feature = "http", feature = "tcp")))]
mod tests {
    use super::{DeliveryMode, DeliveryType, Subscription};

    #[test]
    fn blank_topics_are_rejected() {
        assert!(Subscription::new("").validate().is_err());
        assert!(Subscription::new(" \t").validate().is_err());
        assert!(Subscription::new("topic").validate().is_ok());
    }

    #[test]
    fn subscription_uses_eventmesh_wire_names() {
        let subscription = Subscription::new("t")
            .with_delivery_mode(DeliveryMode::Cluster)
            .with_delivery_type(DeliveryType::Async);
        let json = serde_json::to_string(&subscription).unwrap();
        assert_eq!(json, r#"{"topic":"t","mode":"CLUSTERING","type":"ASYNC"}"#);
        let decoded: Subscription = serde_json::from_str(&json).unwrap();
        assert_eq!(decoded, subscription);
    }
}
