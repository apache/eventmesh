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

//! Public handler contract and private adapters for the previous transport
//! engines.

use std::future::Future;

use crate::error::Result;
#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
use crate::message::EventMeshMessage;
use crate::message::Message;

/// Handles a delivered EventMesh message.
///
/// Return `Ok(None)` to acknowledge an asynchronous message, or
/// `Ok(Some(reply))` to reply to a synchronous delivery.  Returning an error
/// reports application failure to the transport adapter rather than treating
/// it as a successful business acknowledgement.
pub trait MessageHandler: Send + Sync + 'static {
    /// Handle one delivery.
    fn handle(&self, message: Message) -> impl Future<Output = Result<Option<Message>>> + Send;
}

impl<F, Fut> MessageHandler for F
where
    F: Fn(Message) -> Fut + Send + Sync + 'static,
    Fut: Future<Output = Result<Option<Message>>> + Send + 'static,
{
    fn handle(&self, message: Message) -> impl Future<Output = Result<Option<Message>>> + Send {
        (self)(message)
    }
}

/// Adapter used while the protocol engines are migrated to the v2 handler
/// contract.  The old engines only accept native EventMesh messages.
#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
pub(crate) struct NativeHandler<H> {
    handler: H,
}

#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
impl<H> NativeHandler<H> {
    pub(crate) fn new(handler: H) -> Self {
        Self { handler }
    }
}

#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
impl<H: MessageHandler> crate::MessageListener for NativeHandler<H> {
    type Message = EventMeshMessage;

    async fn handle(&self, message: EventMeshMessage) -> Result<Option<EventMeshMessage>> {
        match self.handler.handle(Message::EventMesh(message)).await {
            Ok(Some(reply)) => reply.into_event_mesh().map(Some),
            Ok(None) => Ok(None),
            Err(error) => Err(error),
        }
    }
}
