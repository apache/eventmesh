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

//! TCP producer.

use std::sync::Arc;
use std::time::Duration;

use tracing::debug;

use crate::config::TcpClientConfig;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, PublishResponse};
use crate::transport::tcp::connection::TcpConnection;
use crate::transport::tcp::frame::{Command, UserAgent};
use crate::transport::tcp::message;
use crate::transport::Publisher;

/// TCP-based producer.
///
/// Created via [`TcpProducer::connect`], which opens a TCP connection, performs
/// the HELLO handshake (role = pub), and starts the background heartbeat.
/// Implements the [`Publisher`] trait.
pub struct TcpProducer {
    conn: Arc<TcpConnection>,
    config: TcpClientConfig,
}

impl TcpProducer {
    /// Connect to the EventMesh TCP endpoint and perform the HELLO handshake.
    ///
    /// The reconnect policy from the config controls automatic reconnection
    /// after I/O failures (enabled by default).
    pub async fn connect(config: TcpClientConfig) -> Result<Self> {
        let user_agent = UserAgent::from_identity(&config.identity, config.server_port, "pub");
        let conn = Arc::new(
            TcpConnection::connect(
                &config.server_addr,
                config.server_port,
                &user_agent,
                config.heartbeat_interval,
                config.timeout,
                config.reconnect.clone(),
            )
            .await?,
        );

        Ok(Self { conn, config })
    }

    /// Broadcast a message (fire-and-forget). Corresponds to Java
    /// `broadcast` which uses `send()` with `BROADCAST_MESSAGE_TO_SERVER`.
    pub async fn broadcast(&self, msg: EventMeshMessage) -> Result<()> {
        validate_publish(&msg)?;
        let pkg = message::build_message_package(&msg, Command::BroadcastMessageToServer)?;
        self.conn.send(pkg).await
    }

    /// Publish an OpenMessaging-style message using the interoperable native
    /// EventMesh TCP envelope.
    pub async fn publish_open_message(
        &self,
        message: crate::model::OpenMessage,
    ) -> Result<PublishResponse> {
        validate_publish(&message.to_event_mesh_message())?;
        let pkg = message::build_open_message_package(&message, Command::AsyncMessageToServer)?;
        let response = message::response_from_pkg(&self.conn.io(pkg, self.config.timeout).await?);
        ensure_success(response, "publish failed")
    }

    /// Broadcast an OpenMessaging-style message.
    pub async fn broadcast_open_message(&self, message: crate::model::OpenMessage) -> Result<()> {
        validate_publish(&message.to_event_mesh_message())?;
        let pkg = message::build_open_message_package(&message, Command::BroadcastMessageToServer)?;
        self.conn.send(pkg).await
    }

    /// Send an OpenMessaging-style request and wait for its reply.
    pub async fn request_reply_open_message(
        &self,
        message: crate::model::OpenMessage,
        timeout: Duration,
    ) -> Result<crate::model::OpenMessage> {
        validate_publish(&message.to_event_mesh_message())?;
        let pkg = message::build_open_message_package(&message, Command::RequestToServer)?;
        let response = self.conn.io(pkg, timeout).await?;
        ensure_success(
            message::response_from_pkg(&response),
            "request-reply failed",
        )?;
        message::parse_message(&response.body)
            .map(crate::model::OpenMessage::from_event_mesh_message)
            .ok_or_else(|| {
                EventMeshError::Codec(serde::de::Error::custom("failed to parse reply body"))
            })
    }

