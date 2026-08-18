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

    pub(crate) fn validate(&self) -> crate::Result<()> {
        if let Some(url) = self.advertise_url() {
            validate_webhook_url(url)?;
        } else if self.bind_addr.ip().is_unspecified() {
            return Err(crate::Error::Config(
                "an advertise URL is required when the webhook binds to an unspecified address"
                    .into(),
            ));
        }
        Ok(())
    }
}

#[cfg(any(feature = "grpc", feature = "http"))]
pub(crate) fn validate_webhook_url(url: &str) -> crate::Result<()> {
    let uri = url
        .parse::<http::Uri>()
        .map_err(|error| crate::Error::InvalidArgument(format!("invalid webhook URL: {error}")))?;
    if !matches!(uri.scheme_str(), Some("http" | "https")) || uri.authority().is_none() {
        return Err(crate::Error::InvalidArgument(
            "webhook URL must be an absolute http:// or https:// URL".into(),
        ));
    }
    if matches!(uri.host(), Some("0.0.0.0" | "::" | "[::]")) {
        return Err(crate::Error::InvalidArgument(
            "webhook URL must use an address reachable by EventMesh".into(),
        ));
    }
    Ok(())
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

#[cfg(all(test, feature = "http"))]
mod tests {
    use super::*;

    #[test]
    fn webhook_urls_must_be_absolute_http_urls() {
        assert!(validate_webhook_url("http://127.0.0.1:8080/callback").is_ok());
        assert!(validate_webhook_url("https://example.com/callback").is_ok());
        assert!(validate_webhook_url("/callback").is_err());
        assert!(validate_webhook_url("ftp://example.com/callback").is_err());
        assert!(validate_webhook_url("http://0.0.0.0:8080/callback").is_err());
        assert!(validate_webhook_url("http://[::]:8080/callback").is_err());
        assert!(validate_webhook_url(" ").is_err());
    }

    #[test]
    fn unspecified_bind_address_requires_an_advertise_url() {
        let options = WebhookOptions::new("0.0.0.0:8080".parse().unwrap());
        assert!(options.validate().is_err());
        assert!(options
            .with_advertise_url("http://127.0.0.1:8080/eventmesh/callback")
            .validate()
            .is_ok());
    }
}
