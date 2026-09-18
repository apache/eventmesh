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

//! Owned task state retained across cancelled lifecycle waits.

use tokio::task::{JoinError, JoinHandle};

pub(crate) struct BackgroundTask<T> {
    handle: Option<JoinHandle<T>>,
    result: Option<std::result::Result<T, JoinError>>,
}

impl<T> BackgroundTask<T> {
    pub(crate) fn new(handle: JoinHandle<T>) -> Self {
        Self {
            handle: Some(handle),
            result: None,
        }
    }

    /// Borrow the handle while waiting and retain its output until all cleanup
    /// is complete. Cancelling this future never detaches the task or loses a
    /// completed result, and a completed handle is never polled twice.
    pub(crate) async fn wait(&mut self) {
        if let Some(handle) = self.handle.as_mut() {
            self.result = Some(handle.await);
            self.handle = None;
        }
    }

    /// Consume the result only after the last cancellation point in the caller.
    pub(crate) fn take_result(&mut self) -> Option<std::result::Result<T, JoinError>> {
        self.result.take()
    }

    #[cfg(feature = "http")]
    pub(crate) fn is_finished(&self) -> bool {
        self.handle.as_ref().is_none_or(JoinHandle::is_finished)
    }
}

impl<T> Drop for BackgroundTask<T> {
    fn drop(&mut self) {
        if let Some(handle) = &self.handle {
            handle.abort();
        }
    }
}
