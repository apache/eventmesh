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

//! HTTP client API (`http-client`, also included by `http`).
//!
//! Producers, external webhook registration, and codec helpers are independent
//! of a server framework. `http-server` adds the SDK-managed axum consumer.

/// Framework-independent helpers for custom webhook endpoints.
pub mod codec {
    pub use crate::transport::http::codec::{
        parse_push_body, PushMessageRequestBody, WebhookReply,
    };
}

#[cfg(all(test, feature = "http-server"))]
use crate::config::HttpConfig;
use crate::config::{ConsumerOptions, ProducerOptions};
#[cfg(feature = "http-server")]
use crate::error::Error;
use crate::error::Result;
#[cfg(feature = "http-server")]
use crate::subscription::Subscription;
#[cfg(feature = "http-server")]
use crate::transport::http::consumer::combine_cleanup_results;
#[cfg(feature = "http-server")]
use crate::transport::http::WebhookServer;
pub use crate::transport::http::{HttpClient, HttpProducer, WebhookRegistration};
#[cfg(feature = "http-server")]
use crate::transport::task::BackgroundTask;
#[cfg(feature = "http-server")]
use crate::webhook::WebhookOptions;
#[cfg(feature = "http-server")]
use crate::MessageHandler;
#[cfg(all(test, feature = "http-server"))]
use std::sync::Arc;
#[cfg(feature = "http-server")]
use tokio::sync::Mutex;
#[cfg(all(test, feature = "http-server"))]
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;

impl HttpClient {
    /// Create a publishing role.
    pub fn producer(&self, options: ProducerOptions) -> Result<HttpProducer> {
        HttpProducer::new(self.clone(), options)
    }

    /// Start an SDK-managed HTTP consumer.
    ///
    /// The callback socket is bound before subscriptions are registered. The
    /// returned consumer owns the axum server, runtime registrations, and
    /// heartbeat task as one lifecycle.
    /// Requires the `http-server` feature (also included by `http`).
    ///
    /// Cancelling startup stops the local callback server and heartbeat task.
    /// A registration already accepted by the runtime is not rolled back by
    /// cancellation.
    #[cfg(feature = "http-server")]
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
        webhook.validate()?;
        let subscriptions: Vec<_> = subscriptions.into_iter().collect();
        let lifecycle = CancellationToken::new();
        let registration =
            WebhookRegistration::new(self.clone(), options, lifecycle.child_token())?;
        let mut server = WebhookServer::bind(webhook.bind_addr(), handler).await?;
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
        // Own the tasks before the next await so cancellation during startup
        // runs the same Drop cleanup as a fully constructed consumer.
        let consumer = HttpConsumer {
            registration,
            webhook_url,
            lifecycle,
            server_handle: Mutex::new(BackgroundTask::new(server_handle)),
        };

        if let Err(error) = consumer
            .registration
            .subscribe_all(subscriptions, consumer.webhook_url.clone())
            .await
        {
            consumer.shutdown();
            let _ = consumer.join().await;
            return Err(error);
        }

        let server_finished = consumer.server_handle.lock().await.is_finished();
        if server_finished {
            consumer.shutdown();
            let _ = consumer.registration.unsubscribe_all().await;
            return match consumer.join().await {
                Err(error) => Err(error),
                Ok(()) => Err(Error::ChannelClosed(
                    "HTTP webhook server stopped during consumer startup".into(),
                )),
            };
        }

        Ok(consumer)
    }

    /// Create a registration manager for an application-owned HTTP endpoint.
    pub fn webhook_registration(&self, options: ConsumerOptions) -> Result<WebhookRegistration> {
        WebhookRegistration::new(self.clone(), options, CancellationToken::new())
    }
}

/// An SDK-managed HTTP consumer with an embedded axum callback server.
#[cfg(feature = "http-server")]
pub struct HttpConsumer {
    registration: WebhookRegistration,
    webhook_url: String,
    lifecycle: CancellationToken,
    server_handle: Mutex<BackgroundTask<Result<()>>>,
}

#[cfg(feature = "http-server")]
impl HttpConsumer {
    /// Add a subscription to this consumer's callback URL.
    pub async fn subscribe(&self, subscription: Subscription) -> Result<()> {
        self.registration
            .subscribe(subscription, self.webhook_url.clone())
            .await
    }

    /// Remove a subscription from this consumer's callback URL.
    pub async fn unsubscribe(&self, subscription: Subscription) -> Result<()> {
        self.registration
            .unsubscribe(subscription, self.webhook_url.clone())
            .await
    }

    /// Return the URL registered with EventMesh.
    pub fn webhook_url(&self) -> &str {
        &self.webhook_url
    }

