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
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use tracing::{debug, warn};

use crate::common::status_code::RequestCode;
use crate::config::ConsumerOptions;
use crate::error::{Error, Result};
use crate::model::EventMeshProtocolType;
use crate::subscription::{DeliveryType, Subscription};
use crate::transport::http::client::HttpClient;
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
            return Err(Error::ChannelClosed("HTTP registration is closed".into()));
        }
        Ok(())
    }
}

/// Registration and heartbeat manager for an application-owned webhook URL.
pub struct WebhookRegistration {
    client: HttpClient,
    options: ConsumerOptions,
    registrations: Arc<Mutex<Registrations>>,
    shutdown: CancellationToken,
    heartbeat_handle: Mutex<BackgroundTask<()>>,
}

impl WebhookRegistration {
    /// Create a registration role using the client's shared connection pool.
    pub(crate) fn new(
        client: HttpClient,
        options: ConsumerOptions,
        shutdown: CancellationToken,
    ) -> Result<Self> {
        options.validate()?;
        let runtime = tokio::runtime::Handle::try_current().map_err(|_| {
            Error::Config("HTTP consumer construction requires an active Tokio runtime".into())
        })?;
        let registrations = Arc::new(Mutex::new(Registrations::default()));
        let heartbeat_handle = spawn_heartbeat(
            &runtime,
            client.clone(),
            options.clone(),
            registrations.clone(),
            shutdown.clone(),
        );
        Ok(Self {
            client,
            options,
            registrations,
            shutdown,
            heartbeat_handle: Mutex::new(BackgroundTask::new(heartbeat_handle)),
        })
    }

    /// Register a subscription to an application-owned callback URL.
    pub async fn subscribe(
        &self,
        subscription: Subscription,
        webhook_url: impl Into<String>,
    ) -> Result<()> {
        self.subscribe_all(vec![subscription], webhook_url.into())
            .await
    }

    /// Register webhook subscriptions and commit only confirmed registrations.
    pub(crate) async fn subscribe_all(&self, items: Vec<Subscription>, url: String) -> Result<()> {
        if items.is_empty() {
            return Err(Error::InvalidArgument(
                "subscription items must not be empty".into(),
            ));
        }
        if items
            .iter()
            .any(|item| item.delivery_type == DeliveryType::Sync)
        {
            return Err(Error::Unsupported(
                "HTTP request/reply subscriptions".into(),
            ));
        }
        for item in &items {
            item.validate()?;
        }
        crate::webhook::validate_webhook_url(&url)?;
        let mut registrations = self.registrations.lock().await;
        registrations.ensure_running(&self.shutdown)?;
        let body = codec::encode_subscribe(&items, &url, self.options.group());
        control_request(&self.client, RequestCode::SUBSCRIBE, &body).await?;
        for item in items {
            registrations
                .items
                .insert((item.topic.clone(), url.clone()), item);
        }
        Ok(())
    }

    /// Signal the heartbeat task to stop without unregistering remotely.
    pub fn shutdown(&self) {
        self.shutdown.cancel();
    }

    /// Wait for the heartbeat task to stop and report task failure.
    ///
    /// Cancelling this wait preserves task ownership and pending results. Call
    /// `join()` again to finish waiting, or drop the registration to abort its task.
    pub async fn join(&self) -> Result<()> {
        let mut task = self.heartbeat_handle.lock().await;
        task.wait().await;
        self.shutdown.cancel();
        task.take_result()
            .unwrap_or(Ok(()))
            .map_err(|error| Error::ChannelClosed(format!("HTTP heartbeat task panicked: {error}")))
    }

    /// Remove a registration using the same URL supplied to [`subscribe`](Self::subscribe).
    pub async fn unsubscribe(
        &self,
        subscription: Subscription,
        webhook_url: impl Into<String>,
    ) -> Result<()> {
        subscription.validate()?;
        let url = webhook_url.into();
        crate::webhook::validate_webhook_url(&url)?;
        let mut registrations = self.registrations.lock().await;
        registrations.ensure_running(&self.shutdown)?;
        let key = (subscription.topic, url.clone());
        if !registrations.items.contains_key(&key) {
            return Err(Error::InvalidArgument(format!(
                "topic {:?} is not subscribed to webhook URL {url:?}",
                key.0,
            )));
        }
        let body =
            codec::encode_unsubscribe(std::slice::from_ref(&key.0), &url, self.options.group());
        control_request(&self.client, RequestCode::UNSUBSCRIBE, &body).await?;
        registrations.items.remove(&key);
        Ok(())
    }

