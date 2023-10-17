/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#[derive(Debug, Clone)]
pub struct EventMeshGrpcClientConfig {
    pub(crate) server_addr: String,
    pub(crate) server_port: u32,
    pub(crate) env: String,
    pub(crate) consumer_group: Option<String>,
    pub(crate) producer_group: Option<String>,
    pub(crate) idc: String,
    pub(crate) sys: String,
    pub(crate) user_name: String,
    pub(crate) password: String,
    pub(crate) language: String,
    pub(crate) use_tls: Option<bool>,
    pub(crate) time_out: Option<u64>,
}

impl Default for EventMeshGrpcClientConfig {
    fn default() -> Self {
        Self {
            server_addr: "localhost".to_string(),
            server_port: 10205,
            env: "dev".to_string(),
            consumer_group: Some("DefaultConsumerGroup".to_string()),
            producer_group: Some("DefaultProducerGroup".to_string()),
            idc: "default".to_string(),
            sys: "evetmesh".to_string(),
            user_name: "username".to_string(),
            password: "password".to_string(),
            language: "Rust".to_string(),
            use_tls: Some(false),
            time_out: Some(5000),
        }
    }
}

impl ToString for EventMeshGrpcClientConfig {
    fn to_string(&self) -> String {
        format!(
            "ClientConfig={{ServerAddr={},ServerPort={},env={:?},\
            idc={:?},producerGroup={:?},consumerGroup={:?},\
            sys={:?},userName={:?},password=***,\
            useTls={:?},timeOut={:?}}}",
            self.server_addr,
            self.server_port,
            self.env,
            self.idc,
            self.producer_group,
            self.consumer_group,
            self.sys,
            self.user_name,
            self.use_tls,
            self.time_out
        )
    }
}

#[allow(dead_code)]
impl EventMeshGrpcClientConfig {
    pub fn new() -> Self {
        Default::default()
    }

    pub fn set_server_addr(mut self, server_addr: String) -> Self {
        self.server_addr = server_addr;
        self
    }
    pub fn set_server_port(mut self, server_port: u32) -> Self {
        self.server_port = server_port;
        self
    }
    pub fn set_env(mut self, env: String) -> Self {
        self.env = env;
        self
    }
    pub fn set_consumer_group(mut self, consumer_group: String) -> Self {
        self.consumer_group = Some(consumer_group);
        self
    }
    pub fn set_producer_group(mut self, producer_group: String) -> Self {
        self.producer_group = Some(producer_group);
        self
    }
    pub fn set_idc(mut self, idc: String) -> Self {
        self.idc = idc;
        self
    }
    pub fn set_sys(mut self, sys: String) -> Self {
        self.sys = sys;
        self
    }
    pub fn set_user_name(mut self, user_name: String) -> Self {
        self.user_name = user_name;
        self
    }
    pub fn set_password(mut self, password: String) -> Self {
        self.password = password;
        self
    }
    pub fn set_language(mut self, language: String) -> Self {
        self.language = language;
        self
    }
    pub fn set_use_tls(mut self, use_tls: bool) -> Self {
        self.use_tls = Some(use_tls);
        self
    }
    pub fn set_time_out(mut self, time_out: u64) -> Self {
        self.time_out = Some(time_out);
        self
    }
}
