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

//! Transport configuration types.

use std::time::Duration;

use crate::error::{EventMeshError, Result};

use super::{ClientIdentity, ReconnectConfig};

/// Default timeout for short gRPC operations.
pub const DEFAULT_GRPC_REQUEST_TIMEOUT: Duration = Duration::from_secs(5);
/// Default timeout for HTTP requests.
pub const DEFAULT_HTTP_REQUEST_TIMEOUT: Duration = Duration::from_secs(15);
/// Default timeout for TCP request/response operations.
pub const DEFAULT_TCP_REQUEST_TIMEOUT: Duration = Duration::from_secs(20);
/// Default timeout for establishing a TCP socket, matching Java TCP.
pub const DEFAULT_TCP_CONNECT_TIMEOUT: Duration = Duration::from_secs(1);
/// Default timeout for TCP protocol-control responses such as HELLO and
/// subscription commands.
pub const DEFAULT_TCP_CONTROL_TIMEOUT: Duration = Duration::from_secs(20);

/// A validated EventMesh host and port.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Endpoint {
    host: String,
    port: u16,
    weight: u32,
}

impl Endpoint {
    /// Construct an endpoint.  Host names and IP literals are accepted; port
    /// zero and blank hosts are rejected.
    pub fn new(host: impl Into<String>, port: u16) -> Result<Self> {
        let host = host.into();
        if host.trim().is_empty() {
            return Err(EventMeshError::Config(
                "endpoint host must not be empty".into(),
            ));
        }
        if host.chars().any(char::is_whitespace) {
            return Err(EventMeshError::Config(
                "endpoint host must not contain whitespace".into(),
            ));
        }
        if host.contains("://") || host.chars().any(|c| matches!(c, '/' | '?' | '#')) {
            return Err(EventMeshError::Config(
                "endpoint host must not contain a scheme, path, query, or fragment".into(),
            ));
        }
        if host.contains(':') {
            let literal = host
                .strip_prefix('[')
                .and_then(|host| host.strip_suffix(']'))
                .unwrap_or(&host);
            if literal.parse::<std::net::Ipv6Addr>().is_err() {
                return Err(EventMeshError::Config(
                    "endpoint host containing ':' must be a valid IPv6 literal".into(),
                ));
            }
        }
        if port == 0 {
            return Err(EventMeshError::Config(
                "endpoint port must not be zero".into(),
            ));
        }
        Ok(Self {
            host,
            port,
            weight: 1,
        })
    }

    /// Return the endpoint host.
    pub fn host(&self) -> &str {
        &self.host
    }

    /// Return the endpoint port.
    pub const fn port(&self) -> u16 {
        self.port
    }

    /// Return a copy with a non-zero HTTP load-balancing weight.
    pub fn with_weight(mut self, weight: u32) -> Result<Self> {
        if weight == 0 {
            return Err(EventMeshError::Config(
                "endpoint weight must be greater than zero".into(),
            ));
        }
        if weight > i32::MAX as u32 {
            return Err(EventMeshError::Config(format!(
                "endpoint weight must not exceed {}",
                i32::MAX
            )));
        }
        self.weight = weight;
        Ok(self)
    }

    /// The HTTP load-balancing weight.
    pub const fn weight(&self) -> u32 {
        self.weight
    }

    /// Render an authority, including brackets for IPv6 literals.
    pub fn authority(&self) -> String {
        format!("{}:{}", self.authority_host(), self.port)
    }

    /// Render the host component for an authority, including brackets around
    /// bare IPv6 literals.
    fn authority_host(&self) -> String {
        if self.host.contains(':') && !self.host.starts_with('[') {
            format!("[{}]", self.host)
        } else {
            self.host.clone()
        }
    }
}

/// A non-empty collection of HTTP endpoints.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EndpointSet(Vec<Endpoint>);

