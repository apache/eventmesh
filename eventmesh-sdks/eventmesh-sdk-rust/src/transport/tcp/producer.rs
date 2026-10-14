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

use tokio::sync::Mutex;
use tokio_util::sync::CancellationToken;
use tracing::{debug, warn};

use crate::config::{ProducerOptions, TcpConfig};
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshMessage, PublishResponse};
use crate::transport::task::BackgroundTask;
use crate::transport::tcp::connection::TcpConnection;
use crate::transport::tcp::delivery::dispatch;
use crate::transport::tcp::frame::{Command, UserAgent};
use crate::transport::tcp::message;
use crate::transport::validation::validate_tcp_publish;
use crate::MessageHandler;

/// TCP-based producer.
///
/// Created via [`TcpProducer::connect`], which opens a TCP connection, performs
/// the HELLO handshake (role = pub), and starts the background heartbeat.
pub struct TcpProducer {
    conn: Arc<TcpConnection>,
    request_timeout: std::time::Duration,
    response_shutdown: CancellationToken,
    response_driver: Mutex<Option<BackgroundTask<Result<()>>>>,
}

impl TcpProducer {
    /// Connect to the EventMesh TCP endpoint and perform the HELLO handshake.
    ///
    /// The reconnect policy from the config controls automatic reconnection
    /// after I/O failures (enabled by default).
    pub async fn connect(config: TcpConfig, options: &ProducerOptions) -> Result<Self> {
        let user_agent = UserAgent::from_role(
            config.identity(),
            config.credentials(),
            options.group(),
            config.endpoint().port(),
            "pub",
        );
        let conn = Arc::new(
            TcpConnection::connect(
                &config.endpoint().authority_host(),
                config.endpoint().port(),
                &user_agent,
                config.heartbeat_interval(),
                config.connect_timeout(),
                config.control_timeout(),
                config.reconnect().clone(),
            )
            .await?,
        );
        let request_timeout = config.request_timeout();

        Ok(Self {
            conn,
            request_timeout,
            response_shutdown: CancellationToken::new(),
            response_driver: Mutex::new(None),
        })
    }

