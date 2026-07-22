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

//! Semantic HTTP webhook acknowledgement helpers.

/// Network settings for an SDK-managed HTTP webhook consumer.
#[cfg(feature = "http")]
#[derive(Debug, Clone)]
pub struct WebhookOptions {
    bind_addr: std::net::SocketAddr,
    advertise_url: Option<String>,
}

#[cfg(feature = "http")]
impl WebhookOptions {
    /// Listen on `bind_addr` and derive the callback URL from the bound address.
    pub fn new(bind_addr: std::net::SocketAddr) -> Self {
        Self {
            bind_addr,
            advertise_url: None,
        }
    }

    /// Override the URL registered with EventMesh.
    ///
    /// This is normally required when binding to `0.0.0.0` or when EventMesh
    /// runs in a container or on another host.
    pub fn with_advertise_url(mut self, url: impl Into<String>) -> Self {
        self.advertise_url = Some(url.into());
        self
    }

    pub(crate) const fn bind_addr(&self) -> std::net::SocketAddr {
        self.bind_addr
    }

    pub(crate) fn advertise_url(&self) -> Option<&str> {
        self.advertise_url.as_deref()
    }
}

/// Built-in axum webhook server.
#[cfg(feature = "http")]
pub struct WebhookServer<H: crate::MessageHandler> {
    inner: crate::transport::http::WebhookServer,
    _handler: std::marker::PhantomData<H>,
}

#[cfg(feature = "http")]
impl<H: crate::MessageHandler> WebhookServer<H> {
    /// Bind before returning, guaranteeing that [`url`](Self::url) is ready to
    /// register with EventMesh.
    pub async fn bind(addr: std::net::SocketAddr, handler: H) -> crate::Result<Self> {
        let inner = crate::transport::http::WebhookServer::bind(
            addr,
            std::sync::Arc::new(crate::handler::PublicHandler::new(handler)),
        )
        .await?;
        Ok(Self {
            inner,
            _handler: std::marker::PhantomData,
        })
    }

    /// Return the URL that should be registered with EventMesh.
    pub fn url(&self) -> String {
        self.inner.url()
    }

    /// Override the externally visible webhook URL.
    pub fn with_advertise_url(mut self, url: impl Into<String>) -> Self {
        self.inner = self.inner.with_advertise_url(url);
        self
    }

    /// Configure a graceful shutdown signal.
    pub fn with_graceful_shutdown(
        mut self,
        signal: impl std::future::Future<Output = ()> + Send + 'static,
    ) -> Self {
        self.inner = self.inner.with_graceful_shutdown(signal);
        self
    }
}

#[cfg(feature = "http")]
impl<H: crate::MessageHandler> std::future::IntoFuture for WebhookServer<H> {
    type Output = crate::Result<()>;
    type IntoFuture =
        std::pin::Pin<Box<dyn std::future::Future<Output = crate::Result<()>> + Send>>;

    fn into_future(self) -> Self::IntoFuture {
        Box::pin(async move { self.inner.await })
    }
}
