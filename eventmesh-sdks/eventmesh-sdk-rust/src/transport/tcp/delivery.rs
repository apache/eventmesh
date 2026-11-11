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
use crate::error::{Error, Result};
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

pub(crate) fn inherit_request_metadata(reply: &mut Message, request: &Message) -> Result<()> {
    #[cfg(feature = "cloud_events")]
    use crate::common::metadata::is_delivery_property;
    use crate::common::metadata::is_reserved_property;

    match reply {
        Message::EventMesh(reply) => {
            // Only metadata crosses dialects. The original payload and the
            // reply's chosen representation never participate in conversion.
            let mut attributes = match request {
                Message::EventMesh(request) => native_reply_attributes(request),
                #[cfg(feature = "cloud_events")]
                Message::CloudEvent(request) => request
                    .iter_extensions()
                    .map(|(key, value)| (key.to_owned(), value.to_string()))
                    .collect(),
            };
            let ttl = crate::transport::metadata::take_wire_ttl(&mut attributes)?;
            let sequence = attributes
                .remove("bizseqno")
                .or(attributes.remove("seqnum"));
            let unique_id = attributes.remove("uniqueid");
            reply.ttl = reply.ttl.or(ttl);
            if reply.biz_seq_no.is_none() {
                reply.biz_seq_no = sequence;
            }
            if reply.unique_id.is_none() {
                reply.unique_id = unique_id;
            }
            reply.delivery_context =
                Some(Box::new(crate::DeliveryContext::take_from(&mut attributes)));
            for (key, value) in attributes {
                if !is_reserved_property(&key) {
                    reply.props.entry(key).or_insert(value);
                }
            }
        }
        #[cfg(feature = "cloud_events")]
        Message::CloudEvent(reply) => {
            // A handler may return a previously received event. Remove all
            // foreign delivery routing, including keys absent on this request.
            let stale: Vec<_> = reply
                .iter_extensions()
                .filter(|(key, _)| is_delivery_property(key))
                .map(|(key, _)| key.to_owned())
                .collect();
            for key in stale {
                reply.remove_extension(&key);
            }
            match request {
                Message::EventMesh(request) => {
                    for (key, value) in native_reply_attributes(request) {
                        if is_delivery_property(&key) || reply.extension(&key).is_none() {
                            reply.set_extension(&key, value);
                        }
                    }
                }
                Message::CloudEvent(request) => {
                    for (key, value) in request.iter_extensions() {
                        if is_delivery_property(key) || reply.extension(key).is_none() {
                            reply.set_extension(key, value.clone());
                        }
                    }
                }
            }
        }
    }
    Ok(())
}

fn native_reply_attributes(
    request: &crate::EventMeshMessage,
) -> std::collections::HashMap<String, String> {
    let mut attributes = request.props.clone();
    if let Some(ttl) = request.ttl {
        attributes.insert("ttl".into(), ttl.to_string());
    }
    if let Some(sequence) = &request.biz_seq_no {
        attributes.insert("seqnum".into(), sequence.clone());
    }
    if let Some(unique_id) = &request.unique_id {
        attributes.insert("uniqueid".into(), unique_id.clone());
    }
    if let Some(context) = request.delivery_context() {
        attributes.extend(
            context
                .reply_attributes()
                .map(|(key, value)| (key.clone(), value.clone())),
        );
    }
    attributes
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
        .ok_or_else(|| Error::InvalidMessage("failed to decode TCP delivery".into()))?;
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
            return Err(Error::Tcp(format!("TCP handler panicked: {reason}")));
        }
    };
    // A handler may outlive its socket. Its result belongs to that old delivery,
    // so let the consumer observe the pending reconnect rather than use a new socket.
    if !connection.is_generation_active(delivery.generation).await {
        return Ok(());
    }
    if let Some(mut reply) = reply {
        if let Some(request) = request.as_ref() {
            inherit_request_metadata(&mut reply, request)?;
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

#[cfg(all(test, feature = "cloud_events"))]
mod tests {
    use super::*;
    use cloudevents::{AttributesReader, Data, EventBuilder, EventBuilderV10};

    #[test]
    fn native_request_keeps_cloud_event_reply_data_and_replaces_foreign_route() {
        let request = message::parse_message(&super::super::frame::PackageBody::Text(
            serde_json::json!({
                "topic": "requests", "body": "request",
                "properties": {"ttl": "5000", "cluster": "current", "custom": "request"}
            })
            .to_string(),
        ))
        .unwrap();
        let event = EventBuilderV10::new()
            .id("reply-id")
            .source("urn:reply")
            .ty("reply")
            .subject("replies")
            .data("application/cloudevents+json", vec![0, 255, 128])
            .extension("cluster", "foreign")
            .extension("req0group", "foreign-only")
            .extension("custom", "reply")
            .build()
            .unwrap();
        let mut reply = Message::CloudEvent(event.clone());

        inherit_request_metadata(&mut reply, &Message::EventMesh(request)).unwrap();
        let Message::CloudEvent(reply) = reply else {
            panic!("reply dialect changed")
        };
        assert_eq!(reply.data(), event.data());
        assert!(matches!(reply.data(), Some(Data::Binary(bytes)) if bytes == &[0, 255, 128]));
        assert_eq!(reply.id(), event.id());
        assert_eq!(reply.source(), event.source());
        assert_eq!(reply.datacontenttype(), event.datacontenttype());
        assert_eq!(reply.extension("cluster").unwrap().to_string(), "current");
        assert!(reply.extension("req0group").is_none());
        assert_eq!(reply.extension("custom").unwrap().to_string(), "reply");
        assert_eq!(reply.extension("ttl").unwrap().to_string(), "5000");
    }

    #[test]
    fn cloud_event_request_only_supplies_metadata_to_native_reply() {
        let request = EventBuilderV10::new()
            .id("request-id")
            .source("urn:request")
            .ty("request")
            // No subject or payload is needed to inherit routing metadata.
            .extension("cluster", "current")
            .extension("ttl", 5000)
            .extension("seqnum", "sequence")
            .extension("custom", "request")
            .build()
            .unwrap();
        let native = crate::EventMeshMessage::builder()
            .topic("replies")
            .content("reply payload")
            .data_content_type("text/plain")
            .prop("custom", "reply")
            .build()
            .unwrap();
        let mut reply = Message::EventMesh(native);

        inherit_request_metadata(&mut reply, &Message::CloudEvent(request)).unwrap();
        let Message::EventMesh(reply) = reply else {
            panic!("reply dialect changed")
        };
        assert_eq!(reply.content(), "reply payload");
        assert_eq!(reply.data_content_type(), Some("text/plain"));
        assert_eq!(reply.ttl_millis(), Some(5000));
        assert_eq!(reply.biz_seq_no(), Some("sequence"));
        assert_eq!(reply.get_prop("custom"), Some("reply"));
        assert_eq!(
            reply.delivery_context().unwrap().attribute("cluster"),
            Some("current")
        );
    }

    #[test]
    fn business_deliveries_require_an_explicit_supported_dialect() {
        let native = crate::EventMeshMessage::new("orders", "payload").unwrap();
        let mut package =
            message::build_message_package(&native, Command::AsyncMessageToClient).unwrap();
        assert!(decode_message(&package).is_some());
        package.header.properties.remove("protocoltype");
        assert!(decode_message(&package).is_none());
        package.header.set_property("protocoltype", "unknown");
        assert!(decode_message(&package).is_none());
    }
}
