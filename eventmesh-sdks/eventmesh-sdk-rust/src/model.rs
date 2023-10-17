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

use std::fmt::{Display, Formatter};

#[derive(Debug)]
pub enum EventMeshProtocolType {
    CloudEvents,
    EventMeshMessage,
    OpenMessage,
}

impl Display for EventMeshProtocolType {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            EventMeshProtocolType::CloudEvents => {
                writeln!(f, "cloudevents")
            }
            EventMeshProtocolType::EventMeshMessage => {
                writeln!(f, "eventmeshmessage")
            }
            EventMeshProtocolType::OpenMessage => {
                writeln!(f, "openmessage")
            }
        }
    }
}

impl EventMeshProtocolType {
    pub fn protocol_type_name(&self) -> &'static str {
        match self {
            EventMeshProtocolType::CloudEvents => "cloudevents",
            EventMeshProtocolType::EventMeshMessage => "eventmeshmessage",
            EventMeshProtocolType::OpenMessage => "openmessage",
        }
    }
}

pub mod message;

pub(crate) mod convert;
pub(crate) mod response;
pub mod subscription;