    /// Drain a submitted heartbeat, stop scheduling, unregister, and join.
    ///
    /// Subscription changes are rejected once closing begins. Failed remote
    /// unregisters remain tracked and can be retried by calling `close` again.
    pub async fn close(&self) -> Result<()> {
        let unregister_result = self.unsubscribe_all().await;
        self.shutdown();
        let join_result = self.join().await;
        combine_cleanup_results(unregister_result, join_result)
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
            match control_request(&self.client, RequestCode::UNSUBSCRIBE, &body).await {
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

impl Drop for WebhookRegistration {
    fn drop(&mut self) {
        self.shutdown.cancel();
    }
}

pub(crate) fn combine_cleanup_results(operation: Result<()>, shutdown: Result<()>) -> Result<()> {
    match (operation, shutdown) {
        (Ok(()), Ok(())) => Ok(()),
        (Err(error), Ok(())) | (Ok(()), Err(error)) => Err(error),
        (Err(operation), Err(shutdown)) => Err(Error::Cleanup {
            operation: Box::new(operation),
            shutdown: Box::new(shutdown),
        }),
    }
}

async fn control_request(client: &HttpClient, code: i32, body: &[(String, String)]) -> Result<()> {
    let config = client.config();
    let headers = codec::build_headers(
        code,
        EventMeshProtocolType::EventMeshMessage,
        config.identity(),
        config.credentials(),
    );
    let path = if code == RequestCode::HEARTBEAT {
        uri::HEARTBEAT
    } else {
        uri::ROOT
    };
    let text = client
        .post_form(path, body, &headers, config.request_timeout())
        .await?;
    let response = codec::parse_response(&text)?;
    if !response.is_success() {
        return Err(Error::Server {
            code: response.code.unwrap_or(-1) as i32,
            message: response
                .message
                .unwrap_or_else(|| "HTTP control operation failed".into()),
        });
    }
    Ok(())
}

fn spawn_heartbeat(
    runtime: &tokio::runtime::Handle,
    client: HttpClient,
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
                match control_request(&client, RequestCode::HEARTBEAT, &body).await {
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

    fn make_registration() -> WebhookRegistration {
        let endpoints =
            crate::config::EndpointSet::new([
                crate::config::Endpoint::new("127.0.0.1", 10_105).unwrap()
            ])
            .unwrap();
        WebhookRegistration::new(
            HttpClient::new(crate::config::HttpConfig::new(endpoints)).unwrap(),
            ConsumerOptions::new("test-group"),
            CancellationToken::new(),
        )
        .unwrap()
    }

    #[tokio::test(start_paused = true)]
    async fn cancelled_join_preserves_http_heartbeat_panic() {
        let consumer = make_registration();
        let (release, released) = tokio::sync::oneshot::channel::<()>();
        *consumer.heartbeat_handle.lock().await = BackgroundTask::new(tokio::spawn(async move {
            released.await.unwrap();
            panic!("heartbeat regression");
        }));
        for _ in 0..2 {
            assert!(
                tokio::time::timeout(Duration::from_secs(1), consumer.join())
                    .await
                    .is_err()
            );
            consumer.shutdown();
        }
        release.send(()).unwrap();
        let result = tokio::time::timeout(Duration::from_secs(1), consumer.join())
            .await
            .unwrap();
        assert!(matches!(result, Err(Error::ChannelClosed(message))
            if message.contains("heartbeat regression")));
    }

    #[tokio::test]
    async fn registration_rejects_sync_subscription() {
        let consumer = make_registration();
        let item = Subscription::new("sync-topic").with_delivery_type(DeliveryType::Sync);
        let result = consumer.subscribe(item, "http://localhost:9999/cb").await;
        assert!(result.is_err());
        match result.unwrap_err() {
            Error::Unsupported(msg) => {
                assert!(msg.contains("request/reply"));
            }
            other => panic!("expected Unsupported, got {other:?}"),
        }
    }

    #[tokio::test]
    async fn registration_rejects_mixed_sync_and_async() {
        let consumer = make_registration();
        let items = vec![
            Subscription::new("async-topic"),
            Subscription::new("sync-topic").with_delivery_type(DeliveryType::Sync),
        ];
        let result = consumer
            .subscribe_all(items, "http://localhost:9999/cb".into())
            .await;
        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), Error::Unsupported(_)));
    }

    #[tokio::test]
    async fn registration_rejects_empty_items() {
        let consumer = make_registration();
        let result = consumer
            .subscribe_all(vec![], "http://localhost:9999/cb".into())
            .await;
        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), Error::InvalidArgument(_)));
    }

    #[tokio::test]
    async fn registration_validates_topics_and_callback_urls() {
        let registration = make_registration();
        for (topic, url) in [("", "http://localhost:9999/cb"), ("orders", "/callback")] {
            assert!(matches!(
                registration.subscribe(Subscription::new(topic), url).await,
                Err(Error::InvalidArgument(_))
            ));
            assert!(matches!(
                registration
                    .unsubscribe(Subscription::new(topic), url)
                    .await,
                Err(Error::InvalidArgument(_))
            ));
        }
    }
}
