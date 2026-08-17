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

//! Built-in webhook server (axum).
//!
//! A batteries-included HTTP server that receives webhook pushes from the
//! EventMesh runtime. For users who don't want to wire up their own axum/hyper
//! application, this provides a one-liner server.
//!
//! # Example
//!
//! ```ignore
//! # use eventmesh::{
//! #     config::HttpClientConfig, http::{HttpConsumer, WebhookServer},
//! #     DeliveryMode, DeliveryType, EventMeshMessage, Subscription,
//! #     MessageListener,
//! # };
//! # struct MyListener;
//! # impl MessageListener for MyListener {
//! #     type Message = EventMeshMessage;
//! #     async fn handle(&self, _: Self::Message) -> Option<Self::Message> { None }
//! # }
//! # #[tokio::main]
//! # async fn main() -> eventmesh::Result<()> {
//! use std::sync::Arc;
//!
//! let listener = Arc::new(MyListener);
//! let addr: std::net::SocketAddr = "0.0.0.0:8080".parse().unwrap();
//! let server = WebhookServer::bind(addr, listener.clone()).await?;
//!
//! let config = HttpClientConfig::builder()
//!     .servers("127.0.0.1:10105")
//!     .build()?;
//! let consumer = HttpConsumer::new(config, None::<std::future::Ready<()>>)?;
//! consumer.subscribe_webhook(
//!     vec![Subscription::new("test-topic")],
//!     server.url(),
//! ).await?;
//!
//! server.await?; // blocks until shutdown
//! # Ok(())
//! # }
//! ```

use std::future::{Future, IntoFuture};
use std::net::SocketAddr;
use std::pin::Pin;
use std::sync::Arc;

use axum::{routing::post, Router};
use tracing::info;

use crate::error::{EventMeshError, Result};
use crate::transport::http::webhook::{WebhookHandler, WebhookState};
use crate::MessageListener;

/// Default path the webhook server listens on.
pub const DEFAULT_WEBHOOK_PATH: &str = "/eventmesh/callback";

/// A built-in axum-based webhook server.
///
/// Bind with [`WebhookServer::bind`], optionally call
/// [`WebhookServer::with_graceful_shutdown`], then `.await` to run.
///
/// The server binds to `addr`, but the URL registered with the EventMesh
/// runtime (via [`WebhookServer::url`]) must be reachable *from the runtime's
/// perspective*. If the runtime runs in Docker and the consumer on the host,
/// `0.0.0.0` is not a valid target — use [`WebhookServer::with_advertise_url`]
/// to set a URL the runtime can actually POST to.
pub struct WebhookServer {
    router: Router,
    addr: SocketAddr,
    listener: Option<tokio::net::TcpListener>,
    path: String,
    advertise_url: Option<String>,
    shutdown: Option<Pin<Box<dyn Future<Output = ()> + Send + 'static>>>,
}

impl WebhookServer {
    /// Bind `addr` before returning, so callers can safely register the webhook
    /// URL without a connection-refused window.
    pub async fn bind<L>(addr: SocketAddr, listener: Arc<L>) -> Result<Self>
    where
        L: MessageListener,
        L::Message: crate::transport::http::webhook::WebhookMessage,
    {
        Self::bind_with_path(addr, listener, DEFAULT_WEBHOOK_PATH).await
    }

    /// Like [`WebhookServer::bind`] but with a custom webhook path.
    pub async fn bind_with_path<L>(addr: SocketAddr, listener: Arc<L>, path: &str) -> Result<Self>
    where
        L: MessageListener,
        L::Message: crate::transport::http::webhook::WebhookMessage,
    {
        let socket = tokio::net::TcpListener::bind(addr)
            .await
            .map_err(EventMeshError::Io)?;
        let bound_addr = socket.local_addr().map_err(EventMeshError::Io)?;
        let mut server = Self::with_path(addr, listener, path);
        server.addr = bound_addr;
        server.listener = Some(socket);
        Ok(server)
    }

    /// Construct an unbound webhook server.
    ///
    /// Prefer [`bind`](Self::bind), which reports address conflicts before the
    /// callback URL is registered.
    pub fn new<L>(addr: SocketAddr, listener: Arc<L>) -> Self
    where
        L: MessageListener,
        L::Message: crate::transport::http::webhook::WebhookMessage,
    {
        Self::with_path(addr, listener, DEFAULT_WEBHOOK_PATH)
    }

    /// Like [`WebhookServer::new`] but with a custom webhook path.
    pub fn with_path<L>(addr: SocketAddr, listener: Arc<L>, path: &str) -> Self
    where
        L: MessageListener,
        L::Message: crate::transport::http::webhook::WebhookMessage,
    {
        let state = WebhookState::new(listener);
        let router = Router::new()
            .route(path, post(WebhookHandler::handle))
            .with_state(state);
        Self {
            router,
            addr,
            listener: None,
            path: path.to_string(),
            advertise_url: None,
            shutdown: None,
        }
    }

    /// The full webhook URL that should be registered with the EventMesh runtime.
    ///
    /// Returns the [`with_advertise_url`](Self::with_advertise_url) value if set;
    /// otherwise derives `http://{addr}{path}` from the bind address. Note that
    /// when bound to `0.0.0.0` the derived URL is unreachable from another host
    /// (or a Docker container) — use `with_advertise_url` in those cases.
    pub fn url(&self) -> String {
        self.advertise_url
            .clone()
            .unwrap_or_else(|| format!("http://{}{}", self.addr, self.path))
    }

    /// Override the webhook URL returned by [`url`](Self::url).
    ///
    /// Use this when the bind address is not reachable from the EventMesh
    /// runtime (e.g. bound to `0.0.0.0`, or the runtime is in a Docker
    /// container). Example: `http://127.0.0.1:9090/eventmesh/callback`.
    pub fn with_advertise_url(mut self, url: impl Into<String>) -> Self {
        self.advertise_url = Some(url.into());
        self
    }

    /// The address the server will bind to.
    pub fn addr(&self) -> SocketAddr {
        self.addr
    }

    /// Attach a graceful shutdown signal. When `signal` resolves, the server
    /// stops accepting new connections and drains active ones.
    pub fn with_graceful_shutdown(
        mut self,
        signal: impl Future<Output = ()> + Send + 'static,
    ) -> Self {
        self.shutdown = Some(Box::pin(signal));
        self
    }
}

impl IntoFuture for WebhookServer {
    type Output = Result<()>;
    type IntoFuture = Pin<Box<dyn Future<Output = Result<()>> + Send>>;

    fn into_future(self) -> Self::IntoFuture {
        let Self {
            router,
            addr,
            listener,
            path,
            advertise_url: _,
            shutdown,
        } = self;

        Box::pin(async move {
            let listener = match listener {
                Some(listener) => listener,
                None => tokio::net::TcpListener::bind(addr)
                    .await
                    .map_err(EventMeshError::Io)?,
            };
            let bound_addr = listener.local_addr().map_err(EventMeshError::Io)?;
            info!("webhook server listening on http://{bound_addr}{path}");

            let serve = axum::serve(listener, router);
            let result = if let Some(signal) = shutdown {
                serve.with_graceful_shutdown(signal).await
            } else {
                serve.await
            };
            result.map_err(|e| EventMeshError::Protocol {
                transport: "http",
                message: format!("webhook server error: {e}"),
            })?;
            Ok(())
        })
    }
}
