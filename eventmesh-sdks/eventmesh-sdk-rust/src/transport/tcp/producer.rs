//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  The ASF
// licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//

//! TCP producer.

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
    conn: TcpConnection,
    config: TcpClientConfig,
}

impl TcpProducer {
    /// Connect to the EventMesh TCP endpoint and perform the HELLO handshake.
    pub async fn connect(config: TcpClientConfig) -> Result<Self> {
        let user_agent = UserAgent::from_identity(&config.identity, config.server_port, "pub");
        let conn = TcpConnection::connect(
            &config.server_addr,
            config.server_port,
            &user_agent,
            config.heartbeat_interval,
            config.timeout,
        )
        .await?;

        Ok(Self { conn, config })
    }

    /// Broadcast a message (fire-and-forget). Corresponds to Java
    /// `broadcast` which uses `send()` with `BROADCAST_MESSAGE_TO_SERVER`.
    pub async fn broadcast(&self, msg: EventMeshMessage) -> Result<()> {
        validate_publish(&msg)?;
        let pkg = message::build_message_package(&msg, Command::BroadcastMessageToServer)?;
        self.conn.send(pkg).await
    }

    /// Access the underlying connection (for testing or advanced use).
    pub fn connection(&self) -> &TcpConnection {
        &self.conn
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
