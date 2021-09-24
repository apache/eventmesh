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

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.IPUtil;

public class CommonConfiguration {
    public String eventMeshEnv = "P";
    public String eventMeshIDC = "FT";
    public String eventMeshCluster = "LS";
    public String eventMeshName = "";
    public String sysID = "5477";
    public String eventMeshConnectorPluginType = "rocketmq";
    public String eventMeshSecurityPluginType = "security";
    public int eventMeshPrometheusPort = 19090;
    public String eventMeshRegistryPluginType = "namesrv";
    public String eventMeshTraceExporterType = "log";
    public int eventMeshTraceMaxExportSize = 512;
    public int eventMeshTraceMaxQueueSize = 2048;
    public int eventMeshTraceExporterTimeout = 30;
    public int eventMeshTraceExportInterval = 5;
    public String eventMeshExporterZipkinIp = "localhost";
    public int eventMeshExporterZipkinPort = 9411;

    public String namesrvAddr = "";
    public Integer eventMeshRegisterIntervalInMills = 10 * 1000;
    public Integer eventMeshFetchRegistryAddrInterval = 10 * 1000;
    public String eventMeshServerIp = null;
    public boolean eventMeshServerSecurityEnable = false;
    public boolean eventMeshServerRegistryEnable = false;
    protected ConfigurationWrapper configurationWrapper;

    public CommonConfiguration(ConfigurationWrapper configurationWrapper) {
        this.configurationWrapper = configurationWrapper;
    }