    /// Broadcast a message, waiting for its local socket write but no server
    /// ACK. Uses `BROADCAST_MESSAGE_TO_SERVER`, matching Java `broadcast`.
    pub async fn broadcast(&self, msg: EventMeshMessage) -> Result<()> {
        validate_tcp_publish(&msg)?;
        let pkg = message::build_message_package(&msg, Command::BroadcastMessageToServer)?;
        self.conn.send_and_flush(pkg).await
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
    /// Both the Java SDK's upload path and the Java runtime's TCP downlink path
    /// use this value to resolve the serializer for the whole CloudEvent.
    /// Other values (e.g. `application/json`, `text/plain`) therefore fail in
    /// the Java SDK before sending or fail during runtime delivery. This SDK
    /// returns [`EventMeshError::InvalidMessage`] before performing network
    /// I/O instead.
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
        let pkg = message::build_cloud_event_package(&event, Command::AsyncMessageToServer)?;
        debug!(topic = ?event.subject(), "publishing CloudEvent via TCP");

        let resp = self.conn.io(pkg, self.request_timeout).await?;
        let response = message::response_from_pkg(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response.message.unwrap_or_else(|| "publish failed".into()),
            });
        }
        Ok(response)
    }

    /// Broadcast a native CloudEvent, waiting for its local socket write but
    /// no server ACK (requires the `cloud_events` feature).
    ///
    /// See [`publish_cloud_event`](Self::publish_cloud_event) for the
    /// `datacontenttype` requirement.
    #[cfg(feature = "cloud_events")]
    pub async fn broadcast_cloud_event(&self, event: cloudevents::Event) -> Result<()> {
        let pkg = message::build_cloud_event_package(&event, Command::BroadcastMessageToServer)?;
        self.conn.send_and_flush(pkg).await
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

    /// Register the publisher push handler inside the transport lifecycle.
    pub(crate) async fn start_response_handler<H: MessageHandler>(&self, handler: H) -> Result<()> {
        let mut task = self.response_driver.lock().await;
        if task.is_some() {
            return Err(EventMeshError::InvalidArgument(
                "publisher handler is already registered".into(),
            ));
        }
        let mut inbound = self.conn.take_inbound_rx().await.ok_or_else(|| {
            EventMeshError::Tcp("publisher response receiver is already taken".into())
        })?;
        self.conn.enable_orphan_response_delivery();
        let connection = self.conn.clone();
        let shutdown = self.response_shutdown.clone();
        *task = Some(BackgroundTask::new(tokio::spawn(async move {
            let mut last_failure = None;
            let result = loop {
                let delivery = tokio::select! {
                    biased;
                    _ = shutdown.cancelled() => break Ok(()),
                    delivery = inbound.recv() => match delivery {
                        Some(delivery) => delivery,
                        None => break Err(last_failure.unwrap_or_else(|| {
                            EventMeshError::ChannelClosed("TCP publisher connection closed".into())
                        })),
                    },
                };
                if delivery.package.header.cmd != Command::ResponseToClient {
                    continue;
                }
                let handled = tokio::select! {
                    biased;
                    _ = shutdown.cancelled() => break Ok(()),
                    result = dispatch(&delivery, &connection, &handler, Command::ResponseToClientAck) => result,
                };
                if let Err(error) = handled {
                    warn!(%error, "publisher handler failed; recovering without ACK");
                    last_failure = Some(error);
                    connection.request_reconnect(delivery.generation).await;
                }
            };
            connection.shutdown().await;
            result
        })));
        Ok(())
    }

    /// Stop local response handling and await the socket and handler tasks.
    pub async fn shutdown(&self) {
        self.response_shutdown.cancel();
        self.conn.shutdown().await;
        let mut task = self.response_driver.lock().await;
        if let Some(task) = task.as_mut() {
            task.wait().await;
        }
    }

    /// Await terminal transport/handler errors without losing them on cancellation.
    pub(crate) async fn wait_for_shutdown(&self) -> Result<()> {
        let mut task = self.response_driver.lock().await;
        if let Some(task) = task.as_mut() {
            task.wait().await;
        }
        let connection_result = self.conn.wait_for_shutdown().await;
        let handler_result = match task.as_mut().and_then(BackgroundTask::take_result) {
            Some(result) => result.map_err(|error| {
                EventMeshError::Tcp(format!("TCP publisher response task failed: {error}"))
            })?,
            None => Ok(()),
        };
        handler_result.and(connection_result)
    }

    /// Publish a message and wait for the broker ACK.
    /// Uses `ASYNC_MESSAGE_TO_SERVER` + `io()` (mirrors the Java SDK).
    pub(crate) async fn publish(&self, message: EventMeshMessage) -> Result<PublishResponse> {
        validate_tcp_publish(&message)?;
        let pkg = super::message::build_message_package(&message, Command::AsyncMessageToServer)?;
        debug!(topic = ?message.topic, "publishing via TCP");

        let resp = self.conn.io(pkg, self.request_timeout).await?;
        let response = message::response_from_pkg(&resp);
        if !response.is_success() {
            return Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response.message.unwrap_or_else(|| "publish failed".into()),
            });
        }
        Ok(response)
    }

    /// Synchronous request/reply. Uses `REQUEST_TO_SERVER` + `io()` and waits
    /// for the `RESPONSE_TO_CLIENT` push from the server.
    pub(crate) async fn request_reply(
        &self,
        message: EventMeshMessage,
        timeout: Duration,
    ) -> Result<EventMeshMessage> {
        validate_tcp_publish(&message)?;
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

impl Drop for TcpProducer {
    fn drop(&mut self) {
        self.response_shutdown.cancel();
    }
}
