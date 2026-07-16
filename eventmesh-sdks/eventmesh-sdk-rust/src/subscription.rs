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

/// A requested subscription.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Subscription {
    /// The topic to receive.
    pub topic: String,
    /// How messages are distributed among consumers.
    pub delivery_mode: DeliveryMode,
    /// Whether delivery is asynchronous or request/reply.
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
    pub(crate) fn as_legacy(&self) -> crate::model::SubscriptionItem {
        crate::model::SubscriptionItem::new(
            self.topic.clone(),
            self.delivery_mode.as_legacy(),
            self.delivery_type.as_legacy(),
        )
    }
}

/// Consumer distribution mode.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum DeliveryMode {
    /// Every subscriber receives the event.
    Broadcast,
    /// One consumer in the group receives the event.
    Cluster,
}

impl DeliveryMode {
    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    pub(crate) const fn as_legacy(self) -> crate::model::SubscriptionMode {
        match self {
            Self::Broadcast => crate::model::SubscriptionMode::BROADCASTING,
            Self::Cluster => crate::model::SubscriptionMode::CLUSTERING,
        }
    }
}

/// Consumer delivery semantics.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum DeliveryType {
    /// Acknowledged asynchronous delivery.
    Async,
    /// Request/reply delivery.
    Sync,
}

impl DeliveryType {
    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    pub(crate) const fn as_legacy(self) -> crate::model::SubscriptionType {
        match self {
            Self::Async => crate::model::SubscriptionType::ASYNC,
            Self::Sync => crate::model::SubscriptionType::SYNC,
        }
    }
}
