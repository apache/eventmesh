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

//! HTTP consumer.

use std::collections::HashMap;
use std::future::Future;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::{debug, warn};

use crate::config::{ConsumerOptions, HttpConfig};
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshProtocolType, PublishResponse};
use crate::subscription::{DeliveryType, Subscription};
use crate::transport::http::client::{EventMeshHttpClient, HttpRole};
use crate::transport::http::codec::{self, uri};

/// Heartbeat interval (mirrors the Java SDK: 30s).
const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(30);
/// Initial delay before the first heartbeat.
const HEARTBEAT_INITIAL_DELAY: Duration = Duration::from_secs(10);

/// A single subscription entry recorded locally for heartbeat/unsubscribe.
#[derive(Debug, Clone)]
struct SubscriptionEntry {
    item: Subscription,
    url: String,
}

/// HTTP-based consumer.
///
/// The consumer registers a webhook URL with the EventMesh runtime and sends
/// periodic heartbeats. The runtime pushes messages to that URL — serve it
/// either with the built-in [`WebhookServer`](crate::transport::http::WebhookServer)
/// or your own HTTP endpoint built on the
/// [`codec`](crate::transport::http::codec) helpers.
///
/// A background heartbeat task is spawned on construction and stopped on drop
/// or via [`HttpConsumer::shutdown`] / [`HttpConsumer::wait_for_shutdown`].
pub struct HttpConsumer {
    client: EventMeshHttpClient,
    subscriptions: Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    shutdown: CancellationToken,
    heartbeat_handle: Mutex<Option<JoinHandle<()>>>,
}

impl HttpConsumer {
    /// Create a consumer. Spawns a background heartbeat task.
    ///
    /// `shutdown_signal` is an optional future whose resolution triggers
    /// graceful shutdown of the heartbeat.  When omitted, shutdown can only be
    /// initiated by [`shutdown`](Self::shutdown) or drop.
    pub fn new(
        config: HttpConfig,
        options: &ConsumerOptions,
        shutdown_signal: Option<impl Future<Output = ()> + Send + 'static>,
    ) -> Result<Self> {
        let runtime = tokio::runtime::Handle::try_current().map_err(|_| {
            EventMeshError::Config(
                "HTTP consumer construction requires an active Tokio runtime".into(),
            )
        })?;
        let client = EventMeshHttpClient::new(HttpRole::consumer(config, options)?)?;
        let subscriptions = Arc::new(Mutex::new(HashMap::new()));
        let shutdown = CancellationToken::new();

        // Signal watcher.
        if let Some(signal) = shutdown_signal {
            let token = shutdown.clone();
            runtime.spawn(async move {
                tokio::select! {
                    _ = signal => token.cancel(),
                    _ = token.cancelled() => {}
                }
            });
        }

        let heartbeat_handle = spawn_heartbeat(
            &runtime,
            client.clone(),
            Arc::clone(&subscriptions),
            shutdown.clone(),
        );

        Ok(Self {
            client,
            subscriptions,
            shutdown,
            heartbeat_handle: Mutex::new(Some(heartbeat_handle)),
        })
    }

