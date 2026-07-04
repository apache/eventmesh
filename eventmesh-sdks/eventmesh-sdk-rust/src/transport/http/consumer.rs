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

//! HTTP consumer.

use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::{debug, warn};

use crate::config::HttpClientConfig;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshProtocolType, PublishResponse, SubscriptionItem, SubscriptionType};
use crate::transport::http::client::EventMeshHttpClient;
use crate::transport::http::codec::{self, uri};
use crate::transport::Subscriber;

/// Heartbeat interval (mirrors the Java SDK: 30s).
const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(30);
/// Initial delay before the first heartbeat.
const HEARTBEAT_INITIAL_DELAY: Duration = Duration::from_secs(10);

/// A single subscription entry recorded locally for heartbeat/unsubscribe.
#[derive(Debug, Clone)]
struct SubscriptionEntry {
    #[allow(dead_code)]
    item: SubscriptionItem,
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
/// or via [`HttpConsumer::shutdown`].
pub struct HttpConsumer {
    client: EventMeshHttpClient,
    subscriptions: Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    shutdown: CancellationToken,
    heartbeat_handle: Mutex<Option<JoinHandle<()>>>,
}

impl HttpConsumer {
    /// Create a consumer. Spawns a background heartbeat task.
    pub fn new(config: HttpClientConfig) -> Result<Self> {
        let client = EventMeshHttpClient::new(config)?;
        let subscriptions = Arc::new(Mutex::new(HashMap::new()));
        let shutdown = CancellationToken::new();

        let heartbeat_handle =
            spawn_heartbeat(client.clone(), Arc::clone(&subscriptions), shutdown.clone());

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
        items: Vec<SubscriptionItem>,
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
        if items.iter().any(|i| i.r#type == SubscriptionType::SYNC) {
            return Err(EventMeshError::InvalidArgument(
                "HTTP transport does not support SYNC (request/reply) subscriptions; \
                 use the gRPC transport for request/reply"
                    .into(),
            ));
        }
        let config = self.client.config();
        let body = codec::encode_subscribe(&items, &url, &config.identity);
        let code = codec::subscribe_code();
        let headers = codec::build_headers(
            code,
            EventMeshProtocolType::EventMeshMessage,
            &config.identity,
        );
        let timeout = config.timeout;
        let text = self
            .client
            .post_form(uri::ROOT, &body, &headers, timeout)
            .await?;
        let response = codec::parse_response(&text)?;
        if response.is_success() {
            let mut guard = self.subscriptions.lock().await;
            for item in items {
                guard.insert(
                    item.topic.clone(),
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
        &self.client.config().identity.consumer_group
    }

    /// Explicitly shut down: cancel heartbeat and await its exit.
    pub async fn shutdown(&self) {
        self.shutdown.cancel();
        if let Some(handle) = self.heartbeat_handle.lock().await.take() {
            let _ = handle.await;
        }
    }
}

impl Subscriber for HttpConsumer {
    async fn subscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        // Without an explicit URL, we can't register a webhook. Return an
        // error guiding the user to `subscribe_webhook`.
        let _ = &items;
        Err(EventMeshError::InvalidArgument(
            "HTTP transport requires subscribe_webhook(items, url); \
             use subscribe_webhook with a webhook URL"
                .into(),
        ))
    }

    async fn unsubscribe(&self, items: Vec<SubscriptionItem>) -> Result<PublishResponse> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "unsubscribe items must not be empty".into(),
            ));
        }
        // Group topics by their registered webhook URL. The runtime removes a
        // subscription only when BOTH topic AND url match, and the wire format
        // carries a single `url` per request — so topics registered under
        // different URLs must be sent in separate requests.
        let url_groups: HashMap<String, Vec<String>> = {
            let guard = self.subscriptions.lock().await;
            items.iter().fold(HashMap::new(), |mut acc, i| {
                let url = guard
                    .get(&i.topic)
                    .map(|e| e.url.clone())
                    .unwrap_or_default();
                acc.entry(url).or_default().push(i.topic.clone());
                acc
            })
        };
        let config = self.client.config();
        let code = codec::unsubscribe_code();
        let headers = codec::build_headers(
            code,
            EventMeshProtocolType::EventMeshMessage,
            &config.identity,
        );
        let timeout = config.timeout;
        let mut last_response = None;
        for (url, topics) in &url_groups {
            let body = codec::encode_unsubscribe(topics, url, &config.identity);
            let text = self
                .client
                .post_form(uri::ROOT, &body, &headers, timeout)
                .await?;
            let response = codec::parse_response(&text)?;
            if response.is_success() {
                let mut guard = self.subscriptions.lock().await;
                for topic in topics {
                    guard.remove(topic);
                }
                last_response = Some(response);
            } else {
                return Err(EventMeshError::Server {
                    code: response.code.unwrap_or(-1) as i32,
                    message: response
                        .message
                        .unwrap_or_else(|| "unsubscribe failed".into()),
                });
            }
        }
        last_response
            .ok_or_else(|| EventMeshError::InvalidArgument("no unsubscribe groups to send".into()))
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
    client: EventMeshHttpClient,
    subscriptions: Arc<Mutex<HashMap<String, SubscriptionEntry>>>,
    shutdown: CancellationToken,
) -> JoinHandle<()> {
    tokio::spawn(async move {
        tokio::select! {
            _ = tokio::time::sleep(HEARTBEAT_INITIAL_DELAY) => {}
            _ = shutdown.cancelled() => return,
        }
        loop {
            let items: Vec<(String, String)> = subscriptions
                .lock()
                .await
                .iter()
                .map(|(t, e)| (t.clone(), e.url.clone()))
                .collect();
            if !items.is_empty() {
                let config = client.config();
                let body = codec::encode_heartbeat(&items, &config.identity);
                let code = codec::heartbeat_code();
                let headers = codec::build_headers(
                    code,
                    EventMeshProtocolType::EventMeshMessage,
                    &config.identity,
                );
                let timeout = config.timeout;
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
    use crate::model::SubscriptionMode;

    fn make_consumer() -> HttpConsumer {
        HttpConsumer::new(HttpClientConfig::default()).unwrap()
    }

    #[tokio::test]
    async fn subscribe_webhook_rejects_sync_only() {
        let consumer = make_consumer();
        let item = SubscriptionItem::new(
            "sync-topic",
            SubscriptionMode::CLUSTERING,
            SubscriptionType::SYNC,
        );
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
            SubscriptionItem::new(
                "async-topic",
                SubscriptionMode::CLUSTERING,
                SubscriptionType::ASYNC,
            ),
            SubscriptionItem::new(
                "sync-topic",
                SubscriptionMode::CLUSTERING,
                SubscriptionType::SYNC,
            ),
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
