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

import static org.apache.eventmesh.common.Constants.HTTP;

import org.apache.eventmesh.common.Constants;
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

    @ConfigField(field = "sysid", beNumber = true, notEmpty = true)
    private String sysID = "5477";

    @ConfigField(field = "server.env", notEmpty = true)
    private String eventMeshEnv = "P";

    @ConfigField(field = "server.idc", notEmpty = true)
    private String eventMeshIDC = "FT";

    @ConfigField(field = "server.name", notEmpty = true)
    private String eventMeshName = "";

    @ConfigField(field = "server.cluster", notEmpty = true)
    private String eventMeshCluster = "LS";

    @ConfigField(field = "server.hostIp", reload = true)
    private String eventMeshServerIp = null;

    @ConfigField(field = "metaStorage.plugin.server-addr", notEmpty = true)
    private String metaStorageAddr = "";

    @ConfigField(field = "metaStorage.plugin.type", notEmpty = true)
    private String eventMeshMetaStoragePluginType = "nacos";

    @ConfigField(field = "metaStorage.plugin.username")
    private String eventMeshMetaStoragePluginUsername = "";

    @ConfigField(field = "metaStorage.plugin.password")
    private String eventMeshMetaStoragePluginPassword = "";

    @ConfigField(field = "metaStorage.plugin.enabled")
    private boolean eventMeshServerMetaStorageEnable = false;

    @ConfigField(field = "trace.plugin", notEmpty = true)
    private String eventMeshTracePluginType;

    @ConfigField(field = "metrics.plugin", notEmpty = true)
    private List<String> eventMeshMetricsPluginType;

    @ConfigField(field = "security.plugin.type", notEmpty = true)
    private String eventMeshSecurityPluginType = "security";

    @ConfigField(field = "storage.plugin.type", notEmpty = true)
    private String eventMeshStoragePluginType = "standalone";

    @ConfigField(field = "security.validation.type.token", notEmpty = true)
    private boolean eventMeshSecurityValidateTypeToken = false;

    @ConfigField(field = "server.trace.enabled")
    private boolean eventMeshServerTraceEnable = false;

    @ConfigField(field = "server.security.enabled")
    private boolean eventMeshServerSecurityEnable = false;

    @ConfigField(field = "security.publickey")
    private String eventMeshSecurityPublickey = "";

    @ConfigField(field = "server.provide.protocols", reload = true)
    private List<String> eventMeshProvideServerProtocols;

    @ConfigField(reload = true)
    private String meshGroup;

    @ConfigField(field = "server.retry.plugin.type")
    private String eventMeshRetryPluginType = Constants.DEFAULT;

    @ConfigField(field = "registry.plugin.server-addr", notEmpty = true)
    private String registryAddr = "";

    @ConfigField(field = "registry.plugin.type", notEmpty = true)
    private String eventMeshRegistryPluginType = "nacos";

    @ConfigField(field = "registry.plugin.username")
    private String eventMeshRegistryPluginUsername = "";

    @ConfigField(field = "registry.plugin.password")
    private String eventMeshRegistryPluginPassword = "";

    @ConfigField(field = "registry.plugin.enabled")
    private boolean eventMeshRegistryPluginEnabled = false;

    @ConfigField(field = "connector.plugin.type")
    private String eventMeshConnectorPluginType;

    @ConfigField(field = "connector.plugin.name")
    private String eventMeshConnectorPluginName;

    @ConfigField(field = "connector.plugin.enabled")
    private boolean eventMeshConnectorPluginEnable = false;

    // ========== Unified Runtime: Connector Runtime ==========

    @ConfigField(field = "connector.plugin.config.path")
    private String eventMeshConnectorConfigPath = "conf/connectors/";

    @ConfigField(field = "connector.thread.pool.size")
    private int eventMeshConnectorThreadPoolSize = 4;

    @ConfigField(field = "connector.max.retry")
    private int eventMeshConnectorMaxRetry = 3;

    @ConfigField(field = "connector.max.count")
    private int eventMeshConnectorMaxCount = 16;

    @ConfigField(field = "connector.pool.mode")
    private String eventMeshConnectorPoolMode = "DEDICATED";

    @ConfigField(field = "connector.verify.enabled")
    private boolean eventMeshConnectorVerifyEnabled = false;

    // ========== Unified Runtime: Admin Server ==========

    @ConfigField(field = "admin.server.enabled")
    private boolean eventMeshAdminServerEnabled = true;

    @ConfigField(field = "admin.server.required")
    private boolean eventMeshAdminServerRequired = false;

    @ConfigField(field = "admin.server.address")
    private String eventMeshAdminServerAddress = "localhost:50051";

    @ConfigField(field = "admin.server.registry.type")
    private String eventMeshAdminServerRegistryType = "static";

    @ConfigField(field = "admin.server.heartbeat.interval.seconds")
    private int eventMeshAdminHeartbeatIntervalSeconds = 5;

    @ConfigField(field = "admin.server.monitor.report.interval.seconds")
    private int eventMeshAdminMonitorReportIntervalSeconds = 30;

    // ========== Unified Runtime: Offset Management ==========

    @ConfigField(field = "offset.local.enabled")
    private boolean eventMeshOffsetLocalEnabled = true;

    @ConfigField(field = "offset.local.path")
    private String eventMeshOffsetLocalPath = "data/offset/";

    @ConfigField(field = "offset.remote.enabled")
    private boolean eventMeshOffsetRemoteEnabled = false;

    @ConfigField(field = "offset.remote.sync.interval.seconds")
    private int eventMeshOffsetRemoteSyncIntervalSeconds = 60;

    // ========== Unified Runtime: Pipeline ==========

    @ConfigField(field = "pipeline.ingress.filters")
    private String eventMeshPipelineIngressFilters = "auth,ratelimit,protocol";

    @ConfigField(field = "pipeline.ingress.transformers")
    private String eventMeshPipelineIngressTransformers = "protocol,enrichment";

    @ConfigField(field = "pipeline.egress.filters")
    private String eventMeshPipelineEgressFilters = "acl,sizelimit";

    @ConfigField(field = "pipeline.egress.transformers")
    private String eventMeshPipelineEgressTransformers = "protocol";

    @ConfigField(field = "pipeline.dlq.enabled")
    private boolean eventMeshPipelineDlqEnabled = true;

    @ConfigField(field = "pipeline.dlq.topic")
    private String eventMeshPipelineDlqTopic = "eventmesh-dlq";

    // ========== Unified Runtime: A2A ==========

    @ConfigField(field = "a2a.enabled")
    private boolean eventMeshA2aEnabled = false;

    @ConfigField(field = "a2a.gateway.port")
    private int eventMeshA2aGatewayPort = 8080;

    @ConfigField(field = "a2a.registry.ttl.seconds")
    private int eventMeshA2aRegistryTtlSeconds = 30;

    @ConfigField(field = "a2a.sse.max.connections")
    private int eventMeshA2aSseMaxConnections = 1000;

    // ========== Unified Runtime: FilePersistentOffsetStore ==========

    @ConfigField(field = "file.offset.store.flush.interval.seconds")
    private int eventMeshFileOffsetStoreFlushIntervalSeconds = 10;

    // ========== Unified Runtime: Trace Context ==========

    @ConfigField(field = "pipeline.trace.enabled")
    private boolean eventMeshPipelineTraceEnabled = true;

    public void reload() {

        if (Strings.isNullOrEmpty(this.eventMeshServerIp)) {
            this.eventMeshServerIp = IPUtils.getLocalAddress();
        }

        if (CollectionUtils.isEmpty(eventMeshProvideServerProtocols)) {
            this.eventMeshProvideServerProtocols = Collections.singletonList(HTTP);
        }

        meshGroup = String.join("-", this.eventMeshEnv, this.eventMeshIDC, this.eventMeshCluster, this.sysID);
    }
}
