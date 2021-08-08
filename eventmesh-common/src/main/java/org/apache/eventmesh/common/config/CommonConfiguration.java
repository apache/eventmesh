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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshRuntimeException;
import org.apache.eventmesh.common.utils.IPUtil;
import org.assertj.core.util.Lists;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public enum CommonConfiguration {
    ;
    private static YamlConfigurationReader yamlConfigurationReader;

    public static String eventMeshEnv      = "P";
    public static String eventMeshIDC      = "FT";
    public static String eventMeshCluster  = "LS";
    public static String eventMeshName     = "";
    public static String sysID             = "5477";
    public static String eventMeshServerIp = IPUtil.getLocalAddress();

    public static String eventMeshConnectorPluginType = "rocketmq";

    public static boolean eventMeshServerSecurityEnable = false;
    public static String  eventMeshSecurityPluginType   = "security";

    public static List<String> eventMeshProtocolServerPluginTypes = Lists.newArrayList("http", "tcp");

    public static boolean eventMeshServerRegistryEnable = false;
    public static String  eventMeshRegistryPluginType   = "namesrv";

    public static int eventMeshPrometheusPort = 19090;

    static {
        try {
            yamlConfigurationReader = new YamlConfigurationReader(Constants.EVENTMESH_COMMON_PROPERTY);
        } catch (IOException e) {
            throw new EventMeshRuntimeException(String.format("config file: %s is not exist", Constants.EVENTMESH_COMMON_PROPERTY), e);
        }
        refreshConfig();
    }

    private static void refreshConfig() {
        eventMeshEnv = yamlConfigurationReader.getString(ConfKeys.KEYS_EVENTMESH_ENV, eventMeshEnv);
        eventMeshIDC = yamlConfigurationReader.getString(ConfKeys.KEYS_EVENTMESH_IDC, eventMeshIDC);
        eventMeshCluster = yamlConfigurationReader.getString(ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER, eventMeshCluster);
        eventMeshName = yamlConfigurationReader.getString(ConfKeys.KEYS_EVENTMESH_SERVER_NAME, eventMeshName);
        sysID = yamlConfigurationReader.getString(ConfKeys.KEYS_EVENTMESH_SYSID, sysID);
        eventMeshConnectorPluginType = yamlConfigurationReader.getString(ConfKeys.KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE, eventMeshConnectorPluginType);
        eventMeshServerIp = yamlConfigurationReader.getString(ConfKeys.KEYS_EVENTMESH_SERVER_HOST_IP, eventMeshServerIp);
        eventMeshServerSecurityEnable = yamlConfigurationReader.getBool(ConfKeys.KEYS_EVENTMESH_SECURITY_ENABLED, eventMeshServerSecurityEnable);
        eventMeshSecurityPluginType = yamlConfigurationReader.getString(ConfKeys.KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE, eventMeshSecurityPluginType);
        eventMeshProtocolServerPluginTypes = yamlConfigurationReader.getList(ConfKeys.KEYS_EVENTMESH_PROTOCOL_SERVER_PLUGIN_TYPE, eventMeshProtocolServerPluginTypes);
        eventMeshPrometheusPort = yamlConfigurationReader.getInt(ConfKeys.KEY_EVENTMESH_METRICS_PROMETHEUS_PORT, eventMeshPrometheusPort);
        eventMeshRegistryPluginType = yamlConfigurationReader.getString(ConfKeys.KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE, eventMeshRegistryPluginType);
        eventMeshServerRegistryEnable = yamlConfigurationReader.getBool(ConfKeys.KEYS_EVENTMESH_REGISTRY_ENABLED, eventMeshServerRegistryEnable);
    }

    static class ConfKeys {
        public static String KEYS_EVENTMESH_ENV = "eventMesh.server.env";

        public static String KEYS_EVENTMESH_IDC = "eventMesh.server.idc";

        public static String KEYS_EVENTMESH_SYSID = "eventMesh.sysid";

        public static String KEYS_EVENTMESH_SERVER_CLUSTER = "eventMesh.server.cluster";

        public static String KEYS_EVENTMESH_SERVER_NAME = "eventMesh.server.name";

        public static String KEYS_EVENTMESH_SERVER_HOST_IP = "eventMesh.server.hostIp";

        public static String KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE = "eventMesh.connector.plugin.type";

        public static String KEYS_EVENTMESH_SECURITY_ENABLED = "eventMesh.server.security.enabled";

        public static String KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE = "eventMesh.security.plugin.type";

        public static String KEYS_EVENTMESH_PROTOCOL_SERVER_PLUGIN_TYPE = "eventMesh.protocolServer.plugin.type";

        public static String KEY_EVENTMESH_METRICS_PROMETHEUS_PORT = "eventMesh.metrics.prometheus.port";

        public static String KEYS_EVENTMESH_REGISTRY_ENABLED = "eventMesh.server.registry.enabled";

        public static String KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE = "eventMesh.registry.plugin.type";
    }
}