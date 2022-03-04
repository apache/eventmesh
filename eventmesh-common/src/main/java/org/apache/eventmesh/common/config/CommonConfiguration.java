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

import org.apache.eventmesh.common.utils.IPUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;

public class CommonConfiguration {
    public String eventMeshEnv                 = "P";
    public String eventMeshIDC                 = "FT";
    public String eventMeshCluster             = "LS";
    public String eventMeshName                = "";
    public String sysID                        = "5477";
    public String eventMeshConnectorPluginType = "rocketmq";
    public String eventMeshSecurityPluginType  = "security";
    public String eventMeshRegistryPluginType  = "namesrv";
    public String eventMeshTracePluginType  = "trace";

    public List<String> eventMeshMetricsPluginType;

    public    String               namesrvAddr                        = "";
    public    Integer              eventMeshRegisterIntervalInMills   = 10 * 1000;
    public    Integer              eventMeshFetchRegistryAddrInterval = 10 * 1000;
    public    String               eventMeshServerIp                  = null;
    public    boolean              eventMeshServerSecurityEnable      = false;
    public    boolean              eventMeshServerRegistryEnable      = false;
    public    boolean              eventMeshServerTraceEnable      = false;
    protected ConfigurationWrapper configurationWrapper;

    public String eventMeshWebhookOrigin = "eventmesh." + eventMeshIDC;

    public CommonConfiguration(ConfigurationWrapper configurationWrapper) {
        this.configurationWrapper = configurationWrapper;
    }

    public void init() {

        if (configurationWrapper != null) {
            String eventMeshEnvStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_ENV);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshEnvStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_ENV));
            eventMeshEnv = StringUtils.deleteWhitespace(eventMeshEnvStr);

            String sysIdStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SYSID);
            Preconditions.checkState(StringUtils.isNotEmpty(sysIdStr) && StringUtils.isNumeric(sysIdStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_SYSID));
            sysID = StringUtils.deleteWhitespace(sysIdStr);

            String eventMeshClusterStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshClusterStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER));
            eventMeshCluster = StringUtils.deleteWhitespace(eventMeshClusterStr);

            String eventMeshNameStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_NAME);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshNameStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_NAME));
            eventMeshName = StringUtils.deleteWhitespace(eventMeshNameStr);

            String eventMeshIdcStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_IDC);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshIdcStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_IDC));
            eventMeshIDC = StringUtils.deleteWhitespace(eventMeshIdcStr);

            eventMeshServerIp =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_HOST_IP);
            if (StringUtils.isBlank(eventMeshServerIp)) {
                eventMeshServerIp = IPUtils.getLocalAddress();
            }

            eventMeshConnectorPluginType =
                    configurationWrapper.getProp(ConfKeys.KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshConnectorPluginType),
                    String.format("%s error", ConfKeys.KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE));

            String eventMeshServerAclEnableStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SECURITY_ENABLED);
            if (StringUtils.isNotBlank(eventMeshServerAclEnableStr)) {
                eventMeshServerSecurityEnable =
                        Boolean.parseBoolean(StringUtils.deleteWhitespace(eventMeshServerAclEnableStr));
            }

            eventMeshSecurityPluginType =
                    configurationWrapper.getProp(ConfKeys.KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshSecurityPluginType),
                    String.format("%s error", ConfKeys.KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE));

            String eventMeshServerRegistryEnableStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REGISTRY_ENABLED);
            if (StringUtils.isNotBlank(eventMeshServerRegistryEnableStr)) {
                eventMeshServerRegistryEnable =
                    Boolean.parseBoolean(StringUtils.deleteWhitespace(eventMeshServerRegistryEnableStr));
            }

            String eventMeshServerTraceEnableStr =
                configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_TRACE_ENABLED);
            if (StringUtils.isNotBlank(eventMeshServerTraceEnableStr)) {
                eventMeshServerTraceEnable =
                    Boolean.parseBoolean(StringUtils.deleteWhitespace(eventMeshServerTraceEnableStr));
            }

            eventMeshRegistryPluginType =
                configurationWrapper.getProp(ConfKeys.KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshRegistryPluginType),
                String.format("%s error", ConfKeys.KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE));

            String metricsPluginType = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_METRICS_PLUGIN_TYPE);
            if (StringUtils.isNotEmpty(metricsPluginType)) {
                eventMeshMetricsPluginType = Arrays.stream(metricsPluginType.split(","))
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.toList());
            }

            String eventMeshTracePluginTypeStr =
                configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_TRACE_PLUGIN_TYPE);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshTracePluginTypeStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_TRACE_PLUGIN_TYPE));
            eventMeshTracePluginType = StringUtils.deleteWhitespace(eventMeshTracePluginTypeStr);
        }
    }

    static class ConfKeys {
        public static String KEYS_EVENTMESH_ENV = "eventMesh.server.env";

        public static String KEYS_EVENTMESH_IDC = "eventMesh.server.idc";

        public static String KEYS_EVENTMESH_SYSID = "eventMesh.sysid";

        public static String KEYS_EVENTMESH_SERVER_CLUSTER = "eventMesh.server.cluster";

        public static String KEYS_EVENTMESH_SERVER_NAME = "eventMesh.server.name";

        public static String KEYS_EVENTMESH_SERVER_HOST_IP = "eventMesh.server.hostIp";

        public static String KEYS_EVENTMESH_SERVER_REGISTER_INTERVAL =
                "eventMesh.server.registry.registerIntervalInMills";

        public static String KEYS_EVENTMESH_SERVER_FETCH_REGISTRY_ADDR_INTERVAL =
                "eventMesh.server.registry.fetchRegistryAddrIntervalInMills";

        public static String KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE = "eventMesh.connector.plugin.type";

        public static String KEYS_EVENTMESH_SECURITY_ENABLED = "eventMesh.server.security.enabled";

        public static String KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE = "eventMesh.security.plugin.type";

        public static String KEYS_EVENTMESH_REGISTRY_ENABLED = "eventMesh.server.registry.enabled";

        public static String KEYS_EVENTMESH_TRACE_ENABLED = "eventMesh.server.trace.enabled";

        public static String KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE = "eventMesh.registry.plugin.type";

        public static String KEYS_EVENTMESH_METRICS_PLUGIN_TYPE = "eventmesh.metrics.plugin";

        public static String KEYS_EVENTMESH_TRACE_PLUGIN_TYPE = "eventmesh.trace.plugin";
    }
}