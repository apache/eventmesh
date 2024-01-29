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

use eventmesh::common::grpc_eventmesh_message_utils::{EventMeshCloudEventUtils, ProtoSupport};
use eventmesh::config::EventMeshGrpcClientConfig;
use eventmesh::model::EventMeshProtocolType;
use eventmesh::proto_cloud_event::PbAttr;

#[test]
fn test_proto_support_is_text_content() {
    assert!(ProtoSupport::is_text_content("text/plain"));
    assert!(ProtoSupport::is_text_content("text/html"));
    assert!(ProtoSupport::is_text_content("application/json"));
    assert!(ProtoSupport::is_text_content("application/xml"));
    assert!(!ProtoSupport::is_text_content("application/json+foo"));
    assert!(!ProtoSupport::is_text_content("application/xml+bar"));
    assert!(!ProtoSupport::is_text_content(""));
    assert!(!ProtoSupport::is_text_content("application/octet-stream"));
}

#[test]
fn test_proto_support_is_proto_content() {
    assert!(ProtoSupport::is_proto_content("application/protobuf"));
    assert!(!ProtoSupport::is_proto_content(""));
    assert!(!ProtoSupport::is_proto_content("application/json"));
    assert!(!ProtoSupport::is_proto_content("text/plain"));
}

#[test]
fn test_event_mesh_cloud_event_utils_build_common_cloud_event_attributes() {
    let client_config = EventMeshGrpcClientConfig::default()
        .set_env("test_env".to_string())
        .set_idc("test_idc".to_string());
    let protocol_type = EventMeshProtocolType::CloudEvents;
    let attribute_map = EventMeshCloudEventUtils::build_common_cloud_event_attributes(
        &client_config,
        protocol_type,
    );

    assert_eq!(
        *attribute_map.get("env").map(|attr| &attr.attr).unwrap(),
        Some(PbAttr::CeString("test_env".to_string()))
    );
    assert_eq!(
        *attribute_map.get("idc").map(|attr| &attr.attr).unwrap(),
        Some(PbAttr::CeString("test_idc".to_string()))
    );
}
