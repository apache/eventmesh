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
//! Receives EventMesh pushes and dispatches them to [`MessageHandler`]. The
//! public [`crate::http::HttpClient::consumer`] API manages this server with
//! its subscriptions, or applications can bind [`crate::webhook::WebhookServer`]
//! separately. See `examples/http/consumer_server.rs` for the managed lifecycle.

use std::future::{Future, IntoFuture};
use std::net::SocketAddr;
use std::pin::Pin;
use std::sync::Arc;

use axum::{routing::post, Router};
use tracing::info;

use crate::error::{Error, Result};
use crate::transport::http::webhook::{WebhookHandler, WebhookState};
use crate::MessageHandler;

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
    listener: tokio::net::TcpListener,
    advertise_url: Option<String>,
    shutdown: Option<Pin<Box<dyn Future<Output = ()> + Send + 'static>>>,
}

impl WebhookServer {
    /// Bind `addr` before returning, so callers can safely register the webhook
    /// URL without a connection-refused window.
    pub async fn bind<H>(addr: SocketAddr, handler: H) -> Result<Self>
    where
        H: MessageHandler,
    {
        let listener = tokio::net::TcpListener::bind(addr).await?;
        let addr = listener.local_addr()?;
        let router = Router::new()
            .route(DEFAULT_WEBHOOK_PATH, post(WebhookHandler::handle))
            .with_state(WebhookState::new(Arc::new(handler)));
        Ok(Self {
            router,
            addr,
            listener,
            advertise_url: None,
            shutdown: None,
        })
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
            .unwrap_or_else(|| format!("http://{}{DEFAULT_WEBHOOK_PATH}", self.addr))
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
            advertise_url: _,
            shutdown,
        } = self;

        Box::pin(async move {
            info!("webhook server listening on http://{addr}{DEFAULT_WEBHOOK_PATH}");

            let serve = axum::serve(listener, router);
            let result = if let Some(signal) = shutdown {
                serve.with_graceful_shutdown(signal).await
            } else {
                serve.await
            };
            result.map_err(|e| Error::Protocol {
                transport: "http",
                message: format!("webhook server error: {e}"),
            })?;
            Ok(())
        })
    }
}
