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

/// The outcome returned to EventMesh after an HTTP webhook delivery.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Ack {
    /// The event was accepted.
    Ok,
    /// Ask EventMesh to redeliver the event.
    Retry { message: String },
    /// Reject the event without retrying.
    Fail { message: String },
    /// Tell EventMesh that no listener is available.
    NoListen { message: String },
}

impl Ack {
    /// Acknowledge the event.
    pub const fn ok() -> Self {
        Self::Ok
    }

    /// Request redelivery with a diagnostic message.
    pub fn retry(message: impl Into<String>) -> Self {
        Self::Retry {
            message: message.into(),
        }
    }

    /// Reject the event without retrying.
    pub fn fail(message: impl Into<String>) -> Self {
        Self::Fail {
            message: message.into(),
        }
    }

    /// Indicate that the listener is unavailable.
    pub fn no_listen(message: impl Into<String>) -> Self {
        Self::NoListen {
            message: message.into(),
        }
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
    /// Construct a webhook server that delivers to `handler`.
    pub fn new(addr: std::net::SocketAddr, handler: H) -> Self {
        let inner = crate::transport::http::WebhookServer::new(
            addr,
            std::sync::Arc::new(crate::handler::PublicHandler::new(handler)),
        );
        Self {
            inner,
            _handler: std::marker::PhantomData,
        }
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