    /// Signal heartbeat and callback serving to stop.
    pub fn shutdown(&self) {
        self.registration.shutdown();
        self.lifecycle.cancel();
    }

    /// Wait until the callback server exits and report background task failure.
    ///
    /// Cancelling this wait preserves task ownership and pending results. Call
    /// `join()` again to finish waiting, or drop the consumer to abort its tasks.
    pub async fn join(&self) -> Result<()> {
        let mut server = self.server_handle.lock().await;
        server.wait().await;
        // A panicked server task cannot cancel the lifecycle token itself.
        // Cancel it here so heartbeat shutdown is guaranteed before returning.
        self.lifecycle.cancel();
        self.registration.shutdown();
        let heartbeat_result = self.registration.join().await;
        let server_result = match server.take_result() {
            Some(result) => result.map_err(server_join_error)?,
            None => Ok(()),
        };
        server_result.and(heartbeat_result)
    }

    /// Drain a submitted heartbeat, stop scheduling, unregister, and join.
    ///
    /// Subscription changes are rejected once closing begins. Failed remote
    /// unregisters remain tracked and can be retried by calling `close` again.
    pub async fn close(&self) -> Result<()> {
        let unregister_result = self.registration.unsubscribe_all().await;
        self.shutdown();
        let join_result = self.join().await;
        combine_cleanup_results(unregister_result, join_result)
    }
}

#[cfg(feature = "http-server")]
impl Drop for HttpConsumer {
    fn drop(&mut self) {
        self.lifecycle.cancel();
    }
}

#[cfg(feature = "http-server")]
fn server_join_error(error: tokio::task::JoinError) -> Error {
    Error::Protocol {
        transport: "http",
        message: format!("webhook server task failed: {error}"),
    }
}

#[cfg(all(test, feature = "http-server"))]
mod tests {
    use super::*;
    use axum::{extract::State, http::HeaderMap, routing::post, Json, Router};
    use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
    use tokio::sync::mpsc;

    #[derive(Clone)]
    struct RuntimeState {
        codes: Arc<Mutex<Vec<i32>>>,
        reject_subscribe: Arc<AtomicBool>,
        rejected_unsubscribes_remaining: Arc<AtomicUsize>,
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
        let reject_subscribe = code == crate::common::status_code::RequestCode::SUBSCRIBE
            && state.reject_subscribe.load(Ordering::Relaxed);
        let reject_unsubscribe = if code == crate::common::status_code::RequestCode::UNSUBSCRIBE {
            let counter = &state.rejected_unsubscribes_remaining;
            let mut remaining = counter.load(Ordering::Relaxed);
            loop {
                if remaining == 0 {
                    break false;
                }
                match counter.compare_exchange_weak(
                    remaining,
                    remaining - 1,
                    Ordering::Relaxed,
                    Ordering::Relaxed,
                ) {
                    Ok(_) => break true,
                    Err(actual) => remaining = actual,
                }
            }
        } else {
            false
        };
        let ret_code = if reject_subscribe || reject_unsubscribe {
            17
        } else {
            0
        };
        Json(serde_json::json!({"retCode": ret_code, "retMsg": "test"}))
    }

