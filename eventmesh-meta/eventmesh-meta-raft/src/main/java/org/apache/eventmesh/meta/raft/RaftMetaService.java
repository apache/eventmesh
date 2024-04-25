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

package org.apache.eventmesh.meta.raft;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.MetaService;
import org.apache.eventmesh.api.meta.MetaServiceListener;
import org.apache.eventmesh.api.meta.config.EventMeshMetaConfig;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.meta.raft.config.RaftMetaStorageConfiguration;
import org.apache.eventmesh.meta.raft.consts.MetaRaftConstants;
import org.apache.eventmesh.meta.raft.rpc.MetaServerHelper;
import org.apache.eventmesh.meta.raft.rpc.RequestResponse;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alipay.sofa.jraft.CliService;
import com.alipay.sofa.jraft.RaftServiceFactory;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.CliServiceImpl;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.error.RemotingException;
import com.alipay.sofa.jraft.option.CliOptions;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.impl.cli.CliClientServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RaftMetaService implements MetaService {

    private static ObjectMapper objectMapper = new ObjectMapper();

    private ConcurrentMap<String, EventMeshRegisterInfo> eventMeshRegisterInfoMap;

    private final AtomicBoolean initStatus = new AtomicBoolean(false);

    private final AtomicBoolean startStatus = new AtomicBoolean(false);

    RaftMetaStorageConfiguration configuration;

    private JraftServer jraftServer;

    private CliService cliService;

    private CliClientServiceImpl cliClientService;

    private PeerId leader;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();


    @Override
    public void init() throws MetaException {
        if (!initStatus.compareAndSet(false, true)) {
            return;
        }
        eventMeshRegisterInfoMap = new ConcurrentHashMap<>(ConfigurationContextUtil.KEYS.size());
        ConfigService configService = ConfigService.getInstance();
        configuration = configService.buildConfigInstance(RaftMetaStorageConfiguration.class);
    }

    @Override
    public void start() throws MetaException {
        final String dataPath = configuration.getDataPath();
        final String groupId = MetaRaftConstants.GROUP;
        final String serverIdStr = configuration.getSelfIpAndPort();
        final String initConfStr = configuration.getMembersIpAndPort();
        final NodeOptions nodeOptions = new NodeOptions();
        nodeOptions.setElectionTimeoutMs(configuration.getElectionTimeoutMs() * 1000);
        nodeOptions.setDisableCli(false);
        nodeOptions.setSnapshotIntervalSecs(configuration.getSnapshotIntervalSecs());
        final PeerId serverId = new PeerId();
        if (!serverId.parse(serverIdStr)) {
            throw new MetaException("Fail to parse serverId:" + serverIdStr);
        }
        final Configuration initConf = new Configuration();
        if (!initConf.parse(initConfStr)) {
            throw new MetaException("Fail to parse initConf:" + initConfStr);
        }
        initConf.addPeer(serverId);
        nodeOptions.setInitialConf(initConf);
        try {
            jraftServer = new JraftServer(dataPath, groupId, serverId, nodeOptions);
        } catch (IOException e) {
            throw new MetaException("fail to start jraft server", e);
        }
        log.info("Started jraft server at port: {}", jraftServer.getNode().getNodeId().getPeerId().getPort());

        final Configuration conf = new Configuration();
        if (!conf.parse(serverIdStr)) {
            throw new IllegalArgumentException("Fail to parse conf:" + serverIdStr);
        }
        RouteTable.getInstance().updateConfiguration(MetaRaftConstants.GROUP, conf);
        cliService = RaftServiceFactory.createAndInitCliService(new CliOptions());
        cliClientService = (CliClientServiceImpl) ((CliServiceImpl) this.cliService).getCliClientService();
        while (true) {
            try {
                refreshleader();
                if (this.leader != null) {
                    break;
                }
            } catch (Exception e) {
                log.warn("fail to get leader node");
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                RaftMetaService.this.refreshleader();
            } catch (Exception e) {
                log.error("fail to Refresh Leader", e);
            }
        }, configuration.getRefreshLeaderInterval(), configuration.getRefreshLeaderInterval(), TimeUnit.SECONDS);

        startStatus.compareAndSet(false, true);
    }

    private void refreshleader() throws InterruptedException, TimeoutException {
        if (!RouteTable.getInstance().refreshLeader(cliClientService, MetaRaftConstants.GROUP, 3000).isOk()) {
            throw new IllegalStateException("Refresh leader failed");
        }
        this.leader = RouteTable.getInstance().selectLeader(MetaRaftConstants.GROUP);
        log.info("raft Leader is {}", leader);
    }

    @Override
    public void shutdown() throws MetaException {
        if (!startStatus.compareAndSet(true, false)) {
            return;
        }
        scheduledExecutorService.shutdown();
        MetaServerHelper.shutDown();
        if (cliService != null) {
            cliService.shutdown();
        }
        if (cliClientService != null) {
            cliClientService.shutdown();
        }
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws MetaException {
        List<EventMeshDataInfo> listEventMeshDataInfo = new ArrayList<>();
        RequestResponse req = RequestResponse.newBuilder().setValue(MetaRaftConstants.GET).build();
        boolean result = false;
        try {
            CompletableFuture<RequestResponse> future = commit(req, EventClosure.createDefaultEventClosure());
            RequestResponse requestResponse = future.get(3000, TimeUnit.MILLISECONDS);
            if (requestResponse != null) {
                result = requestResponse.getSuccess();
                if (result) {
                    Map<String, String> infoMap = requestResponse.getInfoMap();
                    for (Entry<String, String> entry : infoMap.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (key.startsWith("eventMeshInfo@@")) {
                            if (Objects.isNull(clusterName)) {
                                if (!key.endsWith("@@" + clusterName)) {
                                    continue;
                                }
                            }
                            EventMeshDataInfo eventMeshDataInfo = objectMapper.readValue(value, EventMeshDataInfo.class);
                            listEventMeshDataInfo.add(eventMeshDataInfo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new MetaException("fail to get meta data ", e);
        }
        return listEventMeshDataInfo;
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws MetaException {
        return findEventMeshInfoByCluster(null);
    }

    @Override
    public void registerMetadata(Map<String, String> metadataMap) {
        for (Map.Entry<String, EventMeshRegisterInfo> eventMeshRegisterInfo : eventMeshRegisterInfoMap.entrySet()) {
            EventMeshRegisterInfo registerInfo = eventMeshRegisterInfo.getValue();
            registerInfo.setMetadata(metadataMap);
            this.register(registerInfo);
        }
    }

    @Override
    public Map<String, String> getMetaData(String key, boolean fuzzyEnabled) {
        Map<String, String> resultMap = new HashMap<>();
        RequestResponse req = RequestResponse.newBuilder().setValue(MetaRaftConstants.GET).build();
        boolean result = false;
        try {
            CompletableFuture<RequestResponse> future = commit(req, EventClosure.createDefaultEventClosure());
            RequestResponse requestResponse = future.get(3000, TimeUnit.MILLISECONDS);
            if (requestResponse != null) {
                result = requestResponse.getSuccess();
                if (result) {
                    Map<String, String> infoMap = requestResponse.getInfoMap();
                    resultMap.putAll(infoMap);
                }
            }
        } catch (Exception e) {
            throw new MetaException("fail to get meta data ", e);
        }
        if (fuzzyEnabled) {
            // todo
        } else {
            Map<String, String> finalResult = new HashMap<>();
            finalResult.put(key, resultMap.get(key));
            return finalResult;
        }

        return resultMap;
    }

    @Override
    public void getMetaDataWithListener(MetaServiceListener metaServiceListener, String key) {
        //todo
    }

    @Override
    public void updateMetaData(Map<String, String> metadataMap) {
        String protocol = metadataMap.get(EventMeshMetaConfig.EVENT_MESH_PROTO);
        String reftDataId = "Raft" + "@@" + protocol;
        boolean result = false;
        try {
            RequestResponse req =
                RequestResponse.newBuilder().setValue(MetaRaftConstants.PUT).putInfo(reftDataId, objectMapper.writeValueAsString(metadataMap))
                    .build();
            CompletableFuture<RequestResponse> future = commit(req, EventClosure.createDefaultEventClosure());
            RequestResponse requestResponse = future.get(3000, TimeUnit.MILLISECONDS);
            if (requestResponse != null) {
                result = requestResponse.getSuccess();
            }
        } catch (Exception e) {
            throw new MetaException("fail to serialize ", e);
        }
        if (!result) {
            throw new MetaException("fail to updateMetaData ");
        }

    }

    @Override
    public void removeMetaData(String key) {
        RequestResponse req = RequestResponse.newBuilder().setValue(MetaRaftConstants.DELETE).putInfo(key, StringUtils.EMPTY).build();

        try {
            CompletableFuture<RequestResponse> future = commit(req, EventClosure.createDefaultEventClosure());
            RequestResponse requestResponse = future.get(3000, TimeUnit.MILLISECONDS);
            if (requestResponse != null) {
                boolean result = requestResponse.getSuccess();
                if (result) {
                    throw new MetaException("fail to remove MetaData");
                }
            }
        } catch (Exception e) {
            throw new MetaException("fail to remove MetaData", e);
        }

    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws MetaException {
        //key= eventMeshInfo@@eventMeshName@@IP@@PORT@@protocolType@@CLUSTER_NAME
        String eventMeshName = eventMeshRegisterInfo.getEventMeshName();
        String protocolType = eventMeshRegisterInfo.getProtocolType();
        String[] ipAndPort = eventMeshRegisterInfo.getEndPoint().split(":");
        String clusterName = eventMeshRegisterInfo.getEventMeshClusterName();
        String key = "eventMeshInfo" + "@@" + eventMeshName + "@@" + ipAndPort[0] + "@@" + ipAndPort[1] + "@@" + protocolType + "@@" + clusterName;
        InfoInner infoInner = new InfoInner(eventMeshRegisterInfo);
        String registerInfo = null;
        boolean result = false;
        try {
            registerInfo = objectMapper.writeValueAsString(infoInner);
            RequestResponse req = RequestResponse.newBuilder().setValue(MetaRaftConstants.PUT).putInfo(key, registerInfo).build();
            CompletableFuture<RequestResponse> future = commit(req, EventClosure.createDefaultEventClosure());
            RequestResponse requestResponse = future.get(3000, TimeUnit.MILLISECONDS);
            if (requestResponse != null) {
                result = requestResponse.getSuccess();
            }
        } catch (Exception e) {
            throw new MetaException("fail to serialize ", e);
        }
        if (result) {
            eventMeshRegisterInfoMap.put(eventMeshName, eventMeshRegisterInfo);
        }
        return result;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws MetaException {
        //key= eventMeshInfo@@eventMeshName@@IP@@PORT@@protocolType@@CLUSTER_NAME
        String eventMeshName = eventMeshUnRegisterInfo.getEventMeshName();
        String protocolType = eventMeshUnRegisterInfo.getProtocolType();
        String[] ipAndPort = eventMeshUnRegisterInfo.getEndPoint().split(":");
        String clusterName = eventMeshUnRegisterInfo.getEventMeshClusterName();
        String key = "eventMeshInfo" + "@@" + eventMeshName + "@@" + ipAndPort[0] + "@@" + ipAndPort[1] + "@@" + protocolType + "@@" + clusterName;
        RequestResponse req = RequestResponse.newBuilder().setValue(MetaRaftConstants.DELETE).putInfo(key, StringUtils.EMPTY).build();
        boolean result = false;
        try {
            CompletableFuture<RequestResponse> future = commit(req, EventClosure.createDefaultEventClosure());
            RequestResponse requestResponse = future.get(3000, TimeUnit.MILLISECONDS);
            if (requestResponse != null) {
                result = requestResponse.getSuccess();
            }
        } catch (Exception e) {
            throw new MetaException(e.getMessage(), e);
        }
        if (result) {
            eventMeshRegisterInfoMap.remove(eventMeshName);
        }
        return result;
    }

    @Data
    class InfoInner implements Serializable {

        EventMeshRegisterInfo eventMeshRegisterInfo;

        public InfoInner(EventMeshRegisterInfo eventMeshRegisterInfo) {
            this.eventMeshRegisterInfo = eventMeshRegisterInfo;
        }
    }

    public CompletableFuture<RequestResponse> commit(RequestResponse requestResponse, EventClosure eventClosure)
        throws RemotingException, InterruptedException {
        CompletableFuture<RequestResponse> future = new CompletableFuture<>();
        eventClosure.setFuture(future);
        if (isLeader()) {
            this.jraftServer.getMetaImpl().handle(requestResponse, eventClosure);
        } else {
            invokeToLeader(requestResponse, future);
        }
        return future;
    }

    private void invokeToLeader(RequestResponse requestResponse, CompletableFuture<RequestResponse> future)
        throws RemotingException, InterruptedException {
        cliClientService.getRpcClient().invokeAsync(leader.getEndpoint(), requestResponse, (result, err) -> {
            if (err != null) {
                future.completeExceptionally(err);
                return;
            }
            future.complete((RequestResponse) result);
        }, 3000);
    }


    private boolean isLeader() {
        return this.jraftServer.getFsm().isLeader();
    }
}
