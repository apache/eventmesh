//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  The ASF
// licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//

//! CloudEvent attribute keys used across protocols.
//!
//! These mirror `org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey`
//! on the server. Attribute keys are lowercase strings stored in the
//! CloudEvent `attributes` map (gRPC) or sent as HTTP/TCP headers.

/// Container for all well-known attribute key constants.
pub struct ProtocolKey;
#[allow(dead_code)]
impl ProtocolKey {
    // ---- client identity (carried in every request) ----
    pub const ENV: &str = "env";
    pub const IDC: &str = "idc";
    pub const SYS: &str = "sys";
    pub const PID: &str = "pid";
    pub const IP: &str = "ip";
    pub const USERNAME: &str = "username";
    pub const PASSWD: &str = "passwd";
    pub const LANGUAGE: &str = "language";

    // ---- protocol descriptors ----
    pub const PROTOCOL_TYPE: &str = "protocoltype";
    pub const PROTOCOL_VERSION: &str = "protocolversion";
    pub const PROTOCOL_DESC: &str = "protocoldesc";
    pub const PROTOCOL_DESC_GRPC_CLOUD_EVENT: &str = "grpc-cloud-event";
    pub const CLOUD_EVENTS_PROTOCOL_NAME: &str = "cloudevents";

    // ---- message routing ----
    pub const SEQ_NUM: &str = "seqnum";
    pub const UNIQUE_ID: &str = "uniqueid";
    pub const TTL: &str = "ttl";
    pub const PRODUCERGROUP: &str = "producergroup";
    pub const CONSUMERGROUP: &str = "consumergroup";
    pub const TAG: &str = "tag";
    pub const URL: &str = "url";
    pub const CLIENT_TYPE: &str = "clienttype";
    pub const SUB_MESSAGE_TYPE: &str = "submessagetype";
    pub const PROPERTY_MESSAGE_CLUSTER: &str = "cluster";

    // ---- CloudEvents spec attributes (lowercased for the attributes map) ----
    pub const ID: &str = "id";
    pub const SOURCE: &str = "source";
    pub const SPECVERSION: &str = "specversion";
    pub const TYPE: &str = "type";
    pub const DATA_CONTENT_TYPE: &str = "datacontenttype";
    pub const DATA_SCHEMA: &str = "dataschema";
    pub const SUBJECT: &str = "subject";
    pub const TIME: &str = "time";
    pub const EVENT_DATA: &str = "eventdata";

    // ---- server -> client response (gRPC) ----
    pub const GRPC_RESPONSE_CODE: &str = "statuscode";
    pub const GRPC_RESPONSE_MESSAGE: &str = "responsemessage";
    pub const GRPC_RESPONSE_TIME: &str = "time";

    // ---- subscription reply marker ----
    pub const SUBSCRIPTION_REPLY: &str = "subscription_reply";
}
