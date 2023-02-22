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

package org.apache.eventmesh.common.config;

import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

import org.assertj.core.util.Strings;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config(prefix = "eventMesh")
public class CommonConfiguration {

    @ConfigFiled(field = "sysid", beNumber = true, notEmpty = true)
    private String sysID = "5477";

    @ConfigFiled(field = "server.env", notEmpty = true)
    private String eventMeshEnv = "P";

    @ConfigFiled(field = "server.idc", notEmpty = true)
    private String eventMeshIDC = "FT";

    @ConfigFiled(field = "server.name", notEmpty = true)
    private String eventMeshName = "";

    @ConfigFiled(field = "server.cluster", notEmpty = true)
    private String eventMeshCluster = "LS";

    @ConfigFiled(field = "server.hostIp", reload = true)
    private String eventMeshServerIp = null;

    @ConfigFiled(field = "registry.plugin.server-addr", notEmpty = true)
    private String namesrvAddr = "";


    @ConfigFiled(field = "trace.plugin", notEmpty = true)
    private String eventMeshTracePluginType;

    @ConfigFiled(field = "metrics.plugin", notEmpty = true)
    private List<String> eventMeshMetricsPluginType;

    @ConfigFiled(field = "registry.plugin.type", notEmpty = true)
    private String eventMeshRegistryPluginType = "namesrv";

    @ConfigFiled(field = "security.plugin.type", notEmpty = true)
    private String eventMeshSecurityPluginType = "security";

    @ConfigFiled(field = "connector.plugin.type", notEmpty = true)
    private String eventMeshConnectorPluginType = "rocketmq";


    @ConfigFiled(field = "registry.plugin.username")
    private String eventMeshRegistryPluginUsername = "";

    @ConfigFiled(field = "registry.plugin.password")
    private String eventMeshRegistryPluginPassword = "";

    @ConfigFiled(field = "server.registry.registerIntervalInMills")
    private Integer eventMeshRegisterIntervalInMills = 10 * 1000;

    @ConfigFiled(field = "server.registry.fetchRegistryAddrIntervalInMills")
    private Integer eventMeshFetchRegistryAddrInterval = 10 * 1000;


    @ConfigFiled(field = "server.trace.enabled")
    private boolean eventMeshServerTraceEnable = false;

    @ConfigFiled(field = "server.security.enabled")
    private boolean eventMeshServerSecurityEnable = false;

    @ConfigFiled(field = "server.registry.enabled")
    private boolean eventMeshServerRegistryEnable = false;


    @ConfigFiled(field = "server.provide.protocols", reload = true)
    private List<String> eventMeshProvideServerProtocols;


    @ConfigFiled(reload = true)
    private String eventMeshWebhookOrigin;

    @ConfigFiled(reload = true)
    private String meshGroup;

    public void reload() {
        this.eventMeshWebhookOrigin = "eventmesh." + eventMeshIDC;

        if (Strings.isNullOrEmpty(this.eventMeshServerIp)) {
            this.eventMeshServerIp = IPUtils.getLocalAddress();
        }

        if (CollectionUtils.isEmpty(eventMeshProvideServerProtocols)) {
            this.eventMeshProvideServerProtocols = Collections.singletonList(ConfigurationContextUtil.HTTP);
        }

        meshGroup = String.join("-", this.eventMeshEnv, this.eventMeshCluster, this.sysID);
    }
}