    async fn mock_runtime(
        reject_subscribe: bool,
        rejected_unsubscribes: usize,
    ) -> (HttpClient, Arc<Mutex<Vec<i32>>>, JoinHandle<()>) {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let codes = Arc::new(Mutex::new(Vec::new()));
        let state = RuntimeState {
            codes: Arc::clone(&codes),
            reject_subscribe: Arc::new(AtomicBool::new(reject_subscribe)),
            rejected_unsubscribes_remaining: Arc::new(AtomicUsize::new(rejected_unsubscribes)),
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

    #[test]
    fn webhook_registration_returns_error_without_a_tokio_runtime() {
        let endpoint = crate::config::Endpoint::new("127.0.0.1", 10_104).unwrap();
        let endpoints = crate::config::EndpointSet::new([endpoint]).unwrap();
        let client = HttpClient::new(HttpConfig::new(endpoints)).unwrap();

        let result = client.webhook_registration(ConsumerOptions::new("consumer-group"));

        assert!(matches!(
            result,
            Err(Error::Config(message)) if message.contains("active Tokio runtime")
        ));
    }

    #[test]
    fn cleanup_result_preserves_one_or_both_failures() {
        assert!(combine_cleanup_results(Ok(()), Ok(())).is_ok());

        let operation_only =
            combine_cleanup_results(Err(Error::InvalidArgument("unregister".into())), Ok(()));
        assert!(matches!(
            operation_only,
            Err(Error::InvalidArgument(message)) if message == "unregister"
        ));

        let shutdown_only =
            combine_cleanup_results(Ok(()), Err(Error::ChannelClosed("join".into())));
        assert!(matches!(
            shutdown_only,
            Err(Error::ChannelClosed(message)) if message == "join"
        ));

        let both = combine_cleanup_results(
            Err(Error::InvalidArgument("unregister".into())),
            Err(Error::ChannelClosed("join".into())),
        );
        match both {
            Err(Error::Cleanup {
                operation,
                shutdown,
            }) => {
                assert!(matches!(
                    *operation,
                    Error::InvalidArgument(ref message) if message == "unregister"
                ));
                assert!(matches!(
                    *shutdown,
                    Error::ChannelClosed(ref message) if message == "join"
                ));
            }
            other => panic!("expected combined cleanup error, got {other:?}"),
        }
    }

    #[tokio::test]
    async fn managed_consumer_binds_before_registering() {
        let (client, codes, runtime) = mock_runtime(false, 0).await;
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

        assert!(matches!(result, Err(Error::Io(_))));
        assert!(
            codes.lock().await.is_empty(),
            "registration must not start before bind"
        );
        runtime.abort();
    }

    #[tokio::test]
    async fn managed_consumer_serves_and_unregisters_on_close() {
        let (client, codes, runtime) = mock_runtime(false, 0).await;
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
    async fn managed_close_keeps_callback_running_until_unregister_finishes() {
        let unregister_entered = Arc::new(tokio::sync::Notify::new());
        let unregister_release = Arc::new(tokio::sync::Notify::new());
        let entered = unregister_entered.clone();
        let release = unregister_release.clone();
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let app = Router::new().route(
            "/",
            post(move |headers: HeaderMap| {
                let entered = entered.clone();
                let release = release.clone();
                async move {
                    if headers["code"]
                        == crate::common::status_code::RequestCode::UNSUBSCRIBE.to_string()
                    {
                        entered.notify_one();
                        release.notified().await;
                    }
                    Json(serde_json::json!({"retCode": 0}))
                }
            }),
        );
        let runtime = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
        let endpoint = crate::config::Endpoint::new("127.0.0.1", address.port()).unwrap();
        let endpoints = crate::config::EndpointSet::new([endpoint]).unwrap();
        let client = HttpClient::new(HttpConfig::new(endpoints)).unwrap();
        let consumer = Arc::new(
            client
                .consumer(
                    ConsumerOptions::new("group"),
                    WebhookOptions::new("127.0.0.1:0".parse().unwrap()),
                    [Subscription::new("orders")],
                    |_| async { Ok(None) },
                )
                .await
                .unwrap(),
        );
        let closing = consumer.clone();
        let close = tokio::spawn(async move { closing.close().await });
        tokio::time::timeout(
            std::time::Duration::from_secs(2),
            unregister_entered.notified(),
        )
        .await
        .unwrap();

        let response = reqwest::Client::builder()
            .no_proxy()
            .build()
            .unwrap()
            .post(consumer.webhook_url())
            .form(&[("content", "in-flight"), ("topic", "orders")])
            .send()
            .await
            .expect("callback must remain available while unregister is pending");
        assert!(response.status().is_success());
        assert!(!close.is_finished());

        unregister_release.notify_one();
        close.await.unwrap().unwrap();
        runtime.abort();
    }

    #[tokio::test]
    async fn cancelled_join_still_waits_for_inflight_http_handler() {
        let (client, _, runtime) = mock_runtime(false, 0).await;
        let entered = Arc::new(tokio::sync::Notify::new());
        let release = Arc::new(tokio::sync::Notify::new());
        let handler_entered = entered.clone();
        let handler_release = release.clone();
        let consumer = client
            .consumer(
                ConsumerOptions::new("group"),
                WebhookOptions::new("127.0.0.1:0".parse().unwrap()),
                [Subscription::new("orders")],
                move |_| {
                    let entered = handler_entered.clone();
                    let release = handler_release.clone();
                    async move {
                        entered.notify_one();
                        release.notified().await;
                        Ok(None)
                    }
                },
            )
            .await
            .unwrap();
        let url = consumer.webhook_url().to_owned();
        let request = tokio::spawn(async move {
            reqwest::Client::new()
                .post(url)
                .form(&[("content", "created"), ("topic", "orders")])
                .send()
                .await
                .unwrap()
                .error_for_status()
                .unwrap()
        });
        tokio::time::timeout(std::time::Duration::from_secs(3), entered.notified())
            .await
            .unwrap();
        consumer.shutdown();
        for _ in 0..2 {
            assert!(
                tokio::time::timeout(std::time::Duration::from_millis(20), consumer.join())
                    .await
                    .is_err(),
                "join must keep waiting for the handler after cancellation"
            );
        }
        release.notify_one();
        tokio::time::timeout(std::time::Duration::from_secs(3), consumer.join())
            .await
            .unwrap()
            .unwrap();
        request.await.unwrap();
        runtime.abort();
    }

    #[tokio::test]
    async fn cancelled_join_then_drop_aborts_http_server_task() {
        let (client, _, runtime) = mock_runtime(false, 0).await;
        let consumer = client
            .consumer(
                ConsumerOptions::new("group"),
                WebhookOptions::new("127.0.0.1:0".parse().unwrap()),
                [Subscription::new("orders")],
                |_| async { Ok(None) },
            )
            .await
            .unwrap();
        let (alive, dropped) = tokio::sync::oneshot::channel::<()>();
        *consumer.server_handle.lock().await = BackgroundTask::new(tokio::spawn(async move {
            let _alive = alive;
            std::future::pending::<Result<()>>().await
        }));
        assert!(
            tokio::time::timeout(std::time::Duration::from_millis(20), consumer.join())
                .await
                .is_err()
        );
        drop(consumer);
        assert!(
            tokio::time::timeout(std::time::Duration::from_secs(3), dropped)
                .await
                .expect("Drop must abort the retained server task")
                .is_err()
        );
        runtime.abort();
    }

    #[tokio::test]
    async fn managed_consumer_shutdown_only_signals_local_tasks() {
        let (client, codes, runtime) = mock_runtime(false, 0).await;
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
        let (client, _codes, runtime) = mock_runtime(true, 0).await;
        let address = available_address();
        let result = client
            .consumer(
                ConsumerOptions::new("group"),
                WebhookOptions::new(address),
                [Subscription::new("orders")],
                |_message| async { Ok(None) },
            )
            .await;
        assert!(matches!(result, Err(Error::Server { .. })));
        assert!(tokio::net::TcpStream::connect(address).await.is_err());
        runtime.abort();
    }

    #[tokio::test]
    async fn cancelled_startup_releases_callback_server() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let runtime_address = listener.local_addr().unwrap();
        let (registration_tx, mut registration_rx) = mpsc::unbounded_channel();
        let runtime = tokio::spawn(async move {
            let app = Router::new().route(
                "/",
                post(move || {
                    registration_tx.send(()).unwrap();
                    std::future::pending::<&'static str>()
                }),
            );
            axum::serve(listener, app).await.unwrap();
        });
        let endpoint = crate::config::Endpoint::new("127.0.0.1", runtime_address.port()).unwrap();
        let endpoints = crate::config::EndpointSet::new([endpoint]).unwrap();
        let client = HttpClient::new(HttpConfig::new(endpoints)).unwrap();
        let address = available_address();
        let (handler_tx, mut handler_rx) = mpsc::unbounded_channel::<()>();
        let startup = tokio::spawn(async move {
            client
                .consumer(
                    ConsumerOptions::new("group"),
                    WebhookOptions::new(address),
                    [Subscription::new("orders")],
                    move |_message| {
                        let _ = &handler_tx;
                        std::future::ready(Ok(None))
                    },
                )
                .await
        });
        let timeout = std::time::Duration::from_secs(2);
        tokio::time::timeout(timeout, registration_rx.recv())
            .await
            .unwrap()
            .expect("registration must start before cancellation");
        assert!(std::net::TcpListener::bind(address).is_err());

        startup.abort();
        assert!(matches!(startup.await, Err(error) if error.is_cancelled()));

        assert!(tokio::time::timeout(timeout, handler_rx.recv())
            .await
            .expect("cancelled startup must drop its callback handler")
            .is_none());
        let _rebound = tokio::net::TcpListener::bind(address)
            .await
            .expect("cancelled startup must release its callback port");
        runtime.abort();
    }

    #[tokio::test]
    async fn registration_close_attempts_every_webhook_url() {
        let (client, codes, runtime) = mock_runtime(false, 1).await;
        let registration = client
            .webhook_registration(ConsumerOptions::new("consumer-group"))
            .unwrap();
        registration
            .subscribe(Subscription::new("orders"), "http://127.0.0.1:30001/orders")
            .await
            .unwrap();
        registration
            .subscribe(
                Subscription::new("payments"),
                "http://127.0.0.1:30002/payments",
            )
            .await
            .unwrap();

        assert!(matches!(
            registration.close().await,
            Err(Error::Server { code: 17, .. })
        ));
        let unsubscribe_count = codes
            .lock()
            .await
            .iter()
            .filter(|code| **code == crate::common::status_code::RequestCode::UNSUBSCRIBE)
            .count();
        assert_eq!(unsubscribe_count, 2);
        runtime.abort();
    }
}
