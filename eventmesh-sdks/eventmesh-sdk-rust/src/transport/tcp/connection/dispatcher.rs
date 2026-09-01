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

//! Request-response correlation for the TCP transport.

use std::collections::HashMap;

use tokio::sync::{mpsc, oneshot};

use super::super::frame::Package;

pub(super) type PendingKey = (u64, String);

pub(super) enum OutboundCommand {
    Send(Package),
    Request {
        package: Package,
        key: PendingKey,
        response_tx: oneshot::Sender<Package>,
    },
}

/// Owns all pending requests. It is driven exclusively by `SocketDriver`, so
/// request correlation never requires a shared map or an asynchronous lock.
pub(super) struct RpcDispatcher {
    pending: HashMap<PendingKey, oneshot::Sender<Package>>,
}

impl RpcDispatcher {
    pub(super) fn new() -> Self {
        Self {
            pending: HashMap::new(),
        }
    }

    /// Register a request immediately before the driver writes it. A caller
    /// cancelled while its command was queued is detected without touching the
    /// socket.
    pub(super) fn register(
        &mut self,
        key: PendingKey,
        response_tx: oneshot::Sender<Package>,
    ) -> bool {
        if response_tx.is_closed() {
            return false;
        }
        self.pending.insert(key, response_tx);
        true
    }

    pub(super) fn take_response(
        &mut self,
        generation: u64,
        seq: String,
    ) -> Option<oneshot::Sender<Package>> {
        self.pending.remove(&(generation, seq))
    }

    pub(super) fn cancel(&mut self, key: &PendingKey) {
        self.pending.remove(key);
    }

    pub(super) fn connection_lost(&mut self) {
        self.pending.clear();
    }
}

/// Emits a non-blocking cancellation command when an `io()` future is
/// dropped. The dispatcher remains the only owner of the pending map.
pub(super) struct PendingCancellation {
    key: Option<PendingKey>,
    cancel_tx: mpsc::UnboundedSender<PendingKey>,
}

impl PendingCancellation {
    pub(super) fn new(key: PendingKey, cancel_tx: mpsc::UnboundedSender<PendingKey>) -> Self {
        Self {
            key: Some(key),
            cancel_tx,
        }
    }

    pub(super) fn disarm(&mut self) {
        self.key = None;
    }
}

impl Drop for PendingCancellation {
    fn drop(&mut self) {
        if let Some(key) = self.key.take() {
            let _ = self.cancel_tx.send(key);
        }
    }
}