    /// Publish a native CloudEvent over TCP (requires the `cloud_events`
    /// feature).
    ///
    /// The event is serialized as CloudEvents JSON
    /// (`application/cloudevents+json`) with `protocoltype=cloudevents`,
    /// matching the Java runtime's TCP CloudEvents codec path.
    ///
    /// # `datacontenttype` requirement
    ///
    /// The event's `datacontenttype` **must** be `application/cloudevents+json`.
    /// The server uses this value to resolve the serializer on the downlink
    /// path; other values (e.g. `application/json`, `text/plain`) cause an
    /// NPE and the message is silently dropped before reaching consumers.
    ///
    /// ```ignore
    /// EventBuilderV10::new()
    ///     .id("1").source("...").ty("...").subject(topic)
    ///     .data("application/cloudevents+json", json!({"msg": "hi"}))
    ///     .build()?;
    /// ```
    #[cfg(feature = "cloud_events")]
    pub async fn publish_cloud_event(&self, event: cloudevents::Event) -> Result<PublishResponse> {
        use cloudevents::AttributesReader;
        validate_cloud_event(&event)?;
        let pkg = message::build_cloud_event_package(&event, Command::AsyncMessageToServer)?;
        debug!(topic = ?event.subject(), "publishing CloudEvent via TCP");

        let resp = self.conn.io(pkg, self.config.timeout).await?;
        let response = message::response_from_pkg(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response.message.unwrap_or_else(|| "publish failed".into()),
            });
        }
        Ok(response)
    }

    /// Broadcast a native CloudEvent (fire-and-forget, requires the
    /// `cloud_events` feature).
    ///
    /// See [`publish_cloud_event`](Self::publish_cloud_event) for the
    /// `datacontenttype` requirement.
    #[cfg(feature = "cloud_events")]
    pub async fn broadcast_cloud_event(&self, event: cloudevents::Event) -> Result<()> {
        validate_cloud_event(&event)?;
        let pkg = message::build_cloud_event_package(&event, Command::BroadcastMessageToServer)?;
        self.conn.send(pkg).await
    }

    /// Synchronous request/reply with a native CloudEvent (requires the
    /// `cloud_events` feature).
    ///
    /// See [`publish_cloud_event`](Self::publish_cloud_event) for the
    /// `datacontenttype` requirement.
    ///
    /// Sends the CloudEvent as `REQUEST_TO_SERVER` and waits for the reply.
    /// The reply is parsed as a CloudEvent if the server tags it
    /// `protocoltype=cloudevents`; otherwise it is parsed as a TCP-wire
    /// `EventMeshMessage` and converted to a CloudEvent for a uniform return
    /// type.
    #[cfg(feature = "cloud_events")]
    pub async fn request_reply_cloud_event(
        &self,
        event: cloudevents::Event,
        timeout: Duration,
    ) -> Result<cloudevents::Event> {
        use cloudevents::AttributesReader;
        validate_cloud_event(&event)?;
        let pkg = message::build_cloud_event_package(&event, Command::RequestToServer)?;
        debug!(topic = ?event.subject(), "request-reply CloudEvent via TCP");

        let resp = self.conn.io(pkg, timeout).await?;
        let response = message::response_from_pkg(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response
                    .message
                    .unwrap_or_else(|| "request-reply failed".into()),
            });
        }

        // Try CloudEvents first; fall back to EventMeshMessage → convert.
        if message::is_cloudevents(&resp) {
            message::parse_cloud_event(&resp.body).ok_or_else(|| {
                EventMeshError::Codec(serde::de::Error::custom(
                    "failed to parse CloudEvent reply body",
                ))
            })
        } else {
            let msg = message::parse_message(&resp.body).ok_or_else(|| {
                EventMeshError::Codec(serde::de::Error::custom("failed to parse reply body"))
            })?;
            message::message_to_cloud_event(&msg)
        }
    }

    /// Access the underlying connection (for testing or advanced use).
    pub fn connection(&self) -> &TcpConnection {
        &self.conn
    }

    /// Clone the shared connection for a background publisher-side handler.
    pub fn shared_connection(&self) -> Arc<TcpConnection> {
        Arc::clone(&self.conn)
    }

    /// Graceful shutdown.
    pub async fn shutdown(&self) {
        self.conn.shutdown().await;
    }

    /// Current config.
    pub fn config(&self) -> &TcpClientConfig {
        &self.config
    }
}

fn ensure_success(response: PublishResponse, fallback: &str) -> Result<PublishResponse> {
    if response.is_success() {
        Ok(response)
    } else {
        Err(EventMeshError::Server {
            code: response.code.unwrap_or(-1) as i32,
            message: response.message.unwrap_or_else(|| fallback.into()),
        })
    }
}