    /// Subscribe to topics with a webhook URL. The EventMesh runtime will
    /// POST messages to `url`.
    pub async fn subscribe_webhook(
        &self,
        items: Vec<Subscription>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        let url = url.into();
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }
        // HTTP SYNC (request/reply) subscriptions are not supported. The
        // runtime's CloudEvents protocol adaptor cannot deserialize a
        // REPLY_MESSAGE (code 301) request — its switch only handles
        // MSG_SEND_SYNC/MSG_SEND_ASYNC/MSG_BATCH_SEND* and throws on anything
        // else — so there is no wire path to deliver listener replies back to
        // the original requester. Use the gRPC transport for request/reply.
        if items.iter().any(|i| i.delivery_type == DeliveryType::Sync) {
            return Err(EventMeshError::InvalidArgument(
                "HTTP transport does not support SYNC (request/reply) subscriptions; \
                 use the gRPC transport for request/reply"
                    .into(),
            ));
        }
        let role = self.client.role();
        let config = role.config();
        let body = codec::encode_subscribe(&items, &url, role.consumer_group());
        let code = codec::subscribe_code();
        let headers = codec::build_headers(
            code,
            EventMeshProtocolType::EventMeshMessage,
            config.identity(),
            config.credentials(),
        );
        let timeout = role.timeout();
        let text = self
            .client
            .post_form(uri::ROOT, &body, &headers, timeout)
            .await?;
        let response = codec::parse_response(&text)?;
        if response.is_success() {
            let mut guard = self.subscriptions.lock().await;
            for item in items {
                guard.insert(
                    (item.topic.clone(), url.clone()),
                    SubscriptionEntry {
                        item,
                        url: url.clone(),
                    },
                );
            }
            Ok(response)
        } else {
            Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response
                    .message
                    .unwrap_or_else(|| "subscribe failed".into()),
            })
        }
    }

    /// Current consumer group.
    pub fn consumer_group(&self) -> &str {
        self.client.role().consumer_group()
    }

    /// Signal the heartbeat task to stop.
    pub fn request_shutdown(&self) {
        self.shutdown.cancel();
    }

    /// Signal shutdown and wait for the heartbeat task to finish.
    pub async fn shutdown(&self) -> Result<()> {
        self.request_shutdown();
        self.wait_for_shutdown().await
    }

    /// Block until the shutdown signal fires or the heartbeat task exits.
    ///
    /// If no shutdown signal was provided at construction time, this blocks
    /// until the heartbeat task exits (which typically only happens on
    /// explicit shutdown or drop).
    pub async fn wait_for_shutdown(&self) -> Result<()> {
        let handle = self.heartbeat_handle.lock().await.take();
        match handle {
            Some(mut handle) => {
                tokio::select! {
                    _ = self.shutdown.cancelled() => {
                        handle.await.map_err(|error| EventMeshError::ChannelClosed(
                            format!("HTTP heartbeat task panicked: {error}")
                        ))
                    }
                    result = &mut handle => {
                        self.shutdown.cancel();
                        result.map_err(|error| EventMeshError::ChannelClosed(
                            format!("HTTP heartbeat task panicked: {error}")
                        ))
                    }
                }
            }
            None => {
                self.shutdown.cancelled().await;
                Ok(())
            }
        }
    }
}

impl HttpConsumer {
    /// Unsubscribe topics from one webhook URL.
    pub async fn unsubscribe(
        &self,
        items: Vec<Subscription>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "unsubscribe items must not be empty".into(),
            ));
        }
        let url = url.into();
        let topics: Vec<String> = items.iter().map(|item| item.topic.clone()).collect();
        {
            let guard = self.subscriptions.lock().await;
            if let Some(topic) = topics
                .iter()
                .find(|topic| !guard.contains_key(&(topic.to_string(), url.clone())))
            {
                return Err(EventMeshError::InvalidArgument(format!(
                    "topic {topic:?} is not subscribed to webhook URL {url:?}"
                )));
            }
        }
        let role = self.client.role();
        let config = role.config();
        let code = codec::unsubscribe_code();
        let headers = codec::build_headers(
            code,
            EventMeshProtocolType::EventMeshMessage,
            config.identity(),
            config.credentials(),
        );
        let timeout = role.timeout();
        let body = codec::encode_unsubscribe(&topics, &url, role.consumer_group());
        let text = self
            .client
            .post_form(uri::ROOT, &body, &headers, timeout)
            .await?;
        let response = codec::parse_response(&text)?;
        if response.is_success() {
            let mut guard = self.subscriptions.lock().await;
            for topic in topics {
                guard.remove(&(topic, url.clone()));
            }
            Ok(response)
        } else {
            Err(EventMeshError::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response
                    .message
                    .unwrap_or_else(|| "unsubscribe failed".into()),
            })
        }
    }

    /// Remove every locally tracked webhook registration.
    ///
    /// Registrations are grouped by callback URL because the HTTP protocol
    /// requires the original URL when unsubscribing.
    pub(crate) async fn unsubscribe_all(&self) -> Result<()> {
        let registrations = {
            let guard = self.subscriptions.lock().await;
            let mut grouped: HashMap<String, Vec<Subscription>> = HashMap::new();
            for entry in guard.values() {
                grouped
                    .entry(entry.url.clone())
                    .or_default()
                    .push(entry.item.clone());
            }
            grouped
        };

        let mut first_error = None;
        for (url, items) in registrations {
            if let Err(error) = self.unsubscribe(items, url).await {
                if first_error.is_none() {
                    first_error = Some(error);
                }
            }
        }
        first_error.map_or(Ok(()), Err)
    }
}

