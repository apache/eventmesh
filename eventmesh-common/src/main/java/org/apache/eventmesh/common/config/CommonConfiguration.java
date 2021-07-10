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

    public String namesrvAddr = "";
    public String clientUserName = "username";
    public String clientPass = "user@123";
    public Integer consumeThreadMin = 2;
    public Integer consumeThreadMax = 2;
    public Integer consumeQueueSize = 10000;
    public Integer pullBatchSize = 32;
    public Integer ackWindow = 1000;
    public Integer pubWindow = 100;
    public long consumeTimeout = 0L;
    public Integer pollNameServerInteval = 10 * 1000;
    public Integer heartbeatBrokerInterval = 30 * 1000;
    public Integer rebalanceInterval = 20 * 1000;
    public Integer eventMeshRegisterIntervalInMills = 10 * 1000;
    public Integer eventMeshFetchRegistryAddrInterval = 10 * 1000;
    public String eventMeshServerIp = null;
    protected ConfigurationWraper configurationWraper;

    public CommonConfiguration(ConfigurationWraper configurationWraper) {
        this.configurationWraper = configurationWraper;
    }

    public void init() {

        if (configurationWraper != null) {
            String eventMeshEnvStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ENV);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshEnvStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ENV));
            eventMeshEnv = StringUtils.deleteWhitespace(eventMeshEnvStr);

            String sysIdStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SYSID);
            Preconditions.checkState(StringUtils.isNotEmpty(sysIdStr) && StringUtils.isNumeric(sysIdStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SYSID));
            sysID = StringUtils.deleteWhitespace(sysIdStr);

            String eventMeshClusterStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshClusterStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER));
            eventMeshCluster = StringUtils.deleteWhitespace(eventMeshClusterStr);

            String eventMeshNameStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_NAME);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshNameStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_NAME));
            eventMeshName = StringUtils.deleteWhitespace(eventMeshNameStr);

            String eventMeshIDCStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_IDC);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshIDCStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_IDC));
            eventMeshIDC = StringUtils.deleteWhitespace(eventMeshIDCStr);

            eventMeshServerIp = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_HOST_IP);
            if (StringUtils.isBlank(eventMeshServerIp)) {
                eventMeshServerIp = IPUtil.getLocalAddress();
            }

            eventMeshConnectorPluginType = configurationWraper.getProp(ConfKeys.KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshConnectorPluginType), String.format("%s error", ConfKeys.KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE));
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
    }
}