impl Publisher for TcpProducer {
    /// Publish a message and wait for the broker ACK.
    /// Uses `ASYNC_MESSAGE_TO_SERVER` + `io()` (mirrors the Java SDK).
    async fn publish(&self, message: EventMeshMessage) -> Result<PublishResponse> {
        validate_publish(&message)?;
        let pkg = super::message::build_message_package(&message, Command::AsyncMessageToServer)?;
        debug!(topic = ?message.topic, "publishing via TCP");

        let resp = self.conn.io(pkg, self.config.timeout).await?;
        let response = message::response_from_pkg(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response.message.unwrap_or_else(|| "publish failed".into()),
            });
        }
        Ok(response)
    }

    /// TCP has no batch semantics — returns [`EventMeshError::Unsupported`].
    async fn publish_batch(&self, _messages: Vec<EventMeshMessage>) -> Result<PublishResponse> {
        Err(EventMeshError::Unsupported(
            "batch publish is not supported over TCP".into(),
        ))
    }

    /// Synchronous request/reply. Uses `REQUEST_TO_SERVER` + `io()` and waits
    /// for the `RESPONSE_TO_CLIENT` push from the server.
    async fn request_reply(
        &self,
        message: EventMeshMessage,
        timeout: Duration,
    ) -> Result<EventMeshMessage> {
        validate_publish(&message)?;
        let pkg = super::message::build_message_package(&message, Command::RequestToServer)?;
        debug!(topic = ?message.topic, "request-reply via TCP");

        let resp = self.conn.io(pkg, timeout).await?;
        // Surface server-side failures (ACL/TPS/routing) before attempting to
        // parse the body. The runtime sets header.code on the RESPONSE_TO_CLIENT
        // reply via `new Header(cmd, OPStatus.<status>.getCode(), desc, seq)`.
        let response = message::response_from_pkg(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response
                    .message
                    .unwrap_or_else(|| "request-reply failed".into()),
            });
        }
        message::parse_message(&resp.body).ok_or_else(|| {
            EventMeshError::Codec(serde::de::Error::custom("failed to parse reply body"))
        })
    }
}

fn validate_publish(message: &EventMeshMessage) -> Result<()> {
    if message
        .topic
        .as_deref()
        .map(|t| t.trim().is_empty())
        .unwrap_or(true)
    {
        return Err(EventMeshError::InvalidMessage("topic is required".into()));
    }
    if message
        .content
        .as_deref()
        .map(|c| c.trim().is_empty())
        .unwrap_or(true)
    {
        return Err(EventMeshError::InvalidMessage("content is required".into()));
    }
    Ok(())
}

/// The `datacontenttype` value the Java runtime's TCP CloudEvents codec
/// requires. Any other value causes an NPE on the downlink path and the
/// message is silently dropped before reaching consumers.
#[cfg(feature = "cloud_events")]
const REQUIRED_CE_DATA_CONTENT_TYPE: &str = "application/cloudevents+json";

/// Validate that a CloudEvent has the `datacontenttype` required by the TCP
/// transport.
#[cfg(feature = "cloud_events")]
fn validate_cloud_event(event: &cloudevents::Event) -> Result<()> {
    use cloudevents::AttributesReader;
    match event.datacontenttype() {
        Some(REQUIRED_CE_DATA_CONTENT_TYPE) => Ok(()),
        Some(other) => Err(EventMeshError::InvalidMessage(format!(
            "TCP transport requires datacontenttype = \"{REQUIRED_CE_DATA_CONTENT_TYPE}\", \
             got \"{other}\" — other values cause the server to silently drop the message"
        ))),
        None => Err(EventMeshError::InvalidMessage(format!(
            "TCP transport requires datacontenttype = \"{REQUIRED_CE_DATA_CONTENT_TYPE}\", \
             but none is set — the server would silently drop the message"
        ))),
    }
}

#[cfg(all(test, feature = "cloud_events"))]
mod tests {
    use super::*;
    use cloudevents::{AttributesReader, EventBuilder, EventBuilderV10};

    fn make_event(datacontenttype: &str) -> cloudevents::Event {
        EventBuilderV10::new()
            .id("ce-1")
            .source("https://example.com")
            .ty("com.example.test")
            .subject("ce-topic")
            .data(datacontenttype, serde_json::json!({"hello": "world"}))
            .build()
            .expect("valid event")
    }

    #[test]
    fn accepts_required_content_type() {
        assert!(validate_cloud_event(&make_event(REQUIRED_CE_DATA_CONTENT_TYPE)).is_ok());
    }

    #[test]
    fn rejects_application_json() {
        let event = make_event("application/json");
        let err = validate_cloud_event(&event).unwrap_err();
        assert!(
            err.to_string().contains("application/cloudevents+json"),
            "error should mention required content type: {err}"
        );
    }

    #[test]
    fn rejects_missing_content_type() {
        let event = EventBuilderV10::new()
            .id("ce-1")
            .source("https://example.com")
            .ty("com.example.test")
            .subject("ce-topic")
            .build()
            .expect("valid event");

        assert!(event.datacontenttype().is_none());
        assert!(validate_cloud_event(&event).is_err());
    }
}
