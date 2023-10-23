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
//! Common utilities for eventmesh.

/// Constants.
pub mod constants;

/// Eventmesh message utilities.
pub mod eventmesh_message_utils;

/// Local IP helper.
pub(crate) mod local_ip;

/// Protocol keys.
mod protocol_key;

/// Random string generator.
mod random_string_util;

/// Re-export protocol keys.
pub use crate::common::protocol_key::ProtocolKey;

/// Re-export random string generator.  
pub use crate::common::random_string_util::RandomStringUtils;

/// Trait for message listener.
pub trait ReceiveMessageListener: Sync + Send {
    /// Message type.
    type Message;

    /// Handle received message.
    ///
    /// # Arguments
    ///
    /// * `msg` - The received message.
    ///
    /// # Returns
    ///
    /// The processed message or error.
    fn handle(&self, msg: Self::Message) -> crate::Result<Option<Self::Message>>;
}
