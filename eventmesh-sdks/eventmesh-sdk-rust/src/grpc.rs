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

//! gRPC channel and role API.

use crate::config::GrpcConfig;
use crate::error::Result;
use crate::transport::grpc::ChannelClient;

pub use crate::transport::grpc::{GrpcProducer, GrpcStreamConsumer, GrpcWebhookConsumer};

/// A connected EventMesh gRPC channel.
///
/// Create the channel inside the Tokio runtime that will use it. Producers and
/// consumers built from clones of the channel share one multiplexed HTTP/2
/// connection. To use EventMesh from another runtime, connect another channel
/// in that runtime instead of moving this value across runtime lifetimes.
#[derive(Clone)]
pub struct GrpcChannel {
    config: GrpcConfig,
    inner: ChannelClient,
}

impl GrpcChannel {
    /// Validate `config` and connect on the current Tokio runtime.
    pub async fn connect(config: GrpcConfig) -> Result<Self> {
        let inner = ChannelClient::connect(&config).await?;
        Ok(Self { config, inner })
    }

    pub(crate) fn into_parts(self) -> (ChannelClient, GrpcConfig) {
        (self.inner, self.config)
    }

    #[cfg(test)]
    pub(crate) fn connect_lazy(config: GrpcConfig) -> Result<Self> {
        let inner = ChannelClient::connect_lazy(&config)?;
        Ok(Self { config, inner })
    }
}
