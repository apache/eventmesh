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
pub const DEFAULT_EVENTMESH_MESSAGE_TTL: i32 = 4000;
pub const SDK_STREAM_URL: &str = "grpc_stream";

pub struct DataContentType;

impl DataContentType {
    pub const TEXT_PLAIN: &'static str = "text/plain";
    pub const JSON: &'static str = "application/json";
}

pub(crate) struct SpecVersion;

#[allow(dead_code)]
impl SpecVersion {
    pub(crate) const V1: &'static str = "1.0";
    pub(crate) const V03: &'static str = "0.3";
}

#[derive(Debug, PartialEq, Eq)]
pub enum ClientType {
    PUB,
    SUB,
}

impl ClientType {
    pub fn get(type_: i32) -> Option<ClientType> {
        match type_ {
            1 => Some(ClientType::PUB),
            2 => Some(ClientType::SUB),
            _ => None,
        }
    }

    pub fn contains(client_type: i32) -> bool {
        match client_type {
            1 | 2 => true,
            _ => false,
        }
    }
}
