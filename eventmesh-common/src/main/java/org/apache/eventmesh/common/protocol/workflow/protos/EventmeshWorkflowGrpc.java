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

package org.apache.eventmesh.common.protocol.workflow.protos;

@SuppressWarnings({"all"})
public final class EventmeshWorkflowGrpc {

    private EventmeshWorkflowGrpc() {
    }
    public static void registerAllExtensions(
                                             com.google.protobuf.ExtensionRegistryLite registry) {
    }

    public static void registerAllExtensions(
                                             com.google.protobuf.ExtensionRegistry registry) {
        registerAllExtensions(
                (com.google.protobuf.ExtensionRegistryLite) registry);
    }
    static final com.google.protobuf.Descriptors.Descriptor internal_static_eventmesh_workflow_api_protocol_ExecuteRequest_descriptor;
    static final com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internal_static_eventmesh_workflow_api_protocol_ExecuteRequest_fieldAccessorTable;
    static final com.google.protobuf.Descriptors.Descriptor internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_descriptor;
    static final com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_fieldAccessorTable;

    public static com.google.protobuf.Descriptors.FileDescriptor getDescriptor() {
        return descriptor;
    }
    private static com.google.protobuf.Descriptors.FileDescriptor descriptor;
    static {
        String[] descriptorData = {
                "\n\016workflow.proto\022\037eventmesh.workflow.api" +
                        ".protocol\"Z\n\016ExecuteRequest\022\n\n\002id\030\001 \001(\t\022" +
                        "\023\n\013instance_id\030\002 \001(\t\022\030\n\020task_instance_id" +
                        "\030\003 \001(\t\022\r\n\005input\030\004 \001(\t\"&\n\017ExecuteResponse" +
                        "\022\023\n\013instance_id\030\001 \001(\t2z\n\010Workflow\022n\n\007Exe" +
                        "cute\022/.eventmesh.workflow.api.protocol.E" +
                        "xecuteRequest\0320.eventmesh.workflow.api.p" +
                        "rotocol.ExecuteResponse\"\000B\226\001\n4org.apache" +
                        ".eventmesh.common.protocol.workflow.prot" +
                        "osB\025EventmeshWorkflowGrpcP\001ZEgithub.com/" +
                        "apache/eventmesh/eventmesh-wor" +
                        "kflow-go/api/protob\006proto3"
        };
        descriptor = com.google.protobuf.Descriptors.FileDescriptor
                .internalBuildGeneratedFileFrom(descriptorData,
                        new com.google.protobuf.Descriptors.FileDescriptor[]{
                        });
        internal_static_eventmesh_workflow_api_protocol_ExecuteRequest_descriptor =
                getDescriptor().getMessageTypes().get(0);
        internal_static_eventmesh_workflow_api_protocol_ExecuteRequest_fieldAccessorTable =
                new com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
                        internal_static_eventmesh_workflow_api_protocol_ExecuteRequest_descriptor,
                        new String[]{"Id", "InstanceId", "TaskInstanceId", "Input",});
        internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_descriptor =
                getDescriptor().getMessageTypes().get(1);
        internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_fieldAccessorTable =
                new com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
                        internal_static_eventmesh_workflow_api_protocol_ExecuteResponse_descriptor,
                        new String[]{"InstanceId",});
    }

    // @@protoc_insertion_point(outer_class_scope)
}
