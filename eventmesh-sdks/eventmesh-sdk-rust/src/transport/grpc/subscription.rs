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

//! Subscription control state shared by the gRPC consumer and heartbeat driver.

use std::collections::HashMap;
use std::sync::Arc;

use tokio::sync::{mpsc, Mutex};
use tokio_util::sync::CancellationToken;

use crate::error::{EventMeshError, Result};
use crate::proto_gen::PbCloudEvent;
use crate::subscription::Subscription;

/// A locally-recorded subscription entry, used by the heartbeat loop.
#[derive(Debug, Clone)]
pub(crate) struct SubscriptionEntry {
    pub(crate) item: Subscription,
    pub(crate) url: String,
}

pub(super) type Subscriptions = Arc<Mutex<SubscriptionState>>;
pub(super) type StreamTx = Arc<Mutex<Option<mpsc::Sender<PbCloudEvent>>>>;

/// Remote RPCs, local commits, heartbeat snapshots and replay are serialized
/// through one owner. Cancellation closes the control API immediately.
pub(super) struct SubscriptionState {
    pub(super) entries: HashMap<(String, String), SubscriptionEntry>,
    shutdown: CancellationToken,
}

impl SubscriptionState {
    pub(super) fn new(shutdown: CancellationToken) -> Self {
        Self {
            entries: HashMap::new(),
            shutdown,
        }
    }

    pub(super) fn ensure_open(&self) -> Result<()> {
        if self.shutdown.is_cancelled() {
            return Err(EventMeshError::ChannelClosed(
                "gRPC consumer is shut down".into(),
            ));
        }
        Ok(())
    }
}
