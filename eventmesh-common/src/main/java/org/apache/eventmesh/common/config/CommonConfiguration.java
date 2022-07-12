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
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;

public class CommonConfiguration {
    public String eventMeshEnv = "P";
    public String eventMeshIDC = "FT";
    public String eventMeshCluster = "LS";
    public String eventMeshName = "";
    public String sysID = "5477";
    public String eventMeshConnectorPluginType = "rocketmq";
    public String eventMeshSecurityPluginType = "security";
    public String eventMeshRegistryPluginType = "namesrv";
    public List<String> eventMeshMetricsPluginType;
    public String eventMeshTracePluginType;
    public String namesrvAddr = "";
    public String eventMeshRegistryPluginUsername = "";
    public String eventMeshRegistryPluginPassword = "";
    public Integer eventMeshRegisterIntervalInMills = 10 * 1000;
    public Integer eventMeshFetchRegistryAddrInterval = 10 * 1000;
    public String eventMeshServerIp = null;
    public boolean eventMeshServerSecurityEnable = false;
    public boolean eventMeshServerRegistryEnable = false;
    public boolean eventMeshServerTraceEnable = false;
    protected ConfigurationWrapper configurationWrapper;
    public String eventMeshWebhookOrigin = "eventmesh." + eventMeshIDC;

    public CommonConfiguration(ConfigurationWrapper configurationWrapper) {
        this.configurationWrapper = configurationWrapper;
    }

    public void init() {

        if (configurationWrapper != null) {
            eventMeshEnv = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_ENV);

            sysID = checkNumeric(ConfKeys.KEYS_EVENTMESH_SYSID);

            eventMeshCluster = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER);

            eventMeshName = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_SERVER_NAME);

            eventMeshIDC = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_IDC);

            eventMeshServerIp = get(ConfKeys.KEYS_EVENTMESH_SERVER_HOST_IP, IPUtils::getLocalAddress);

            eventMeshConnectorPluginType = checkNotEmpty(ConfKeys.KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE);

            eventMeshServerSecurityEnable = Boolean.parseBoolean(get(ConfKeys.KEYS_EVENTMESH_SECURITY_ENABLED, () -> "false"));

            eventMeshSecurityPluginType = checkNotEmpty(ConfKeys.KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE);

            eventMeshServerRegistryEnable = Boolean.parseBoolean(get(ConfKeys.KEYS_EVENTMESH_REGISTRY_ENABLED, () -> "false"));

            eventMeshRegistryPluginType = checkNotEmpty(ConfKeys.KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE);

            namesrvAddr = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_REGISTRY_PULGIN_SERVER_ADDR);

            eventMeshRegistryPluginUsername =
                Optional.ofNullable(configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REGISTRY_PULGIN_USERNAME)).orElse("");

            eventMeshRegistryPluginPassword =
                Optional.ofNullable(configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REGISTRY_PULGIN_PASSWORD)).orElse("");

            String metricsPluginType = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_METRICS_PLUGIN_TYPE);
            if (StringUtils.isNotEmpty(metricsPluginType)) {
                eventMeshMetricsPluginType = Arrays.stream(metricsPluginType.split(","))
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.toList());
            }

            eventMeshServerTraceEnable = Boolean.parseBoolean(get(ConfKeys.KEYS_EVENTMESH_TRACE_ENABLED, () -> "false"));
            eventMeshTracePluginType = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_TRACE_PLUGIN_TYPE);
        }
    }

    private String checkNotEmpty(String key) {
        String value = configurationWrapper.getProp(key);
        if (value != null) {
            value = StringUtils.deleteWhitespace(value);
        }
        Preconditions.checkState(StringUtils.isNotEmpty(value), key + " is invalidated");
        return value;
    }

    private String checkNumeric(String key) {
        String value = configurationWrapper.getProp(key);
        if (value != null) {
            value = StringUtils.deleteWhitespace(value);
        }
        Preconditions.checkState(StringUtils.isNotEmpty(value) && StringUtils.isNumeric(value), key + " is invalidated");
        return value;
    }

    private String get(String key, Supplier<String> defaultValueSupplier) {
        String value = configurationWrapper.getProp(key);
        if (value != null) {
            value = StringUtils.deleteWhitespace(value);
        }
        return StringUtils.isEmpty(value) ? defaultValueSupplier.get() : value;
    }

    static class ConfKeys {
        public static final String KEYS_EVENTMESH_ENV = "eventMesh.server.env";

        public static final String KEYS_EVENTMESH_IDC = "eventMesh.server.idc";

        public static final String KEYS_EVENTMESH_SYSID = "eventMesh.sysid";

        public static final String KEYS_EVENTMESH_SERVER_CLUSTER = "eventMesh.server.cluster";

        public static final String KEYS_EVENTMESH_SERVER_NAME = "eventMesh.server.name";

        public static final String KEYS_EVENTMESH_SERVER_HOST_IP = "eventMesh.server.hostIp";

        public static final String KEYS_EVENTMESH_SERVER_REGISTER_INTERVAL =
            "eventMesh.server.registry.registerIntervalInMills";

        public static final String KEYS_EVENTMESH_SERVER_FETCH_REGISTRY_ADDR_INTERVAL =
            "eventMesh.server.registry.fetchRegistryAddrIntervalInMills";

        public static final String KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE = "eventMesh.connector.plugin.type";

        public static final String KEYS_EVENTMESH_SECURITY_ENABLED = "eventMesh.server.security.enabled";

        public static final String KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE = "eventMesh.security.plugin.type";

        public static final String KEYS_EVENTMESH_REGISTRY_ENABLED = "eventMesh.registry.plugin.enabled";

        public static final String KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE = "eventMesh.registry.plugin.type";

        public static final String KEYS_EVENTMESH_REGISTRY_PULGIN_SERVER_ADDR = "eventMesh.registry.plugin.server-addr";

        public static final String KEYS_EVENTMESH_REGISTRY_PULGIN_USERNAME = "eventMesh.registry.plugin.username";

        public static final String KEYS_EVENTMESH_REGISTRY_PULGIN_PASSWORD = "eventMesh.registry.plugin.password";

        public static final String KEYS_EVENTMESH_METRICS_PLUGIN_TYPE = "eventMesh.metrics.plugin";

        public static final String KEYS_EVENTMESH_TRACE_ENABLED = "eventMesh.server.trace.enabled";

        public static final String KEYS_EVENTMESH_TRACE_PLUGIN_TYPE = "eventMesh.trace.plugin";
    }
}