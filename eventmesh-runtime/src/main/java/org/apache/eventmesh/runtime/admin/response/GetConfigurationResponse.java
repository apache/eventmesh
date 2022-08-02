/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.runtime.admin.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GetConfigurationResponse {
    public String eventMeshEnv;
    public String eventMeshIDC;
    public String eventMeshCluster;
    public String eventMeshServerIp;
    public boolean eventMeshServerSecurityEnable;
    public boolean eventMeshServerRegistryEnable;
    public String eventMeshName;
    public String sysID;
    public String eventMeshWebhookOrigin;
    public String namesrvAddr;

    public int eventMeshTcpServerPort;
    public boolean eventMeshTcpServerEnabled;

    public int eventMeshHttpServerPort;
    public boolean eventMeshHttpServerUseTls;

    public int eventMeshGrpcServerPort;
    public boolean eventMeshGrpcServerUseTls;

    //    public String eventMeshConnectorPluginType;
    //    public String eventMeshSecurityPluginType;
    //    public String eventMeshRegistryPluginType;
    //    public String eventMeshRegistryPluginUsername = "";
    //    public String eventMeshRegistryPluginPassword = "";
    //    public Integer eventMeshRegisterIntervalInMills;
    //    public Integer eventMeshFetchRegistryAddrInterval;
    //    public List<String> eventMeshMetricsPluginType;
    //    public String eventMeshTracePluginType;
    //    public boolean eventMeshServerTraceEnable;

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
            @JsonProperty("eventMeshTcpServerEnabled") boolean eventMeshTcpServerEnabled,

            // HTTP Configuration
            @JsonProperty("eventMeshHttpServerPort") int eventMeshHttpServerPort,
            @JsonProperty("eventMeshHttpServerUseTls") boolean eventMeshHttpServerUseTls,

            // gRPC Configuration
            @JsonProperty("eventMeshGrpcServerPort") int eventMeshGrpcServerPort,
            @JsonProperty("eventMeshGrpcServerUseTls") boolean eventMeshGrpcServerUseTls
    ) {
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
        this.eventMeshTcpServerEnabled = eventMeshTcpServerEnabled;

        this.eventMeshHttpServerPort = eventMeshHttpServerPort;
        this.eventMeshHttpServerUseTls = eventMeshHttpServerUseTls;

        this.eventMeshGrpcServerPort = eventMeshGrpcServerPort;
        this.eventMeshGrpcServerUseTls = eventMeshGrpcServerUseTls;
    }
}
