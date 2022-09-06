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

use crate::constants;

use super::eventmesh_grpc::RequestHeader;

#[derive(Debug, Clone)]
pub struct EventMeshGrpcConfig {
    pub eventmesh_addr: String,
    pub env: String,
    pub idc: String,
    pub ip: String,
    pub pid: String,
    pub sys: String,
    pub user_name: String,
    pub password: String,
    pub producer_group: String,
    pub consumer_group:String
}

impl EventMeshGrpcConfig {
    pub(crate) fn build_header(&self) -> RequestHeader {
        RequestHeader {
            env: self.env.to_string(),
            region: String::from(""),
            idc: self.idc.to_string(),
            ip: self.ip.to_string(),
            pid: self.pid.to_string(),
            sys: self.sys.to_string(),
            username: self.user_name.to_string(),
            password: self.password.to_string(),
            language: String::from("RUST"),
            //TODO: cloudevent openmessage
            protocol_type: String::from(constants::EM_MESSAGE_PROTOCOL),
            protocol_version: String::from("1.0"),
            protocol_desc: String::from("grpc"),
        }
    }
}