impl EndpointSet {
    /// Construct a non-empty endpoint set.
    pub fn new(endpoints: impl IntoIterator<Item = Endpoint>) -> Result<Self> {
        let endpoints: Vec<_> = endpoints.into_iter().collect();
        if endpoints.is_empty() {
            return Err(EventMeshError::Config(
                "at least one endpoint is required".into(),
            ));
        }
        let total_weight = endpoints
            .iter()
            .try_fold(0u64, |total, endpoint| {
                total.checked_add(u64::from(endpoint.weight))
            })
            .ok_or_else(|| EventMeshError::Config("endpoint weight sum overflowed".into()))?;
        if total_weight > i32::MAX as u64 {
            return Err(EventMeshError::Config(format!(
                "endpoint weights must sum to at most {}",
                i32::MAX
            )));
        }
        Ok(Self(endpoints))
    }

    /// Borrow the endpoints in this set.
    pub fn endpoints(&self) -> &[Endpoint] {
        &self.0
    }
}

/// Authentication material supplied to EventMesh.
#[derive(Clone, Default, PartialEq, Eq)]
pub struct Credentials {
    username: Option<String>,
    password: Option<String>,
    token: Option<String>,
}

impl Credentials {
    /// Start with no credentials.
    pub const fn new() -> Self {
        Self {
            username: None,
            password: None,
            token: None,
        }
    }

    /// Configure username/password authentication.
    pub fn with_basic(mut self, username: impl Into<String>, password: impl Into<String>) -> Self {
        self.username = Some(username.into());
        self.password = Some(password.into());
        self
    }

    /// Configure bearer/token authentication.
    pub fn with_token(mut self, token: impl Into<String>) -> Self {
        self.token = Some(token.into());
        self
    }
}

impl std::fmt::Debug for Credentials {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Credentials")
            .field("username", &self.username)
            .field("password", &self.password.as_ref().map(|_| "***"))
            .field("token", &self.token.as_ref().map(|_| "***"))
            .finish()
    }
}

/// Runtime identity attached to EventMesh requests.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Identity {
    env: String,
    idc: String,
    system: String,
    process_id: String,
    ip: String,
    language: String,
}

impl Default for Identity {
    fn default() -> Self {
        let legacy = ClientIdentity::detect();
        Self {
            env: legacy.env,
            idc: legacy.idc,
            system: legacy.sys,
            process_id: legacy.pid,
            ip: legacy.ip,
            language: legacy.language,
        }
    }
}

impl Identity {
    /// Set the EventMesh environment label.
    pub fn with_env(mut self, env: impl Into<String>) -> Self {
        self.env = env.into();
        self
    }

    /// Set the data-centre label.
    pub fn with_idc(mut self, idc: impl Into<String>) -> Self {
        self.idc = idc.into();
        self
    }

    /// Set the calling system/application name.
    pub fn with_system(mut self, system: impl Into<String>) -> Self {
        self.system = system.into();
        self
    }

    /// Override the process identifier sent to EventMesh.
    pub fn with_process_id(mut self, process_id: impl Into<String>) -> Self {
        self.process_id = process_id.into();
        self
    }

    /// Override the client IP advertised to EventMesh.
    ///
    /// EventMesh uses this value together with the process identifier to
    /// distinguish gRPC stream subscribers; it does not need to be a local
    /// bind address.
    pub fn with_ip(mut self, ip: impl Into<String>) -> Self {
        self.ip = ip.into();
        self
    }
}

/// Options shared by a protocol client.
#[derive(Debug, Clone, Default)]
pub struct ClientOptions {
    request_timeout: Option<Duration>,
}

impl ClientOptions {
    /// Override the timeout for unary client operations.
    pub fn with_request_timeout(mut self, request_timeout: Duration) -> Self {
        self.request_timeout = Some(request_timeout);
        self
    }

    /// Return the request-timeout override, or `None` to use the transport's
    /// default.
    pub const fn request_timeout(&self) -> Option<Duration> {
        self.request_timeout
    }

    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    fn validate(&self) -> Result<()> {
        if let Some(timeout) = self.request_timeout {
            validate_non_zero_duration("request timeout", timeout)?;
        }
        Ok(())
    }
}

/// Options for a producer role.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ProducerOptions {
    group: String,
}

impl ProducerOptions {
    /// Create options for `group`.
    pub fn new(group: impl Into<String>) -> Self {
        Self {
            group: group.into(),
        }
    }

    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    pub(crate) fn validate(&self) -> Result<()> {
        validate_group("producer", &self.group)
    }
}

