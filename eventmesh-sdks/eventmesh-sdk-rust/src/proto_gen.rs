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

//! Crate-private generated gRPC stubs, type aliases, and attribute helpers.

#[allow(clippy::enum_variant_names)]
pub(crate) mod pb {
    tonic::include_proto!("org.apache.eventmesh.cloudevents.v1");
}

// ---- convenience aliases used throughout the gRPC transport ----
pub(crate) use pb::cloud_event::cloud_event_attribute_value::Attr as PbAttr;
pub(crate) use pb::cloud_event::CloudEventAttributeValue as PbCloudEventAttributeValue;
pub(crate) use pb::cloud_event::Data as PbData;
pub(crate) use pb::consumer_service_client::ConsumerServiceClient;
pub(crate) use pb::heartbeat_service_client::HeartbeatServiceClient;
pub(crate) use pb::publisher_service_client::PublisherServiceClient;
pub(crate) use pb::CloudEvent as PbCloudEvent;
pub(crate) use pb::CloudEventBatch as PbCloudEventBatch;

/// Build a string-valued CloudEvent attribute.
pub(crate) fn attr_str(value: impl Into<String>) -> PbCloudEventAttributeValue {
    PbCloudEventAttributeValue {
        attr: Some(PbAttr::CeString(value.into())),
    }
}

/// Build an int32-valued CloudEvent attribute.
pub(crate) fn attr_int(value: i32) -> PbCloudEventAttributeValue {
    PbCloudEventAttributeValue {
        attr: Some(PbAttr::CeInteger(value)),
    }
}

/// Read an attribute's value as a string. EventMesh only ever uses the
/// string/uri/uri-ref variants for its protocol attributes, but we handle the
/// others defensively (no `unsafe`).
pub(crate) fn attr_as_str(value: &PbCloudEventAttributeValue) -> String {
    match &value.attr {
        Some(PbAttr::CeString(s)) | Some(PbAttr::CeUri(s)) | Some(PbAttr::CeUriRef(s)) => s.clone(),
        Some(PbAttr::CeBoolean(b)) => b.to_string(),
        Some(PbAttr::CeInteger(i)) => i.to_string(),
        Some(PbAttr::CeBytes(b)) => String::from_utf8_lossy(b).into_owned(),
        Some(PbAttr::CeTimestamp(ts)) => format!("{}.{}", ts.seconds, ts.nanos),
        None => String::new(),
    }
}