    public void init() {

        if (configurationWrapper != null) {
            String eventMeshEnvStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_ENV);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshEnvStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ENV));
            eventMeshEnv = StringUtils.deleteWhitespace(eventMeshEnvStr);

            String sysIdStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SYSID);
            Preconditions.checkState(StringUtils.isNotEmpty(sysIdStr) && StringUtils.isNumeric(sysIdStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SYSID));
            sysID = StringUtils.deleteWhitespace(sysIdStr);

            String eventMeshClusterStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshClusterStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER));
            eventMeshCluster = StringUtils.deleteWhitespace(eventMeshClusterStr);

            String eventMeshNameStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_NAME);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshNameStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_NAME));
            eventMeshName = StringUtils.deleteWhitespace(eventMeshNameStr);

            String eventMeshIDCStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_IDC);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshIDCStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_IDC));
            eventMeshIDC = StringUtils.deleteWhitespace(eventMeshIDCStr);

            String eventMeshPrometheusPortStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_METRICS_PROMETHEUS_PORT);
            if (StringUtils.isNotEmpty(eventMeshPrometheusPortStr)) {
                eventMeshPrometheusPort = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshPrometheusPortStr));
            }

            eventMeshServerIp = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_HOST_IP);
            if (StringUtils.isBlank(eventMeshServerIp)) {
                eventMeshServerIp = IPUtil.getLocalAddress();
            }

            eventMeshConnectorPluginType = configurationWrapper.getProp(ConfKeys.KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshConnectorPluginType), String.format("%s error", ConfKeys.KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE));

            String eventMeshServerAclEnableStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SECURITY_ENABLED);
            if (StringUtils.isNotBlank(eventMeshServerAclEnableStr)) {
                eventMeshServerSecurityEnable = Boolean.valueOf(StringUtils.deleteWhitespace(eventMeshServerAclEnableStr));
            }

            eventMeshSecurityPluginType = configurationWrapper.getProp(ConfKeys.KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshSecurityPluginType), String.format("%s error", ConfKeys.KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE));

            String eventMeshServerRegistryEnableStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REGISTRY_ENABLED);
            if (StringUtils.isNotBlank(eventMeshServerRegistryEnableStr)) {
                eventMeshServerRegistryEnable = Boolean.valueOf(StringUtils.deleteWhitespace(eventMeshServerRegistryEnableStr));
            }

            eventMeshRegistryPluginType = configurationWrapper.getProp(ConfKeys.KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshRegistryPluginType), String.format("%s error", ConfKeys.KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE));

            String eventMeshTraceExporterTypeStr = configurationWrapper.getProp(ConfKeys.KEYS_ENENTMESH_TRACE_EXPORTER_TYPE);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshTraceExporterTypeStr), String.format("%s error", ConfKeys.KEYS_ENENTMESH_TRACE_EXPORTER_TYPE));
            eventMeshTraceExporterType = StringUtils.deleteWhitespace(eventMeshTraceExporterTypeStr);

            String eventMeshTraceMaxExportSizeStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_TRACE_MAX_EXPORT_SIZE);
            if (StringUtils.isNotEmpty(eventMeshTraceMaxExportSizeStr)) {
                eventMeshTraceMaxExportSize = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTraceMaxExportSizeStr));
            }

            String eventMeshTraceMaxQueueSizeStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_TRACE_MAX_QUEUE_SIZE);
            if (StringUtils.isNotEmpty(eventMeshTraceMaxQueueSizeStr)) {
                eventMeshTraceMaxQueueSize = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTraceMaxQueueSizeStr));
            }

            String eventMeshTraceExportTimeoutStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_TRACE_EXPORTER_TIMEOUT);
            if (StringUtils.isNotEmpty(eventMeshTraceExportTimeoutStr)) {
                eventMeshTraceExporterTimeout = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTraceExportTimeoutStr));
            }

            String eventMeshTraceExportIntervalStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_TRACE_EXPORT_INTERVAL);
            if (StringUtils.isNotEmpty(eventMeshTraceExportIntervalStr)) {
                eventMeshTraceExportInterval = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTraceExportIntervalStr));
            }

            String eventMeshExporterZipkinIpStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_EXPORTER_ZIPKIN_IP);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshExporterZipkinIpStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_EXPORTER_ZIPKIN_IP));
            eventMeshExporterZipkinIp = StringUtils.deleteWhitespace(eventMeshExporterZipkinIpStr);

            String eventMeshExporterZipkinPortStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_EXPORTER_ZIPKIN_PORT);
            if (StringUtils.isNotEmpty(eventMeshExporterZipkinPortStr)) {
                eventMeshExporterZipkinPort = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshExporterZipkinPortStr));
            }
        }
    }

    static class ConfKeys {
        public static String KEYS_EVENTMESH_ENV = "eventMesh.server.env";

        public static String KEYS_EVENTMESH_IDC = "eventMesh.server.idc";

        public static String KEYS_EVENTMESH_SYSID = "eventMesh.sysid";

        public static String KEYS_EVENTMESH_SERVER_CLUSTER = "eventMesh.server.cluster";

        public static String KEYS_EVENTMESH_SERVER_NAME = "eventMesh.server.name";

        public static String KEYS_EVENTMESH_SERVER_HOST_IP = "eventMesh.server.hostIp";

        public static String KEYS_EVENTMESH_SERVER_REGISTER_INTERVAL = "eventMesh.server.registry.registerIntervalInMills";

        public static String KEYS_EVENTMESH_SERVER_FETCH_REGISTRY_ADDR_INTERVAL = "eventMesh.server.registry.fetchRegistryAddrIntervalInMills";

        public static String KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE = "eventMesh.connector.plugin.type";

        public static String KEYS_EVENTMESH_SECURITY_ENABLED = "eventMesh.server.security.enabled";

        public static String KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE = "eventMesh.security.plugin.type";

        public static String KEY_EVENTMESH_METRICS_PROMETHEUS_PORT = "eventMesh.metrics.prometheus.port";

        public static String KEYS_EVENTMESH_REGISTRY_ENABLED = "eventMesh.server.registry.enabled";

        public static String KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE = "eventMesh.registry.plugin.type";

        public static String KEYS_ENENTMESH_TRACE_EXPORTER_TYPE = "eventmesh.trace.exporter.type";

        public static String KEYS_EVENTMESH_TRACE_MAX_EXPORT_SIZE = "eventmesh.trace.max.export.size";

        public static String KEYS_EVENTMESH_TRACE_MAX_QUEUE_SIZE = "eventmesh.trace.max.queue.size";

        public static String KEYS_EVENTMESH_TRACE_EXPORTER_TIMEOUT = "eventmesh.trace.exporter.timeout";

        public static String KEYS_EVENTMESH_TRACE_EXPORT_INTERVAL = "5";

        public static String KEYS_EVENTMESH_EXPORTER_ZIPKIN_IP = "eventmesh.exporter.zipkin.ip";

        public static String KEYS_EVENTMESH_EXPORTER_ZIPKIN_PORT = "eventmesh.exporter.zipkin.port";
    }
}