/// Options for a consumer role.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ConsumerOptions {
    group: String,
}

impl ConsumerOptions {
    /// Create options for `group`.
    ///
    /// Delivery concurrency is transport-specific. HTTP webhook requests may
    /// run concurrently, TCP delivery is serial, and gRPC stream consumers use
    /// [`GrpcConsumerOptions`] to configure handler concurrency.
    pub fn new(group: impl Into<String>) -> Self {
        Self {
            group: group.into(),
        }
    }

    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    pub(crate) fn validate(&self) -> Result<()> {
        validate_group("consumer", &self.group)
    }
}

/// Options for a gRPC stream consumer role.
///
/// Unlike HTTP webhook delivery and the serial TCP receive loop, gRPC stream
/// delivery supports an explicit bound on concurrently running handlers.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct GrpcConsumerOptions {
    consumer: ConsumerOptions,
    max_concurrent_handlers: usize,
}

impl GrpcConsumerOptions {
    /// Create options for `group` with serial handler execution by default.
    pub fn new(group: impl Into<String>) -> Self {
        Self {
            consumer: ConsumerOptions::new(group),
            max_concurrent_handlers: 1,
        }
    }

    /// Allow up to `max_concurrent_handlers` handlers to run concurrently.
    pub fn with_max_concurrent_handlers(mut self, max_concurrent_handlers: usize) -> Self {
        self.max_concurrent_handlers = max_concurrent_handlers;
        self
    }

    #[cfg(feature = "grpc")]
    pub(crate) const fn consumer(&self) -> &ConsumerOptions {
        &self.consumer
    }

    #[cfg(feature = "grpc")]
    pub(crate) const fn max_concurrent_handlers(&self) -> usize {
        self.max_concurrent_handlers
    }

    #[cfg(feature = "grpc")]
    pub(crate) fn validate(&self) -> Result<()> {
        self.consumer.validate()?;
        if self.max_concurrent_handlers == 0 {
            return Err(EventMeshError::Config(
                "gRPC max concurrent handlers must be greater than zero".into(),
            ));
        }
        Ok(())
    }
}

/// HTTP endpoint selection policy.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
#[non_exhaustive]
pub enum LoadBalance {
    /// Choose an endpoint at random.
    #[default]
    Random,
    /// Choose according to configured endpoint weights.
    WeightedRandom,
    /// Smooth weighted round-robin selection.
    WeightedRoundRobin,
}

/// gRPC client configuration.
#[derive(Debug, Clone)]
pub struct GrpcConfig {
    endpoint: Endpoint,
    options: ClientOptions,
    identity: Identity,
    credentials: Credentials,
}

impl GrpcConfig {
    /// Build a gRPC configuration for `endpoint`.
    pub fn new(endpoint: Endpoint) -> Self {
        Self {
            endpoint,
            options: ClientOptions::default(),
            identity: Identity::default(),
            credentials: Credentials::default(),
        }
    }

    /// Override common client options.
    pub fn with_options(mut self, options: ClientOptions) -> Self {
        self.options = options;
        self
    }

    /// Override request identity.
    pub fn with_identity(mut self, identity: Identity) -> Self {
        self.identity = identity;
        self
    }

    /// Set credentials.
    pub fn with_credentials(mut self, credentials: Credentials) -> Self {
        self.credentials = credentials;
        self
    }

    /// Return the configured server endpoint.
    pub const fn endpoint(&self) -> &Endpoint {
        &self.endpoint
    }

    /// Return shared client options.
    pub const fn options(&self) -> &ClientOptions {
        &self.options
    }

    /// Return the request identity.
    pub const fn identity(&self) -> &Identity {
        &self.identity
    }

    /// Return the configured credentials.
    pub const fn credentials(&self) -> &Credentials {
        &self.credentials
    }

    #[cfg(feature = "grpc")]
    pub(crate) fn validate(&self) -> Result<()> {
        self.options.validate()
    }

    #[cfg(feature = "grpc")]
    pub(crate) const fn request_timeout(&self) -> Duration {
        match self.options.request_timeout {
            Some(timeout) => timeout,
            None => DEFAULT_GRPC_REQUEST_TIMEOUT,
        }
    }

