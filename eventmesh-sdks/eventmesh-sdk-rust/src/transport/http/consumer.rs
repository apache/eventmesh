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

//! Registration and heartbeat lifecycle for one HTTP consumer role.

use std::collections::HashMap;
use std::future::Future;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::{debug, warn};

use crate::config::ConsumerOptions;
use crate::error::{EventMeshError, Result};
use crate::model::{EventMeshProtocolType, PublishResponse};
use crate::subscription::{DeliveryType, Subscription};
use crate::transport::http::client::EventMeshHttpClient;
use crate::transport::http::codec::{self, uri};
use crate::transport::task::BackgroundTask;

const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(30);
const HEARTBEAT_INITIAL_DELAY: Duration = Duration::from_secs(10);

#[derive(Default, PartialEq, Eq)]
enum Phase {
    #[default]
    Running,
    Closing,
    Closed,
}

/// The lock spans remote operations and their local commits, including
/// heartbeats. A confirmed unsubscribe therefore precedes every later snapshot.
#[derive(Default)]
struct Registrations {
    phase: Phase,
    items: HashMap<(String, String), Subscription>,
}

impl Registrations {
    fn ensure_running(&self, shutdown: &CancellationToken) -> Result<()> {
        if self.phase != Phase::Running || shutdown.is_cancelled() {
            return Err(EventMeshError::ChannelClosed(
                "HTTP registration is closed".into(),
            ));
        }
        Ok(())
    }
}

/// Remote registrations and local heartbeat task owned by one HTTP consumer.
pub struct HttpConsumer {
    client: EventMeshHttpClient,
    options: ConsumerOptions,
    registrations: Arc<Mutex<Registrations>>,
    shutdown: CancellationToken,
    heartbeat_handle: Mutex<BackgroundTask<()>>,
}

impl HttpConsumer {
    /// Create a registration role using the client's shared connection pool.
    pub fn new(
        client: EventMeshHttpClient,
        options: &ConsumerOptions,
        shutdown_signal: Option<impl Future<Output = ()> + Send + 'static>,
    ) -> Result<Self> {
        options.validate()?;
        let runtime = tokio::runtime::Handle::try_current().map_err(|_| {
            EventMeshError::Config(
                "HTTP consumer construction requires an active Tokio runtime".into(),
            )
        })?;
        let registrations = Arc::new(Mutex::new(Registrations::default()));
        let shutdown = CancellationToken::new();
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
            options.clone(),
            registrations.clone(),
            shutdown.clone(),
        );
        Ok(Self {
            client,
            options: options.clone(),
            registrations,
            shutdown,
            heartbeat_handle: Mutex::new(BackgroundTask::new(heartbeat_handle)),
        })
    }

    /// Register webhook subscriptions and commit only confirmed registrations.
    pub async fn subscribe_webhook(
        &self,
        items: Vec<Subscription>,
        url: impl Into<String>,
    ) -> Result<PublishResponse> {
        if items.is_empty() {
            return Err(EventMeshError::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }
        if items
            .iter()
            .any(|item| item.delivery_type == DeliveryType::Sync)
        {
            return Err(EventMeshError::InvalidArgument(
                "HTTP transport does not support SYNC (request/reply) subscriptions; use the gRPC transport for request/reply".into(),
            ));
        }
        let url = url.into();
        let mut registrations = self.registrations.lock().await;
        registrations.ensure_running(&self.shutdown)?;
        let body = codec::encode_subscribe(&items, &url, self.options.group());
        let response = control_request(&self.client, codec::subscribe_code(), &body).await?;
        for item in items {
            registrations
                .items
                .insert((item.topic.clone(), url.clone()), item);
        }
        Ok(response)
    }

    /// Stop scheduling new heartbeats without unregistering remotely.
    pub fn request_shutdown(&self) {
        self.shutdown.cancel();
    }

    /// Wait for the heartbeat, including an already submitted request.
    pub async fn wait_for_shutdown(&self) -> Result<()> {
        let mut task = self.heartbeat_handle.lock().await;
        task.wait().await;
        self.shutdown.cancel();
        task.take_result().unwrap_or(Ok(())).map_err(|error| {
            EventMeshError::ChannelClosed(format!("HTTP heartbeat task panicked: {error}"))
        })
    }

    /// Unregister topics from their original callback URL.
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
        let topics: Vec<_> = items.iter().map(|item| item.topic.clone()).collect();
        let mut registrations = self.registrations.lock().await;
        registrations.ensure_running(&self.shutdown)?;
        if let Some(topic) = topics.iter().find(|topic| {
            !registrations
                .items
                .contains_key(&(topic.to_string(), url.clone()))
        }) {
            return Err(EventMeshError::InvalidArgument(format!(
                "topic {topic:?} is not subscribed to webhook URL {url:?}",
            )));
        }
        let body = codec::encode_unsubscribe(&topics, &url, self.options.group());
        let response = control_request(&self.client, codec::unsubscribe_code(), &body).await?;
        for topic in topics {
            registrations.items.remove(&(topic, url.clone()));
        }
        Ok(response)
    }

    /// Quiesce heartbeats before the final remote unregister. This lock waits
    /// for any submitted heartbeat to complete; cancelling a request would not
    /// undo its remote side effect. Failed registrations remain retryable by a
    /// subsequent close, while new subscriptions are rejected once closing starts.
    pub(crate) async fn unsubscribe_all(&self) -> Result<()> {
        let mut registrations = self.registrations.lock().await;
        registrations.phase = Phase::Closing;
        self.shutdown.cancel();
        let mut groups: HashMap<String, Vec<String>> = HashMap::new();
        for (topic, url) in registrations.items.keys() {
            groups.entry(url.clone()).or_default().push(topic.clone());
        }
        let mut first_error = None;
        for (url, topics) in groups {
            let body = codec::encode_unsubscribe(&topics, &url, self.options.group());
            match control_request(&self.client, codec::unsubscribe_code(), &body).await {
                Ok(_) => {
                    for topic in topics {
                        registrations.items.remove(&(topic, url.clone()));
                    }
                }
                Err(error) if first_error.is_none() => first_error = Some(error),
                Err(_) => {}
            }
        }
        registrations.phase = Phase::Closed;
        first_error.map_or(Ok(()), Err)
    }
}

