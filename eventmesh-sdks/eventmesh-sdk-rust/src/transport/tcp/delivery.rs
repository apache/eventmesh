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

//! Dialect decoding and session-bound handler execution shared by TCP roles.

use std::panic::AssertUnwindSafe;

use futures::FutureExt;

use super::connection::{InboundPackage, TcpConnection};
use super::frame::{Command, Package};
use super::message;
use crate::error::{EventMeshError, Result};
use crate::message::Message;
use crate::MessageHandler;

pub(crate) fn decode_message(pkg: &Package) -> Option<Message> {
    if message::is_cloudevents(pkg) {
        #[cfg(feature = "cloud_events")]
        return message::parse_cloud_event(&pkg.body).map(Message::CloudEvent);
        #[cfg(not(feature = "cloud_events"))]
        return None;
    }

    if !message::is_event_mesh_message(pkg) {
        return None;
    }

    let mut message = message::parse_message(&pkg.body)?;
    if let Some(context) = message.delivery_context.as_mut() {
        for (key, value) in &pkg.header.properties {
            context.insert_missing(
                key,
                value
                    .as_str()
                    .map(str::to_owned)
                    .unwrap_or_else(|| value.to_string()),
            );
        }
    }
    Some(Message::EventMesh(message))
}

pub(crate) fn encode_reply(message: &Message) -> Result<Package> {
    match message {
        Message::EventMesh(message) => {
            message::build_message_package(message, Command::ResponseToServer)
        }
        #[cfg(feature = "cloud_events")]
        Message::CloudEvent(event) => {
            message::build_cloud_event_package(event, Command::ResponseToServer)
        }
    }
}

pub(crate) fn inherit_request_metadata(reply_message: &mut Message, request: &Message) {
    #[cfg(feature = "cloud_events")]
    if let (Message::CloudEvent(reply), Message::CloudEvent(request)) =
        (&mut *reply_message, request)
    {
        for (key, value) in request.iter_extensions() {
            if crate::common::metadata::is_delivery_property(key) || reply.extension(key).is_none()
            {
                reply.set_extension(key, value.clone());
            }
        }
        return;
    }

    // Normalize non-CloudEvent replies to EventMeshMessage before merging
    // request metadata.
    #[cfg(feature = "cloud_events")]
    let reply = match reply_message.clone() {
        Message::EventMesh(message) => Ok(message),
        Message::CloudEvent(event) => message::cloud_event_to_message(&event),
    };
    #[cfg(feature = "cloud_events")]
    let request = match request.clone() {
        Message::EventMesh(message) => Ok(message),
        Message::CloudEvent(event) => message::cloud_event_to_message(&event),
    };
    #[cfg(feature = "cloud_events")]
    let (Ok(mut reply), Ok(request)) = (reply, request) else {
        return;
    };
    #[cfg(not(feature = "cloud_events"))]
    let (mut reply, request) = match (reply_message.clone(), request.clone()) {
        (Message::EventMesh(reply), Message::EventMesh(request)) => (reply, request),
    };
    if reply.ttl.is_none() {
        reply.ttl = request.ttl;
    }
    if reply.biz_seq_no.is_none() {
        reply.biz_seq_no = request.biz_seq_no.clone();
    }
    if reply.unique_id.is_none() {
        reply.unique_id = request.unique_id.clone();
    }
    // A reply always belongs to this request, even if the application returned
    // a message originally received from another delivery.
    reply.delivery_context = request.delivery_context.clone();
    for (key, value) in &request.props {
        reply
            .props
            .entry(key.clone())
            .or_insert_with(|| value.clone());
    }
    *reply_message = Message::EventMesh(reply);
}

/// Dispatch one delivery without acknowledging or replying on another socket.
/// Both future construction and polling are covered by the unwind boundary.
pub(super) async fn dispatch<H: MessageHandler>(
    delivery: &InboundPackage,
    connection: &TcpConnection,
    handler: &H,
    ack: Command,
) -> Result<()> {
    if !connection.is_generation_active(delivery.generation).await {
        return Ok(());
    }
    let message = decode_message(&delivery.package)
        .ok_or_else(|| EventMeshError::InvalidMessage("failed to decode TCP delivery".into()))?;
    let request =
        (delivery.package.header.cmd == Command::RequestToClient).then(|| message.clone());
    let handled = AssertUnwindSafe(async { handler.handle(message).await })
        .catch_unwind()
        .await;
    let reply = match handled {
        Ok(result) => result?,
        Err(panic) => {
            let reason = panic
                .downcast_ref::<&str>()
                .copied()
                .or_else(|| panic.downcast_ref::<String>().map(String::as_str))
                .unwrap_or("non-string panic payload");
            return Err(EventMeshError::Tcp(format!(
                "TCP handler panicked: {reason}"
            )));
        }
    };
    // A handler may outlive its socket. Its result belongs to that old delivery,
    // so let the consumer observe the pending reconnect rather than use a new socket.
    if !connection.is_generation_active(delivery.generation).await {
        return Ok(());
    }
    if let Some(mut reply) = reply {
        if let Some(request) = request.as_ref() {
            inherit_request_metadata(&mut reply, request);
        }
        let package = encode_reply(&reply)?;
        connection
            .send_in_generation(package, delivery.generation)
            .await?;
    }
    connection
        .send_in_generation(message::ack(ack, &delivery.package), delivery.generation)
        .await
}
