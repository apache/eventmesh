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

//! HTTP producer.

use crate::common::status_code::RequestCode;
use crate::config::ProducerOptions;
use crate::error::{Error, Result};
use crate::message::{Message, PublishReceipt};
use crate::model::EventMeshProtocolType;
use crate::transport::http::client::HttpClient;
use crate::transport::http::codec::{self, uri};

/// HTTP publishing capability created by [`HttpClient::producer`].
pub struct HttpProducer {
    client: HttpClient,
    options: ProducerOptions,
}

impl HttpProducer {
    pub(crate) fn new(client: HttpClient, options: ProducerOptions) -> Result<Self> {
        options.validate()?;
        Ok(Self { client, options })
    }

    /// Publish one native EventMesh message or CloudEvent and await acceptance.
    pub async fn publish(&self, message: Message) -> Result<PublishReceipt> {
        let (body, protocol_type) = match message {
            Message::EventMesh(message) => {
                crate::transport::validation::validate_native_publish(&message)?;
                (
                    codec::encode_publish(&message, self.options.group()),
                    EventMeshProtocolType::EventMeshMessage,
                )
            }
            #[cfg(feature = "cloud_events")]
            Message::CloudEvent(event) => (
                codec::encode_cloud_event(&event, self.options.group())?,
                EventMeshProtocolType::CloudEvents,
            ),
        };
        self.send(body, protocol_type).await
    }

    async fn send(
        &self,
        body: Vec<(String, String)>,
        protocol_type: EventMeshProtocolType,
    ) -> Result<PublishReceipt> {
        let config = self.client.config();
        let headers = codec::build_headers(
            RequestCode::MSG_SEND_ASYNC,
            protocol_type,
            config.identity(),
            config.credentials(),
        );
        let text = self
            .client
            .post_form(uri::ROOT, &body, &headers, config.request_timeout())
            .await?;
        let response = codec::parse_response(&text)?;
        if !response.is_success() {
            return Err(Error::Server {
                code: response.code.unwrap_or(-1) as i32,
                message: response.message.unwrap_or_else(|| "publish failed".into()),
            });
        }
        Ok(PublishReceipt::from_response(response))
    }
}
