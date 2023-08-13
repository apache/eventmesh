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

use super::{config::EventMeshHttpConfig, protocol_key};
use crate::{
    http::{protocol_version, request_code},
    message::{EventMeshMessage, EventMeshMessageResp}, constants,
};
use anyhow::Result;
use reqwest::header::{self, HeaderValue};
use serde::{Deserialize, Serialize};

pub struct EventMeshMessageProducer {
    client: reqwest::Client,
    producer_group: String,
    url: String,
}
macro_rules! header {
    ($header:expr,$key:expr, $value:expr) => {
        $header.insert($key, HeaderValue::from_str(&$value)?);
    };
}
#[derive(Serialize)]
struct Body {
    producergroup: String,
    #[serde(flatten)]
    body: EventMeshMessage,
}
impl EventMeshMessageProducer {
    pub fn new(config: &EventMeshHttpConfig) -> Result<Self> {
        let mut headers = header::HeaderMap::with_capacity(12);

        header!(headers, protocol_key::CLIENT_ENV, config.env);
        header!(headers, protocol_key::CLIENT_IDC, config.idc);
        header!(headers, protocol_key::CLIENT_IP, config.ip);
        header!(headers, protocol_key::CLIENT_PID, config.pid);
        header!(headers, protocol_key::CLIENT_SYS, config.sys);
        header!(headers, protocol_key::CLIENT_USERNAME, config.user_name);
        header!(headers, protocol_key::CLIENT_PASSWD, config.password);
        header!(
            headers,
            protocol_key::VERSION,
            protocol_version::ProtocolVersion::V1.to_string()
        );
        header!(
            headers,
            protocol_key::PROTOCOL_TYPE,
            constants::EM_MESSAGE_PROTOCOL
        );
        header!(
            headers,
            protocol_key::RPOTOCOL_DESC,
            constants::PROTOCOL_DESC
        );
        // SpecVersion::V03 => "0.3",
        // SpecVersion::V10 => "1.0",
        header!(headers, protocol_key::PROTOCOL_VERSION, "1.0");
        header!(headers, protocol_key::LANGUAGE, "RUST");
        let client = reqwest::ClientBuilder::new().default_headers(headers);
        Ok(EventMeshMessageProducer {
            client: client.build()?,
            producer_group: config.producergroup.clone(),
            url: config.eventmesh_addr.clone(),
        })
    }

    pub async fn publish(&self, message: EventMeshMessage) -> Result<EventMeshMessageResp> {
        let body = Body {
            producergroup: self.producer_group.clone(),
            body: message,
        };
        let resp = self
            .client
            .post(&self.url)
            .header(
                protocol_key::REQUEST_CODE,
                request_code::RequestCode::MsgSendAsync.to_code(),
            )
            .form(&body)
            .send()
            .await?;
        let body: EventMeshMessageResp = resp.json().await?;
        Ok(body)
    }
    pub async fn request(&self, message: EventMeshMessage) -> Result<EventMeshMessage> {
        let body = Body {
            producergroup: self.producer_group.clone(),
            body: message,
        };
        let resp = self
            .client
            .post(&self.url)
            .header(
                protocol_key::REQUEST_CODE,
                request_code::RequestCode::MsgSendSync.to_code(),
            )
            .form(&body)
            .send()
            .await?;
        let body: EventMeshMessage = resp.json().await?;
        Ok(body)
    }
}
