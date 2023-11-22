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
pub struct ProtocolKey;

#[allow(dead_code)]
impl ProtocolKey {
    // EventMesh extensions
    pub const ENV: &'static str = "env";
    pub const IDC: &'static str = "idc";
    pub const SYS: &'static str = "sys";
    pub const PID: &'static str = "pid";
    pub const IP: &'static str = "ip";
    pub const USERNAME: &'static str = "username";
    pub const PASSWD: &'static str = "passwd";
    pub const LANGUAGE: &'static str = "language";
    pub const PROTOCOL_TYPE: &'static str = "protocoltype";
    pub const PROTOCOL_VERSION: &'static str = "protocolversion";
    pub const PROTOCOL_DESC: &'static str = "protocoldesc";
    pub const SEQ_NUM: &'static str = "seqnum";
    pub const UNIQUE_ID: &'static str = "uniqueid";
    pub const TTL: &'static str = "ttl";
    pub const PRODUCERGROUP: &'static str = "producergroup";
    pub const CONSUMERGROUP: &'static str = "consumergroup";
    pub const TAG: &'static str = "tag";
    pub const CONTENT_TYPE: &'static str = "contenttype";
    pub const PROPERTY_MESSAGE_CLUSTER: &'static str = "cluster";
    pub const URL: &'static str = "url";
    pub const CLIENT_TYPE: &'static str = "clienttype";
    pub const GRPC_RESPONSE_CODE: &'static str = "status_code";
    pub const GRPC_RESPONSE_MESSAGE: &'static str = "response_message";
    pub const GRPC_RESPONSE_TIME: &'static str = "time";

    // CloudEvents spec
    pub const ID: &'static str = "id";
    pub const SOURCE: &'static str = "source";
    pub const SPECVERSION: &'static str = "specversion";
    pub const TYPE: &'static str = "type";
    pub const DATA_CONTENT_TYPE: &'static str = "datacontenttype";
    pub const DATA_SCHEMA: &'static str = "dataschema";
    pub const SUBJECT: &'static str = "subject";
    pub const TIME: &'static str = "time";
    pub const EVENT_DATA: &'static str = "eventdata";

    //protocol desc
    pub const PROTOCOL_DESC_GRPC_CLOUD_EVENT: &'static str = "grpc-cloud-event";

    pub const CLOUD_EVENTS_PROTOCOL_NAME: &'static str = "cloudevents";

    pub const CLOUDEVENT_CONTENT_TYPE: &'static str = "application/cloudevents+json";
}
