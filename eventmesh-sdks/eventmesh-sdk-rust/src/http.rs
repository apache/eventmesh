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

//! HTTP client API.

/// Framework-independent helpers for custom webhook endpoints.
pub mod codec {
    pub use crate::transport::http::codec::{
        parse_push_body, PushMessageRequestBody, WebhookReply,
    };
}

use crate::config::{ConsumerOptions, HttpConfig, ProducerOptions};
use crate::error::{EventMeshError, Result};
use crate::handler::PublicHandler;
use crate::message::{Message, PublishReceipt};
use crate::subscription::{DeliveryType, Subscription};
use crate::transport::http::{
    HttpConsumer as LegacyConsumer, HttpProducer as LegacyProducer,
    WebhookServer as ManagedWebhookServer,
};
use crate::transport::Publisher as LegacyPublisher;
use crate::webhook::WebhookOptions;
use crate::MessageHandler;
use std::sync::Arc;
use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;

/// A configured EventMesh HTTP client.
#[derive(Clone)]
pub struct HttpClient {
    config: HttpConfig,
}

impl HttpClient {
    /// Validate and create an HTTP client handle.
    pub fn new(config: HttpConfig) -> Result<Self> {
        config.validate()?;
        // Constructing the private HTTP client validates the endpoint set and
        // request client without issuing network I/O.
        LegacyProducer::new(config.legacy(None, None))?;
        Ok(Self { config })
    }

    /// Create a publishing role.
    pub fn producer(&self, options: ProducerOptions) -> Result<HttpProducer> {
        options.validate()?;
        Ok(HttpProducer {
            inner: LegacyProducer::new(self.config.legacy(Some(&options), None))?,
        })
    }

    /// Start an SDK-managed HTTP consumer.
    ///
    /// The callback socket is bound before subscriptions are registered. The
    /// returned consumer owns the axum server, runtime registrations, and
    /// heartbeat task as one lifecycle.
    pub async fn consumer<H>(
        &self,
        options: ConsumerOptions,
        webhook: WebhookOptions,
        subscriptions: impl IntoIterator<Item = Subscription>,
        handler: H,
    ) -> Result<HttpConsumer>
    where
        H: MessageHandler,
    {
        options.validate()?;
        let subscriptions: Vec<_> = subscriptions.into_iter().collect();
        validate_subscriptions(&subscriptions)?;

        let lifecycle = CancellationToken::new();
        let inner = LegacyConsumer::new(
            self.config.legacy(None, Some(&options)),
            Some(lifecycle.clone().cancelled_owned()),
        )?;
        let mut server =
            ManagedWebhookServer::bind(webhook.bind_addr(), Arc::new(PublicHandler::new(handler)))
                .await?;
        if let Some(url) = webhook.advertise_url() {
            server = server.with_advertise_url(url);
        }
        let webhook_url = server.url();
        server = server.with_graceful_shutdown(lifecycle.clone().cancelled_owned());

        let task_lifecycle = lifecycle.clone();
        let server_handle = tokio::spawn(async move {
            let result = server.await;
            task_lifecycle.cancel();
            result
        });

        if let Err(error) = inner
            .subscribe_webhook(
                subscriptions.iter().map(Subscription::as_legacy).collect(),
                webhook_url.clone(),
            )
            .await
        {
            lifecycle.cancel();
            let _ = inner.shutdown().await;
            let _ = server_handle.await;
            return Err(error);
        }

        if server_handle.is_finished() {
            lifecycle.cancel();
            let _ = inner.unsubscribe_all().await;
            let _ = inner.shutdown().await;
            return match server_handle.await {
                Ok(Err(error)) => Err(error),
                Ok(Ok(())) => Err(EventMeshError::ChannelClosed(
                    "HTTP webhook server stopped during consumer startup".into(),
                )),
                Err(error) => Err(server_join_error(error)),
            };
        }

        Ok(HttpConsumer {
            inner,
            webhook_url,
            lifecycle,
            server_handle: Mutex::new(Some(server_handle)),
        })
    }

    /// Create a registration manager for an application-owned HTTP endpoint.
    pub fn webhook_registration(&self, options: ConsumerOptions) -> Result<WebhookRegistration> {
        options.validate()?;
        Ok(WebhookRegistration {
            inner: LegacyConsumer::new(
                self.config.legacy(None, Some(&options)),
                None::<std::future::Ready<()>>,
            )?,
        })
    }
}

/// HTTP publishing capability.
pub struct HttpProducer {
    inner: LegacyProducer,
}

impl HttpProducer {
    /// Publish one event.
    pub async fn publish(&self, message: Message) -> Result<PublishReceipt> {
        match message {
            Message::EventMesh(message) => self
                .inner
                .publish(message)
                .await
                .map(PublishReceipt::from_legacy),
            #[cfg(feature = "cloud_events")]
            Message::CloudEvent(event) => self
                .inner
                .publish_cloud_event(event)
                .await
                .map(PublishReceipt::from_legacy),
        }
    }
}