    #[cfg(feature = "grpc")]
    pub(crate) fn legacy(
        &self,
        producer: Option<&ProducerOptions>,
        consumer: Option<&ConsumerOptions>,
    ) -> super::GrpcClientConfig {
        let mut identity = legacy_identity(&self.identity, &self.credentials);
        if let Some(producer) = producer {
            identity.producer_group = producer.group.clone();
        }
        if let Some(consumer) = consumer {
            identity.consumer_group = consumer.group.clone();
        }
        super::GrpcClientConfig {
            server_addr: self.endpoint.authority_host(),
            server_port: self.endpoint.port,
            timeout: self.request_timeout(),
            identity,
            max_concurrent_handlers: 1,
        }
    }

    #[cfg(feature = "grpc")]
    pub(crate) fn legacy_stream(&self, consumer: &GrpcConsumerOptions) -> super::GrpcClientConfig {
        let mut config = self.legacy(None, Some(consumer.consumer()));
        config.max_concurrent_handlers = consumer.max_concurrent_handlers();
        config
    }
}

/// HTTP client configuration.
#[derive(Debug, Clone)]
pub struct HttpConfig {
    endpoints: EndpointSet,
    load_balance: LoadBalance,
    options: ClientOptions,
    identity: Identity,
    credentials: Credentials,
    use_tls: bool,
    proxy_from_env: bool,
}

impl HttpConfig {
    /// Build an HTTP configuration for a non-empty endpoint set.
    pub fn new(endpoints: EndpointSet) -> Self {
        Self {
            endpoints,
            load_balance: LoadBalance::Random,
            options: ClientOptions::default(),
            identity: Identity::default(),
            credentials: Credentials::default(),
            use_tls: false,
            proxy_from_env: false,
        }
    }

    /// Set endpoint selection policy.
    pub fn with_load_balance(mut self, load_balance: LoadBalance) -> Self {
        self.load_balance = load_balance;
        self
    }

    /// Override common client options.
    pub fn with_options(mut self, options: ClientOptions) -> Self {
        self.options = options;
        self
    }

    /// Override request identity.
    pub fn with_identity(mut self, identity: Identity) -> Self {
        self.identity = identity;
        self
    }

    /// Set credentials.
    pub fn with_credentials(mut self, credentials: Credentials) -> Self {
        self.credentials = credentials;
        self
    }

    /// Enable HTTPS.
    pub fn with_tls(mut self) -> Self {
        self.use_tls = true;
        self
    }

    /// Control whether HTTP requests use proxy settings from the environment.
    ///
    /// Disabled by default, matching the Java SDK's direct connection
    /// behavior. When enabled, reqwest honors variables such as `HTTP_PROXY`,
    /// `HTTPS_PROXY`, and `NO_PROXY`.
    pub fn with_proxy_from_env(mut self, enabled: bool) -> Self {
        self.proxy_from_env = enabled;
        self
    }

    /// Return the configured endpoints.
    pub const fn endpoints(&self) -> &EndpointSet {
        &self.endpoints
    }

    /// Return the endpoint selection policy.
    pub const fn load_balance(&self) -> LoadBalance {
        self.load_balance
    }

    /// Return shared client options.
    pub const fn options(&self) -> &ClientOptions {
        &self.options
    }

    /// Return the request identity.
    pub const fn identity(&self) -> &Identity {
        &self.identity
    }

    /// Return the configured credentials.
    pub const fn credentials(&self) -> &Credentials {
        &self.credentials
    }

    /// Whether HTTPS is enabled.
    pub const fn tls_enabled(&self) -> bool {
        self.use_tls
    }

    /// Whether proxy settings are loaded from the process environment.
    pub const fn proxy_from_env(&self) -> bool {
        self.proxy_from_env
    }

    #[cfg(feature = "http")]
    pub(crate) fn validate(&self) -> Result<()> {
        self.options.validate()
    }

    #[cfg(feature = "http")]
    pub(crate) const fn request_timeout(&self) -> Duration {
        match self.options.request_timeout {
            Some(timeout) => timeout,
            None => DEFAULT_HTTP_REQUEST_TIMEOUT,
        }
    }

