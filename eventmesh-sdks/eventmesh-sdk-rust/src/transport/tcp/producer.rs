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
use tracing::warn;

use crate::config::{ProducerOptions, TcpConfig};
use crate::error::{Error, Result};
use crate::message::{Message, PublishReceipt};
use crate::transport::task::BackgroundTask;
use crate::transport::tcp::connection::TcpConnection;
use crate::transport::tcp::delivery::{decode_message, dispatch};
use crate::transport::tcp::frame::{Command, UserAgent};
use crate::transport::tcp::message;
use crate::transport::validation::validate_tcp_publish;
use crate::MessageHandler;

/// TCP-based producer.
///
/// Created via [`crate::tcp::TcpClient::producer`], which opens a TCP connection, performs
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
    pub(crate) async fn connect(config: TcpConfig, options: &ProducerOptions) -> Result<Self> {
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

    /// Publish one message and wait for the server acknowledgement.
    ///
    /// CloudEvents require `datacontenttype = application/cloudevents+json`:
    /// the Java TCP codec uses this value to select its event serializer.
    pub async fn publish(&self, message: Message) -> Result<PublishReceipt> {
        let package = encode_outbound(&message, Command::AsyncMessageToServer)?;
        let response = self.conn.io(package, self.request_timeout).await?;
        check_response(&response)?;
        Ok(PublishReceipt::from_response(message::response_from_pkg(
            &response,
        )))
    }

    /// Broadcast a message, waiting for its local socket write without a server ACK.
    ///
    /// Queueing and writing share the TCP control timeout. A write failure or
    /// timeout does not establish whether the server received the message.
    /// CloudEvents have the same content-type constraint as [`Self::publish`].
    pub async fn broadcast(&self, message: Message) -> Result<()> {
        let package = encode_outbound(&message, Command::BroadcastMessageToServer)?;
        self.conn.send_and_flush(package).await
    }

    /// Send a message and await its reply using the configured request timeout.
    ///
    /// The returned variant follows the reply's wire protocol, independently
    /// of the request variant. CloudEvents have the same content-type
    /// constraint as [`Self::publish`].
    pub async fn request_reply(&self, message: Message) -> Result<Message> {
        self.request_reply_with_timeout(message, self.request_timeout)
            .await
    }

    /// Send a message and await its reply within a per-operation timeout.
    ///
    /// The reply preserves its wire dialect and payload without converting
    /// between native messages and CloudEvents. A zero timeout is rejected.
    pub async fn request_reply_with_timeout(
        &self,
        message: Message,
        timeout: Duration,
    ) -> Result<Message> {
        if timeout.is_zero() {
            return Err(Error::InvalidArgument(
                "request/reply timeout must be greater than zero".into(),
            ));
        }
        let package = encode_outbound(&message, Command::RequestToServer)?;
        let response = self.conn.io(package, timeout).await?;
        check_response(&response)?;
        decode_message(&response)
            .ok_or_else(|| Error::InvalidMessage("failed to decode TCP reply".into()))
    }

    /// Register the publisher push handler inside the transport lifecycle.
    pub(crate) async fn start_response_handler<H: MessageHandler>(&self, handler: H) -> Result<()> {
        let mut task = self.response_driver.lock().await;
        if task.is_some() {
            return Err(Error::InvalidArgument(
                "publisher handler is already registered".into(),
            ));
        }
        let mut inbound = self
            .conn
            .take_inbound_rx()
            .await
            .ok_or_else(|| Error::Tcp("publisher response receiver is already taken".into()))?;
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
                            Error::ChannelClosed("TCP publisher connection closed".into())
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
    pub async fn join(&self) -> Result<()> {
        let mut task = self.response_driver.lock().await;
        if let Some(task) = task.as_mut() {
            task.wait().await;
        }
        let connection_result = self.conn.wait_for_shutdown().await;
        let handler_result = match task.as_mut().and_then(BackgroundTask::take_result) {
            Some(result) => result.map_err(|error| {
                Error::Tcp(format!("TCP publisher response task failed: {error}"))
            })?,
            None => Ok(()),
        };
        handler_result.and(connection_result)
    }
}

fn encode_outbound(message: &Message, command: Command) -> Result<super::frame::Package> {
    match message {
        Message::EventMesh(message) => {
            validate_tcp_publish(message)?;
            message::build_message_package(message, command)
        }
        #[cfg(feature = "cloud_events")]
        Message::CloudEvent(event) => message::build_cloud_event_package(event, command),
    }
}

fn check_response(package: &super::frame::Package) -> Result<()> {
    if package.header.code != 0 {
        return Err(Error::Server {
            code: package.header.code,
            message: package
                .header
                .desc
                .clone()
                .unwrap_or_else(|| "TCP operation failed".into()),
        });
    }
    Ok(())
}

impl Drop for TcpProducer {
    fn drop(&mut self) {
        self.response_shutdown.cancel();
    }
}