/// An SDK-managed HTTP consumer with an embedded axum callback server.
pub struct HttpConsumer {
    inner: LegacyConsumer,
    webhook_url: String,
    lifecycle: CancellationToken,
    server_handle: Mutex<Option<JoinHandle<Result<()>>>>,
}

impl HttpConsumer {
    /// Add a subscription to this consumer's callback URL.
    pub async fn subscribe(&self, subscription: Subscription) -> Result<()> {
        validate_subscriptions(std::slice::from_ref(&subscription))?;
        self.inner
            .subscribe_webhook(vec![subscription.as_legacy()], self.webhook_url.clone())
            .await
            .map(|_| ())
    }

    /// Remove a subscription from this consumer's callback URL.
    pub async fn unsubscribe(&self, subscription: Subscription) -> Result<()> {
        self.inner
            .unsubscribe(vec![subscription.as_legacy()], self.webhook_url.clone())
            .await
            .map(|_| ())
    }

    /// Return the URL registered with EventMesh.
    pub fn webhook_url(&self) -> &str {
        &self.webhook_url
    }

    /// Signal heartbeat and callback serving to stop.
    pub fn shutdown(&self) {
        self.inner.request_shutdown();
        self.lifecycle.cancel();
    }

    /// Wait until the callback server exits and report background task failure.
    pub async fn join(&self) -> Result<()> {
        let server_result = self.wait_for_server().await;
        // A panicked server task cannot cancel the lifecycle token itself.
        // Cancel it here so heartbeat shutdown is guaranteed before returning.
        self.lifecycle.cancel();
        self.inner.request_shutdown();
        let heartbeat_result = self.inner.wait_for_shutdown().await;
        server_result.and(heartbeat_result)
    }

    /// Unregister all subscriptions, signal shutdown, and join background work.
    pub async fn close(&self) -> Result<()> {
        let unregister_result = self.inner.unsubscribe_all().await;
        self.shutdown();
        let join_result = self.join().await;
        unregister_result.and(join_result)
    }

    async fn wait_for_server(&self) -> Result<()> {
        match self.server_handle.lock().await.take() {
            Some(handle) => handle.await.map_err(server_join_error)?,
            None => Ok(()),
        }
    }
}

impl Drop for HttpConsumer {
    fn drop(&mut self) {
        self.lifecycle.cancel();
        if let Ok(mut handle) = self.server_handle.try_lock() {
            if let Some(handle) = handle.take() {
                handle.abort();
            }
        }
    }
}

/// Registration and heartbeat manager for an application-owned webhook URL.
pub struct WebhookRegistration {
    inner: LegacyConsumer,
}

impl WebhookRegistration {
    /// Register a subscription to an application-owned callback URL.
    pub async fn subscribe(
        &self,
        subscription: Subscription,
        webhook_url: impl Into<String>,
    ) -> Result<()> {
        validate_subscriptions(std::slice::from_ref(&subscription))?;
        self.inner
            .subscribe_webhook(vec![subscription.as_legacy()], webhook_url)
            .await
            .map(|_| ())
    }

    /// Remove a registration using the same URL supplied to [`subscribe`](Self::subscribe).
    pub async fn unsubscribe(
        &self,
        subscription: Subscription,
        webhook_url: impl Into<String>,
    ) -> Result<()> {
        self.inner
            .unsubscribe(vec![subscription.as_legacy()], webhook_url)
            .await
            .map(|_| ())
    }

    /// Signal the heartbeat task to stop.
    pub fn shutdown(&self) {
        self.inner.request_shutdown();
    }

    /// Wait for the heartbeat task to stop and report task failure.
    pub async fn join(&self) -> Result<()> {
        self.inner.wait_for_shutdown().await
    }

    /// Unregister every tracked subscription, shut down, and join.
    pub async fn close(&self) -> Result<()> {
        let unregister_result = self.inner.unsubscribe_all().await;
        self.shutdown();
        let join_result = self.join().await;
        unregister_result.and(join_result)
    }
}

fn validate_subscriptions(subscriptions: &[Subscription]) -> Result<()> {
    if subscriptions.is_empty() {
        return Err(EventMeshError::InvalidArgument(
            "HTTP consumer requires at least one initial subscription".into(),
        ));
    }
    if subscriptions
        .iter()
        .any(|subscription| subscription.delivery_type == DeliveryType::Sync)
    {
        return Err(EventMeshError::Unsupported(
            "HTTP request/reply subscriptions".into(),
        ));
    }
    Ok(())
}