impl Drop for HttpConsumer {
    fn drop(&mut self) {
        self.shutdown.cancel();
        if let Ok(mut guard) = self.heartbeat_handle.try_lock() {
            if let Some(handle) = guard.take() {
                handle.abort();
            }
        }
    }
}

/// Spawn the heartbeat loop. Reads the consumer's subscriptions each tick and
/// reports them to the broker.
fn spawn_heartbeat(
    runtime: &tokio::runtime::Handle,
    client: EventMeshHttpClient,
    subscriptions: Arc<Mutex<HashMap<(String, String), SubscriptionEntry>>>,
    shutdown: CancellationToken,
) -> JoinHandle<()> {
    runtime.spawn(async move {
        tokio::select! {
            _ = tokio::time::sleep(HEARTBEAT_INITIAL_DELAY) => {}
            _ = shutdown.cancelled() => return,
        }
        loop {
            let items: Vec<(String, String)> = subscriptions
                .lock()
                .await
                .iter()
                .map(|((topic, url), _entry)| (topic.clone(), url.clone()))
                .collect();
            if !items.is_empty() {
                let role = client.role();
                let config = role.config();
                let body = codec::encode_heartbeat(&items, role.consumer_group());
                let code = codec::heartbeat_code();
                let headers = codec::build_headers(
                    code,
                    EventMeshProtocolType::EventMeshMessage,
                    config.identity(),
                    config.credentials(),
                );
                let timeout = role.timeout();
                match client
                    .post_form(uri::HEARTBEAT, &body, &headers, timeout)
                    .await
                {
                    Ok(text) => {
                        if let Ok(resp) = codec::parse_response(&text) {
                            debug!("heartbeat ok: {} items", items.len());
                            if !resp.is_success() {
                                warn!("heartbeat non-success: {:?}", resp);
                            }
                        }
                    }
                    Err(e) => warn!("heartbeat failed: {e}"),
                }
            } else {
                debug!("heartbeat tick: no subscriptions yet");
            }
            tokio::select! {
                _ = tokio::time::sleep(HEARTBEAT_INTERVAL) => {}
                _ = shutdown.cancelled() => break,
            }
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::subscription::DeliveryMode;

    fn make_consumer() -> HttpConsumer {
        let endpoints =
            crate::config::EndpointSet::new([
                crate::config::Endpoint::new("127.0.0.1", 10_105).unwrap()
            ])
            .unwrap();
        HttpConsumer::new(
            HttpConfig::new(endpoints),
            &ConsumerOptions::new("test-group"),
            None::<std::future::Ready<()>>,
        )
        .unwrap()
    }

    #[tokio::test]
    async fn subscribe_webhook_rejects_sync_only() {
        let consumer = make_consumer();
        let item = Subscription::new("sync-topic").with_delivery_type(DeliveryType::Sync);
        let result = consumer
            .subscribe_webhook(vec![item], "http://localhost:9999/cb")
            .await;
        assert!(result.is_err());
        match result.unwrap_err() {
            EventMeshError::InvalidArgument(msg) => {
                assert!(msg.contains("SYNC"), "error should mention SYNC: {msg}");
            }
            other => panic!("expected InvalidArgument, got {other:?}"),
        }
    }

    #[tokio::test]
    async fn subscribe_webhook_rejects_mixed_sync_and_async() {
        let consumer = make_consumer();
        let items = vec![
            Subscription::new("async-topic"),
            Subscription::new("sync-topic").with_delivery_type(DeliveryType::Sync),
        ];
        let result = consumer
            .subscribe_webhook(items, "http://localhost:9999/cb")
            .await;
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            EventMeshError::InvalidArgument(_)
        ));
    }

    #[tokio::test]
    async fn subscribe_webhook_rejects_empty_items() {
        let consumer = make_consumer();
        let result = consumer
            .subscribe_webhook(vec![], "http://localhost:9999/cb")
            .await;
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            EventMeshError::InvalidArgument(_)
        ));
    }
}