    #[cfg(feature = "http")]
    pub(crate) fn legacy(
        &self,
        producer: Option<&ProducerOptions>,
        consumer: Option<&ConsumerOptions>,
    ) -> super::HttpClientConfig {
        let mut identity = legacy_identity(&self.identity, &self.credentials);
        if let Some(producer) = producer {
            identity.producer_group = producer.group.clone();
        }
        if let Some(consumer) = consumer {
            identity.consumer_group = consumer.group.clone();
        }
        let nodes = self
            .endpoints
            .0
            .iter()
            .map(|endpoint| crate::common::loadbalance::ServerNode {
                host: endpoint.authority_host(),
                port: endpoint.port,
                weight: endpoint.weight as i32,
            })
            .collect();
        super::HttpClientConfig {
            nodes,
            load_balance: match self.load_balance {
                LoadBalance::Random => crate::common::loadbalance::LoadBalance::Random,
                LoadBalance::WeightedRandom => {
                    crate::common::loadbalance::LoadBalance::WeightRandom
                }
                LoadBalance::WeightedRoundRobin => {
                    crate::common::loadbalance::LoadBalance::WeightRoundRobin
                }
            },
            use_tls: self.use_tls,
            proxy_from_env: self.proxy_from_env,
            pool_size: super::http::DEFAULT_POOL_SIZE,
            pool_idle_timeout: Duration::from_secs(super::http::DEFAULT_IDLE_TIMEOUT_SECS),
            timeout: self.request_timeout(),
            identity,
        }
    }
}

/// TCP reconnect settings.
#[derive(Debug, Clone)]
pub struct ReconnectPolicy {
    enabled: bool,
    max_retries: usize,
    initial_backoff: Duration,
    max_backoff: Duration,
}

impl Default for ReconnectPolicy {
    fn default() -> Self {
        let legacy = ReconnectConfig::default();
        Self {
            enabled: legacy.enabled,
            max_retries: legacy.max_retries,
            initial_backoff: legacy.initial_backoff,
            max_backoff: legacy.max_backoff,
        }
    }
}

impl ReconnectPolicy {
    /// Enable or disable automatic reconnect.
    pub fn with_enabled(mut self, enabled: bool) -> Self {
        self.enabled = enabled;
        self
    }

    /// Limit reconnect attempts (`usize::MAX` means indefinitely).
    pub fn with_max_retries(mut self, max_retries: usize) -> Self {
        self.max_retries = max_retries;
        self
    }

    /// Set the delay before the first reconnect attempt.
    pub fn with_initial_backoff(mut self, initial_backoff: Duration) -> Self {
        self.initial_backoff = initial_backoff;
        self
    }

    /// Cap exponential reconnect backoff at this duration.
    pub fn with_max_backoff(mut self, max_backoff: Duration) -> Self {
        self.max_backoff = max_backoff;
        self
    }

    /// Whether reconnection is enabled.
    pub const fn enabled(&self) -> bool {
        self.enabled
    }

    /// The maximum number of reconnect attempts.
    pub const fn max_retries(&self) -> usize {
        self.max_retries
    }

    /// The initial reconnect delay.
    pub const fn initial_backoff(&self) -> Duration {
        self.initial_backoff
    }

    /// The maximum reconnect delay.
    pub const fn max_backoff(&self) -> Duration {
        self.max_backoff
    }

    #[cfg(feature = "tcp")]
    fn validate(&self) -> Result<()> {
        validate_non_zero_duration("TCP reconnect initial backoff", self.initial_backoff)?;
        validate_non_zero_duration("TCP reconnect maximum backoff", self.max_backoff)?;
        if self.initial_backoff > self.max_backoff {
            return Err(EventMeshError::Config(
                "TCP reconnect initial backoff must not exceed maximum backoff".into(),
            ));
        }
        Ok(())
    }
}

/// TCP client configuration.
#[derive(Debug, Clone)]
pub struct TcpConfig {
    endpoint: Endpoint,
    options: ClientOptions,
    identity: Identity,
    credentials: Credentials,
    reconnect: ReconnectPolicy,
    heartbeat_interval: Duration,
    connect_timeout: Duration,
    control_timeout: Duration,
}

