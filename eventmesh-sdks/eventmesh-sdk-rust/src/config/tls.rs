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

//! TLS configuration for the gRPC channel.
//!
//! This module is plain data only (no tonic dependency) so that
//! [`TlsConfig`] is always available regardless of the `tls` cargo feature.
//! The actual application of TLS settings to the tonic [`Endpoint`] is gated
//! behind `#[cfg(feature = "tls")]` in [`crate::transport::grpc::client`].

use std::path::PathBuf;

/// TLS configuration for the gRPC channel.
///
/// Set on [`GrpcClientConfig`](super::GrpcClientConfig) and used only when
/// `use_tls` is `true`. If `use_tls` is `true` but `tls_config` is `None`,
/// the OS-native trust roots are loaded automatically and the endpoint
/// authority is used as the SNI domain.
///
/// # Examples
///
/// ```ignore
/// use eventmesh::config::{GrpcClientConfig, TlsConfig, TlsClientIdentity};
///
/// // Self-signed CA
/// let config = GrpcClientConfig::builder()
///     .server_addr("eventmesh.internal")
///     .use_tls(true)
///     .tls_config(
///         TlsConfig::builder()
///             .ca_cert_path("/etc/ssl/certs/internal-ca.pem")
///             .use_native_roots(true)
///             .build(),
///     )
///     .build();
///
/// // Mutual TLS
/// let config = GrpcClientConfig::builder()
///     .server_addr("eventmesh.internal")
///     .use_tls(true)
///     .tls_config(
///         TlsConfig::builder()
///             .ca_cert_path("/etc/ssl/certs/ca.pem")
///             .client_identity(TlsClientIdentity {
///                 cert_pem: std::fs::read("client.pem").unwrap(),
///                 key_pem: std::fs::read("client.key").unwrap(),
///             })
///             .build(),
///     )
///     .build();
/// ```
#[derive(Clone, Default)]
pub struct TlsConfig {
    /// Expected SNI / certificate hostname. Defaults to `server_addr` when
    /// unset — set this when connecting via IP but the cert is for a domain.
    pub domain: Option<String>,
    /// Path to a PEM-encoded CA certificate file. Used only when
    /// `ca_cert_pem` is `None`.
    pub ca_cert_path: Option<PathBuf>,
    /// Inline PEM-encoded CA certificate bytes. Takes precedence over
    /// `ca_cert_path`.
    pub ca_cert_pem: Option<Vec<u8>>,
    /// Load the OS-native trust roots in addition to any explicit CA cert.
    pub use_native_roots: bool,
    /// Client certificate + key for mutual TLS (mTLS).
    pub client_identity: Option<TlsClientIdentity>,
}

impl TlsConfig {
    /// Start a fluent builder.
    pub fn builder() -> TlsConfigBuilder {
        TlsConfigBuilder::default()
    }

    /// Resolve CA cert bytes from inline PEM or file path.
    ///
    /// Returns `None` when neither source is configured. Returns `Some(Err)`
    /// when the file cannot be read.
    pub fn ca_cert_pem_bytes(&self) -> Option<std::io::Result<Vec<u8>>> {
        self.ca_cert_pem
            .as_ref()
            .map(|pem| Ok(pem.clone()))
            .or_else(|| self.ca_cert_path.as_ref().map(std::fs::read))
    }
}

/// PEM-encoded client certificate + private key for mutual TLS.
#[derive(Clone)]
pub struct TlsClientIdentity {
    /// PEM-encoded certificate chain.
    pub cert_pem: Vec<u8>,
    /// PEM-encoded private key.
    pub key_pem: Vec<u8>,
}

/// Fluent builder for [`TlsConfig`].
#[derive(Clone, Default)]
pub struct TlsConfigBuilder {
    domain: Option<String>,
    ca_cert_path: Option<PathBuf>,
    ca_cert_pem: Option<Vec<u8>>,
    use_native_roots: Option<bool>,
    client_identity: Option<TlsClientIdentity>,
}

impl TlsConfigBuilder {
    /// Set the SNI / certificate hostname.
    pub fn domain(mut self, v: impl Into<String>) -> Self {
        self.domain = Some(v.into());
        self
    }

    /// Path to a PEM-encoded CA certificate file.
    pub fn ca_cert_path(mut self, v: impl Into<PathBuf>) -> Self {
        self.ca_cert_path = Some(v.into());
        self
    }

