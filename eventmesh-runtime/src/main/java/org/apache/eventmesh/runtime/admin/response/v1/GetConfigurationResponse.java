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

package org.apache.eventmesh.runtime.admin.response.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetConfigurationResponse {

    private String eventMeshEnv;
    private String eventMeshIDC;
    private String eventMeshCluster;
    private String eventMeshServerIp;
    private boolean eventMeshServerSecurityEnable;
    private boolean eventMeshServerRegistryEnable;
    private String eventMeshName;
    private String sysID;
    private String eventMeshWebhookOrigin;
    private String namesrvAddr;

    private int eventMeshTcpServerPort;

    private int eventMeshHttpServerPort;
    private boolean eventMeshHttpServerUseTls;

    private int eventMeshGrpcServerPort;
    private boolean eventMeshGrpcServerUseTls;

    @JsonCreator
    public GetConfigurationResponse(
        // Common Configuration
        @JsonProperty("sysID") String sysID,
        @JsonProperty("namesrvAddr") String namesrvAddr,
        @JsonProperty("eventMeshEnv") String eventMeshEnv,
        @JsonProperty("eventMeshIDC") String eventMeshIDC,
        @JsonProperty("eventMeshCluster") String eventMeshCluster,
        @JsonProperty("eventMeshServerIp") String eventMeshServerIp,
        @JsonProperty("eventMeshName") String eventMeshName,
        @JsonProperty("eventMeshWebhookOrigin") String eventMeshWebhookOrigin,
        @JsonProperty("eventMeshServerSecurityEnable") boolean eventMeshServerSecurityEnable,
        @JsonProperty("eventMeshServerRegistryEnable") boolean eventMeshServerRegistryEnable,

        // TCP Configuration
        @JsonProperty("eventMeshTcpServerPort") int eventMeshTcpServerPort,

        // HTTP Configuration
        @JsonProperty("eventMeshHttpServerPort") int eventMeshHttpServerPort,
        @JsonProperty("eventMeshHttpServerUseTls") boolean eventMeshHttpServerUseTls,

        // gRPC Configuration
        @JsonProperty("eventMeshGrpcServerPort") int eventMeshGrpcServerPort,
        @JsonProperty("eventMeshGrpcServerUseTls") boolean eventMeshGrpcServerUseTls) {

        super();
        this.sysID = sysID;
        this.namesrvAddr = namesrvAddr;
        this.eventMeshEnv = eventMeshEnv;
        this.eventMeshIDC = eventMeshIDC;
        this.eventMeshCluster = eventMeshCluster;
        this.eventMeshServerIp = eventMeshServerIp;
        this.eventMeshName = eventMeshName;
        this.eventMeshWebhookOrigin = eventMeshWebhookOrigin;
        this.eventMeshServerSecurityEnable = eventMeshServerSecurityEnable;
        this.eventMeshServerRegistryEnable = eventMeshServerRegistryEnable;

        this.eventMeshTcpServerPort = eventMeshTcpServerPort;

        this.eventMeshHttpServerPort = eventMeshHttpServerPort;
        this.eventMeshHttpServerUseTls = eventMeshHttpServerUseTls;

        this.eventMeshGrpcServerPort = eventMeshGrpcServerPort;
        this.eventMeshGrpcServerUseTls = eventMeshGrpcServerUseTls;
    }
}