impl Drop for HttpConsumer {
    fn drop(&mut self) {
        self.shutdown.cancel();
    }
}

async fn control_request(
    client: &EventMeshHttpClient,
    code: i32,
    body: &[(String, String)],
) -> Result<PublishResponse> {
    let config = client.config();
    let headers = codec::build_headers(
        code,
        EventMeshProtocolType::EventMeshMessage,
        config.identity(),
        config.credentials(),
    );
    let path = if code == codec::heartbeat_code() {
        uri::HEARTBEAT
    } else {
        uri::ROOT
    };
    let text = client
        .post_form(path, body, &headers, config.request_timeout())
        .await?;
    let response = codec::parse_response(&text)?;
    if !response.is_success() {
        return Err(EventMeshError::Server {
            code: response.code.unwrap_or(-1) as i32,
            message: response
                .message
                .unwrap_or_else(|| "HTTP control operation failed".into()),
        });
    }
    Ok(response)
}

fn spawn_heartbeat(
    runtime: &tokio::runtime::Handle,
    client: EventMeshHttpClient,
    options: ConsumerOptions,
    registrations: Arc<Mutex<Registrations>>,
    shutdown: CancellationToken,
) -> JoinHandle<()> {
    runtime.spawn(async move {
        tokio::select! {
            _ = tokio::time::sleep(HEARTBEAT_INITIAL_DELAY) => {}
            _ = shutdown.cancelled() => return,
        }
        loop {
            let state = tokio::select! {
                biased;
                _ = shutdown.cancelled() => return,
                state = registrations.lock() => state,
            };
            if state.ensure_running(&shutdown).is_err() {
                return;
            }
            let items: Vec<_> = state.items.keys().cloned().collect();
            if !items.is_empty() {
                let body = codec::encode_heartbeat(&items, options.group());
                match control_request(&client, codec::heartbeat_code(), &body).await {
                    Ok(_) => debug!("heartbeat ok: {} items", items.len()),
                    Err(error) => warn!(%error, "HTTP heartbeat failed"),
                }
            }
            drop(state);
            tokio::select! {
                _ = tokio::time::sleep(HEARTBEAT_INTERVAL) => {}
                _ = shutdown.cancelled() => return,
            }
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_consumer() -> HttpConsumer {
        let endpoints =
            crate::config::EndpointSet::new([
                crate::config::Endpoint::new("127.0.0.1", 10_105).unwrap()
            ])
            .unwrap();
        HttpConsumer::new(
            EventMeshHttpClient::new(crate::config::HttpConfig::new(endpoints)).unwrap(),
            &ConsumerOptions::new("test-group"),
            None::<std::future::Ready<()>>,
        )
        .unwrap()
    }

    #[tokio::test(start_paused = true)]
    async fn cancelled_join_preserves_http_heartbeat_panic() {
        let consumer = make_consumer();
        let (release, released) = tokio::sync::oneshot::channel::<()>();
        *consumer.heartbeat_handle.lock().await = BackgroundTask::new(tokio::spawn(async move {
            released.await.unwrap();
            panic!("heartbeat regression");
        }));
        for _ in 0..2 {
            assert!(
                tokio::time::timeout(Duration::from_secs(1), consumer.wait_for_shutdown())
                    .await
                    .is_err()
            );
            consumer.request_shutdown();
        }
        release.send(()).unwrap();
        let result = tokio::time::timeout(Duration::from_secs(1), consumer.wait_for_shutdown())
            .await
            .unwrap();
        assert!(matches!(result, Err(EventMeshError::ChannelClosed(message))
            if message.contains("heartbeat regression")));
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