fn server_join_error(error: tokio::task::JoinError) -> EventMeshError {
    EventMeshError::Protocol {
        transport: "http",
        message: format!("webhook server task failed: {error}"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{extract::State, http::HeaderMap, routing::post, Json, Router};
    use std::sync::atomic::{AtomicBool, Ordering};
    use tokio::sync::mpsc;

    #[derive(Clone)]
    struct RuntimeState {
        codes: Arc<Mutex<Vec<i32>>>,
        reject_subscribe: Arc<AtomicBool>,
    }

    async fn runtime_reply(
        State(state): State<RuntimeState>,
        headers: HeaderMap,
    ) -> Json<serde_json::Value> {
        let code = headers
            .get("code")
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.parse().ok())
            .unwrap_or_default();
        state.codes.lock().await.push(code);
        let ret_code = if code == crate::common::status_code::RequestCode::SUBSCRIBE
            && state.reject_subscribe.load(Ordering::Relaxed)
        {
            17
        } else {
            0
        };
        Json(serde_json::json!({"retCode": ret_code, "retMsg": "test"}))
    }

    async fn mock_runtime(
        reject_subscribe: bool,
    ) -> (HttpClient, Arc<Mutex<Vec<i32>>>, JoinHandle<()>) {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let codes = Arc::new(Mutex::new(Vec::new()));
        let state = RuntimeState {
            codes: Arc::clone(&codes),
            reject_subscribe: Arc::new(AtomicBool::new(reject_subscribe)),
        };
        let task = tokio::spawn(async move {
            axum::serve(
                listener,
                Router::new()
                    .route("/", post(runtime_reply))
                    .with_state(state),
            )
            .await
            .unwrap();
        });
        let endpoint = crate::config::Endpoint::new("127.0.0.1", address.port()).unwrap();
        let endpoints = crate::config::EndpointSet::new([endpoint]).unwrap();
        let client = HttpClient::new(HttpConfig::new(endpoints)).unwrap();
        (client, codes, task)
    }

    fn available_address() -> std::net::SocketAddr {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        drop(listener);
        address
    }

    #[tokio::test]
    async fn managed_consumer_binds_before_registering() {
        let (client, codes, runtime) = mock_runtime(false).await;
        let occupied = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = occupied.local_addr().unwrap();

        let result = client
            .consumer(
                ConsumerOptions::new("group"),
                WebhookOptions::new(address),
                [Subscription::new("orders")],
                |_message| async { Ok(None) },
            )
            .await;

        assert!(matches!(result, Err(EventMeshError::Io(_))));
        assert!(
            codes.lock().await.is_empty(),
            "registration must not start before bind"
        );
        runtime.abort();
    }

    #[tokio::test]
    async fn managed_consumer_serves_and_unregisters_on_close() {
        let (client, codes, runtime) = mock_runtime(false).await;
        let (tx, mut rx) = mpsc::unbounded_channel();
        let consumer = client
            .consumer(
                ConsumerOptions::new("group"),
                WebhookOptions::new("127.0.0.1:0".parse().unwrap()),
                [Subscription::new("orders")],
                move |message| {
                    let tx = tx.clone();
                    async move {
                        tx.send(message).unwrap();
                        Ok(None)
                    }
                },
            )
            .await
            .unwrap();

        reqwest::Client::new()
            .post(consumer.webhook_url())
            .form(&[("content", "created"), ("topic", "orders")])
            .send()
            .await
            .unwrap()
            .error_for_status()
            .unwrap();
        let received = tokio::time::timeout(std::time::Duration::from_secs(1), rx.recv())
            .await
            .unwrap()
            .unwrap();
        assert_eq!(received.as_event_mesh().unwrap().content(), "created");

        consumer.close().await.unwrap();
        let codes = codes.lock().await.clone();
        assert!(codes.contains(&crate::common::status_code::RequestCode::SUBSCRIBE));
        assert!(codes.contains(&crate::common::status_code::RequestCode::UNSUBSCRIBE));
        runtime.abort();
    }

    #[tokio::test]
    async fn managed_consumer_shutdown_only_signals_local_tasks() {
        let (client, codes, runtime) = mock_runtime(false).await;
        let consumer = client
            .consumer(
                ConsumerOptions::new("group"),
                WebhookOptions::new("127.0.0.1:0".parse().unwrap()),
                [Subscription::new("orders")],
                |_message| async { Ok(None) },
            )
            .await
            .unwrap();

        consumer.shutdown();
        consumer.join().await.unwrap();

        let codes = codes.lock().await.clone();
        assert!(codes.contains(&crate::common::status_code::RequestCode::SUBSCRIBE));
        assert!(
            !codes.contains(&crate::common::status_code::RequestCode::UNSUBSCRIBE),
            "shutdown must not perform remote cleanup; close owns that operation"
        );
        runtime.abort();
    }

    #[tokio::test]
    async fn registration_failure_stops_managed_server() {
        let (client, _codes, runtime) = mock_runtime(true).await;
        let address = available_address();
        let result = client
            .consumer(
                ConsumerOptions::new("group"),
                WebhookOptions::new(address),
                [Subscription::new("orders")],
                |_message| async { Ok(None) },
            )
            .await;
        assert!(matches!(result, Err(EventMeshError::Server { .. })));
        assert!(tokio::net::TcpStream::connect(address).await.is_err());
        runtime.abort();
    }
}