impl TcpConfig {
    /// Build a TCP configuration for `endpoint`.
    pub fn new(endpoint: Endpoint) -> Self {
        Self {
            endpoint,
            options: ClientOptions::default(),
            identity: Identity::default(),
            credentials: Credentials::default(),
            reconnect: ReconnectPolicy::default(),
            heartbeat_interval: Duration::from_secs(30),
            connect_timeout: DEFAULT_TCP_CONNECT_TIMEOUT,
            control_timeout: DEFAULT_TCP_CONTROL_TIMEOUT,
        }
    }

    /// Override common client options.
    pub fn with_options(mut self, options: ClientOptions) -> Self {
        self.options = options;
        self
    }

    /// Override request identity.
    pub fn with_identity(mut self, identity: Identity) -> Self {
        self.identity = identity;
        self
    }

    /// Set credentials.
    pub fn with_credentials(mut self, credentials: Credentials) -> Self {
        self.credentials = credentials;
        self
    }

    /// Configure reconnection.
    pub fn with_reconnect(mut self, reconnect: ReconnectPolicy) -> Self {
        self.reconnect = reconnect;
        self
    }

    /// Override the heartbeat interval.
    pub fn with_heartbeat_interval(mut self, heartbeat_interval: Duration) -> Self {
        self.heartbeat_interval = heartbeat_interval;
        self
    }

    /// Override the TCP socket connection timeout.
    pub fn with_connect_timeout(mut self, connect_timeout: Duration) -> Self {
        self.connect_timeout = connect_timeout;
        self
    }

    /// Override the timeout for HELLO, LISTEN, subscribe, and unsubscribe
    /// responses.
    pub fn with_control_timeout(mut self, control_timeout: Duration) -> Self {
        self.control_timeout = control_timeout;
        self
    }

    /// Return the configured server endpoint.
    pub const fn endpoint(&self) -> &Endpoint {
        &self.endpoint
    }

    /// Return shared client options.
    pub const fn options(&self) -> &ClientOptions {
        &self.options
    }

    /// Return the request identity.
    pub const fn identity(&self) -> &Identity {
        &self.identity
    }

    /// Return the configured credentials.
    pub const fn credentials(&self) -> &Credentials {
        &self.credentials
    }

    /// Return the reconnect policy.
    pub const fn reconnect(&self) -> &ReconnectPolicy {
        &self.reconnect
    }

    /// Return the heartbeat interval.
    pub const fn heartbeat_interval(&self) -> Duration {
        self.heartbeat_interval
    }

    /// Return the TCP socket connection timeout.
    pub const fn connect_timeout(&self) -> Duration {
        self.connect_timeout
    }

    /// Return the TCP protocol-control response timeout.
    pub const fn control_timeout(&self) -> Duration {
        self.control_timeout
    }

    #[cfg(feature = "tcp")]
    pub(crate) fn validate(&self) -> Result<()> {
        self.options.validate()?;
        validate_non_zero_duration("TCP heartbeat interval", self.heartbeat_interval)?;
        validate_non_zero_duration("TCP connect timeout", self.connect_timeout)?;
        validate_non_zero_duration("TCP control timeout", self.control_timeout)?;
        self.reconnect.validate()
    }

    #[cfg(feature = "tcp")]
    pub(crate) const fn request_timeout(&self) -> Duration {
        match self.options.request_timeout {
            Some(timeout) => timeout,
            None => DEFAULT_TCP_REQUEST_TIMEOUT,
        }
    }

    #[cfg(feature = "tcp")]
    pub(crate) fn legacy(
        &self,
        producer: Option<&ProducerOptions>,
        consumer: Option<&ConsumerOptions>,
    ) -> super::TcpClientConfig {
        let mut identity = legacy_identity(&self.identity, &self.credentials);
        if let Some(producer) = producer {
            identity.producer_group = producer.group.clone();
        }
        if let Some(consumer) = consumer {
            identity.consumer_group = consumer.group.clone();
        }
        super::TcpClientConfig {
            server_addr: self.endpoint.authority_host(),
            server_port: self.endpoint.port,
            connect_timeout: self.connect_timeout,
            control_timeout: self.control_timeout,
            request_timeout: self.request_timeout(),
            heartbeat_interval: self.heartbeat_interval,
            reconnect: ReconnectConfig {
                enabled: self.reconnect.enabled,
                max_retries: self.reconnect.max_retries,
                initial_backoff: self.reconnect.initial_backoff,
                max_backoff: self.reconnect.max_backoff,
            },
            identity,
        }
    }
}

