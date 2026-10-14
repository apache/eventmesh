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

//! Classification of reserved EventMesh wire attributes.

pub(crate) fn is_transport_property(key: &str) -> bool {
    matches!(
        key,
        "protocoltype"
            | "protocolversion"
            | "protocoldesc"
            | "code"
            | "version"
            | "env"
            | "idc"
            | "ip"
            | "pid"
            | "sys"
            | "username"
            | "passwd"
            | "token"
            | "language"
            | "producergroup"
    )
}

// Exact runtime/storage keys, not a prefix rule: a business property named
// `requestid`, for example, must not be mistaken for a routing attribute.
pub(crate) fn is_delivery_property(key: &str) -> bool {
    matches!(
        key,
        "cluster"
            | "consumergroup"
            | "url"
            | "clienttype"
            | "submessagetype"
            | "msgtype"
            | "req0sys"
            | "req0ip"
            | "req0idc"
            | "req0group"
            | "rsp0sys"
            | "rsp0ip"
            | "rsp0idc"
            | "rsp0group"
            | "rsp0url"
            | "reqc2eventmeshtimestamp"
            | "reqeventmesh2mqtimestamp"
            | "reqmq2eventmeshtimestamp"
            | "reqeventmesh2ctimestamp"
            | "rspc2eventmeshtimestamp"
            | "rspeventmesh2mqtimestamp"
            | "rspmq2eventmeshtimestamp"
            | "rspeventmesh2ctimestamp"
            | "reqsendeventmeship"
            | "reqreceiveeventmeship"
            | "rspsendeventmeship"
            | "rspreceiveeventmeship"
            | "correlation99id"
            | "reply99to99client"
            | "arrive99time"
            | "push99reply99time"
            | "msg99type"
            | "bornhost"
            | "borntimestamp"
            | "storehost"
            | "storetimestamp"
    )
}

pub(crate) fn is_reserved_property(key: &str) -> bool {
    is_transport_property(key)
        || is_delivery_property(key)
        || matches!(
            key,
            "ttl"
                | "seqnum"
                | "bizseqno"
                | "uniqueid"
                | "topic"
                | "content"
                | "datacontenttype"
                | "id"
                | "source"
                | "specversion"
                | "type"
                | "dataschema"
                | "subject"
                | "time"
                | "statuscode"
                | "responsemessage"
                | "subscription_reply"
        )
}