    /// Inline PEM-encoded CA certificate bytes.
    pub fn ca_cert_pem(mut self, v: impl Into<Vec<u8>>) -> Self {
        self.ca_cert_pem = Some(v.into());
        self
    }

    /// Whether to load the OS-native trust roots alongside any explicit CA.
    pub fn use_native_roots(mut self, v: bool) -> Self {
        self.use_native_roots = Some(v);
        self
    }

    /// Client certificate + key for mTLS.
    pub fn client_identity(mut self, v: TlsClientIdentity) -> Self {
        self.client_identity = Some(v);
        self
    }

    /// Finalize the config.
    pub fn build(self) -> TlsConfig {
        TlsConfig {
            domain: self.domain,
            ca_cert_path: self.ca_cert_path,
            ca_cert_pem: self.ca_cert_pem,
            use_native_roots: self.use_native_roots.unwrap_or(false),
            client_identity: self.client_identity,
        }
    }
}

// -----------------------------------------------------------------------
// Redacting Debug impls
// -----------------------------------------------------------------------

impl std::fmt::Debug for TlsClientIdentity {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("TlsClientIdentity")
            .field("cert_pem", &format!("<{} bytes>", self.cert_pem.len()))
            .field("key_pem", &"***")
            .finish()
    }
}

impl std::fmt::Debug for TlsConfig {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("TlsConfig")
            .field("domain", &self.domain)
            .field("ca_cert_path", &self.ca_cert_path)
            .field(
                "ca_cert_pem",
                &self
                    .ca_cert_pem
                    .as_ref()
                    .map(|v| format!("<{} bytes>", v.len())),
            )
            .field("use_native_roots", &self.use_native_roots)
            .field("client_identity", &self.client_identity)
            .finish()
    }
}

impl std::fmt::Debug for TlsConfigBuilder {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("TlsConfigBuilder")
            .field("domain", &self.domain)
            .field("ca_cert_path", &self.ca_cert_path)
            .field(
                "ca_cert_pem",
                &self
                    .ca_cert_pem
                    .as_ref()
                    .map(|v| format!("<{} bytes>", v.len())),
            )
            .field("use_native_roots", &self.use_native_roots)
            .field("client_identity", &self.client_identity)
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builder_defaults() {
        let tls = TlsConfig::builder().build();
        assert!(tls.domain.is_none());
        assert!(tls.ca_cert_path.is_none());
        assert!(tls.ca_cert_pem.is_none());
        assert!(!tls.use_native_roots);
        assert!(tls.client_identity.is_none());
    }

    #[test]
    fn builder_full() {
        let tls = TlsConfig::builder()
            .domain("example.com")
            .ca_cert_pem(b"fake-pem".to_vec())
            .use_native_roots(true)
            .client_identity(TlsClientIdentity {
                cert_pem: b"cert".to_vec(),
                key_pem: b"key".to_vec(),
            })
            .build();
        assert_eq!(tls.domain.as_deref(), Some("example.com"));
        assert_eq!(tls.ca_cert_pem.as_deref(), Some(b"fake-pem" as &[u8]));
        assert!(tls.use_native_roots);
        let id = tls.client_identity.unwrap();
        assert_eq!(id.cert_pem, b"cert");
        assert_eq!(id.key_pem, b"key");
    }

    #[test]
    fn ca_cert_pem_bytes_prefers_inline() {
        let tls = TlsConfig::builder()
            .ca_cert_pem(b"inline".to_vec())
            .ca_cert_path("/nonexistent")
            .build();
        assert_eq!(
            tls.ca_cert_pem_bytes().unwrap().unwrap(),
            b"inline".to_vec()
        );
    }

    #[test]
    fn ca_cert_pem_bytes_returns_none_when_unset() {
        let tls = TlsConfig::builder().build();
        assert!(tls.ca_cert_pem_bytes().is_none());
    }

    #[test]
    fn debug_redacts_private_key() {
        let tls = TlsConfig::builder()
            .ca_cert_pem(b"fake-ca".to_vec())
            .client_identity(TlsClientIdentity {
                cert_pem: b"my-cert".to_vec(),
                key_pem: b"PRIVATE KEY MATERIAL".to_vec(),
            })
            .build();
        let s = format!("{tls:?}");
        assert!(!s.contains("PRIVATE KEY MATERIAL"), "key leaked: {s}");
        assert!(!s.contains("my-cert"), "cert content leaked: {s}");
        assert!(s.contains("***"), "redaction marker missing: {s}");
        assert!(s.contains("<7 bytes>"), "ca cert length missing: {s}");
    }
}