#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
fn validate_non_zero_duration(name: &str, value: Duration) -> Result<()> {
    if value.is_zero() {
        return Err(EventMeshError::Config(format!(
            "{name} must be greater than zero"
        )));
    }
    Ok(())
}

#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
fn validate_group(role: &str, group: &str) -> Result<()> {
    if group.trim().is_empty() {
        return Err(EventMeshError::Config(format!(
            "{role} group must not be empty"
        )));
    }
    Ok(())
}

#[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
fn legacy_identity(identity: &Identity, credentials: &Credentials) -> ClientIdentity {
    ClientIdentity {
        env: identity.env.clone(),
        idc: identity.idc.clone(),
        sys: identity.system.clone(),
        pid: identity.process_id.clone(),
        ip: identity.ip.clone(),
        language: identity.language.clone(),
        username: credentials.username.clone().unwrap_or_default(),
        password: credentials.password.clone().unwrap_or_default(),
        token: credentials.token.clone(),
        producer_group: "DefaultProducerGroup".into(),
        consumer_group: "DefaultConsumerGroup".into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn endpoint_brackets_ipv6_authorities() {
        let endpoint = Endpoint::new("::1", 10_205).unwrap();
        assert_eq!(endpoint.authority(), "[::1]:10205");
    }

    #[test]
    fn endpoint_rejects_a_url_or_embedded_port() {
        assert!(Endpoint::new("http://localhost", 10_105).is_err());
        assert!(Endpoint::new("localhost:10105", 10_105).is_err());
    }

    #[cfg(feature = "grpc")]
    #[test]
    fn grpc_legacy_config_brackets_ipv6_host() {
        let legacy = GrpcConfig::new(Endpoint::new("::1", 10_205).unwrap()).legacy(None, None);
        assert_eq!(legacy.authority(), "[::1]:10205");
    }

    #[cfg(feature = "grpc")]
    #[test]
    fn grpc_stream_options_own_handler_concurrency() {
        let options = GrpcConsumerOptions::new("orders").with_max_concurrent_handlers(8);
        let legacy =
            GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205).unwrap()).legacy_stream(&options);
        assert_eq!(legacy.identity.consumer_group, "orders");
        assert_eq!(legacy.max_concurrent_handlers, 8);
    }

    #[cfg(feature = "http")]
    #[test]
    fn http_legacy_config_brackets_ipv6_host() {
        let endpoints = EndpointSet::new([Endpoint::new("::1", 10_105).unwrap()]).unwrap();
        let legacy = HttpConfig::new(endpoints).legacy(None, None);
        assert_eq!(legacy.nodes[0].addr(), "[::1]:10105");
    }

    #[cfg(feature = "http")]
    #[test]
    fn http_proxy_from_env_is_explicit() {
        let endpoints = EndpointSet::new([Endpoint::new("127.0.0.1", 10_105).unwrap()]).unwrap();
        let direct = HttpConfig::new(endpoints.clone()).legacy(None, None);
        let proxied = HttpConfig::new(endpoints)
            .with_proxy_from_env(true)
            .legacy(None, None);
        assert!(!direct.proxy_from_env);
        assert!(proxied.proxy_from_env);
    }

    #[cfg(feature = "tcp")]
    #[test]
    fn tcp_legacy_config_brackets_ipv6_host() {
        let legacy = TcpConfig::new(Endpoint::new("::1", 10_000).unwrap()).legacy(None, None);
        assert_eq!(legacy.authority(), "[::1]:10000");
    }

    #[cfg(feature = "tcp")]
    #[test]
    fn tcp_legacy_config_preserves_each_timeout_class() {
        let legacy = TcpConfig::new(Endpoint::new("127.0.0.1", 10_000).unwrap())
            .with_options(ClientOptions::default().with_request_timeout(Duration::from_secs(3)))
            .with_connect_timeout(Duration::from_secs(1))
            .with_control_timeout(Duration::from_secs(2))
            .legacy(None, None);
        assert_eq!(legacy.connect_timeout, Duration::from_secs(1));
        assert_eq!(legacy.control_timeout, Duration::from_secs(2));
        assert_eq!(legacy.request_timeout, Duration::from_secs(3));
    }

    #[test]
    fn endpoint_set_requires_an_endpoint() {
        assert!(EndpointSet::new(Vec::new()).is_err());
    }

    #[cfg(all(feature = "grpc", feature = "http", feature = "tcp"))]
    #[test]
    fn transports_keep_their_protocol_specific_default_timeouts() {
        let endpoint = Endpoint::new("127.0.0.1", 10_205).unwrap();
        assert_eq!(
            GrpcConfig::new(endpoint.clone()).request_timeout(),
            DEFAULT_GRPC_REQUEST_TIMEOUT
        );
        let endpoints = EndpointSet::new([endpoint.clone()]).unwrap();
        assert_eq!(
            HttpConfig::new(endpoints).request_timeout(),
            DEFAULT_HTTP_REQUEST_TIMEOUT
        );
        assert_eq!(
            TcpConfig::new(endpoint.clone()).request_timeout(),
            DEFAULT_TCP_REQUEST_TIMEOUT
        );
        let tcp = TcpConfig::new(endpoint);
        assert_eq!(tcp.connect_timeout(), DEFAULT_TCP_CONNECT_TIMEOUT);
        assert_eq!(tcp.control_timeout(), DEFAULT_TCP_CONTROL_TIMEOUT);
    }

    #[test]
    fn endpoint_weight_must_fit_the_transport_representation() {
        let endpoint = Endpoint::new("127.0.0.1", 10_105).unwrap();
        assert!(endpoint.with_weight(i32::MAX as u32 + 1).is_err());
    }

    #[test]
    fn endpoint_set_rejects_a_weight_sum_that_exceeds_i32() {
        let first = Endpoint::new("first", 10_105)
            .unwrap()
            .with_weight(i32::MAX as u32)
            .unwrap();
        let second = Endpoint::new("second", 10_105).unwrap();
        assert!(EndpointSet::new([first.clone()]).is_ok());
        assert!(EndpointSet::new([first, second]).is_err());
    }

    #[cfg(feature = "grpc")]
    #[test]
    fn grpc_config_rejects_zero_request_timeout() {
        let config = GrpcConfig::new(Endpoint::new("127.0.0.1", 10_205).unwrap())
            .with_options(ClientOptions::default().with_request_timeout(Duration::ZERO));
        assert!(config.validate().is_err());
    }

    #[cfg(feature = "tcp")]
    #[test]
    fn tcp_config_rejects_invalid_timing_values() {
        let endpoint = Endpoint::new("127.0.0.1", 10_000).unwrap();
        assert!(TcpConfig::new(endpoint.clone())
            .with_heartbeat_interval(Duration::ZERO)
            .validate()
            .is_err());
        assert!(TcpConfig::new(endpoint.clone())
            .with_connect_timeout(Duration::ZERO)
            .validate()
            .is_err());
        assert!(TcpConfig::new(endpoint.clone())
            .with_control_timeout(Duration::ZERO)
            .validate()
            .is_err());
        assert!(TcpConfig::new(endpoint)
            .with_reconnect(
                ReconnectPolicy::default()
                    .with_initial_backoff(Duration::from_secs(2))
                    .with_max_backoff(Duration::from_secs(1)),
            )
            .validate()
            .is_err());
    }

    #[cfg(any(feature = "grpc", feature = "http", feature = "tcp"))]
    #[test]
    fn role_groups_must_not_be_blank() {
        assert!(ProducerOptions::new("  ").validate().is_err());
        assert!(ConsumerOptions::new("").validate().is_err());
    }

    #[cfg(feature = "grpc")]
    #[test]
    fn grpc_handler_concurrency_must_not_be_zero() {
        assert!(GrpcConsumerOptions::new("orders")
            .with_max_concurrent_handlers(0)
            .validate()
            .is_err());
    }
}
