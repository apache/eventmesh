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
import org.apache.eventmesh.api.meta.MetaServiceListener;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.meta.raft.config.RaftMetaStorageConfiguration;
import org.apache.eventmesh.meta.raft.consts.MetaRaftConstants;
import org.apache.eventmesh.meta.raft.rpc.MetaServerHelper;
import org.apache.eventmesh.meta.raft.rpc.RequestResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RaftMetaService implements org.apache.eventmesh.api.meta.MetaService {

    private final AtomicBoolean initStatus = new AtomicBoolean(false);

    private final AtomicBoolean startStatus = new AtomicBoolean(false);

    RaftMetaStorageConfiguration configuration;

    private JraftServer jraftServer;

    private CliService cliService;

    private CliClientServiceImpl cliClientService;

    private PeerId leader;

    private Thread refreshThread;

    @Override
    public void init() throws MetaException {
        if (!initStatus.compareAndSet(false, true)) {
            return;
        }
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
        nodeOptions.setElectionTimeoutMs(configuration.getElectionTimeoutMs());
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
        refreshThread = new Thread(() -> {
            try {
                RaftMetaService.this.refreshleader();
                Thread.sleep(configuration.getRefreshLeaderInterval());
            } catch (Exception e) {
                log.error("fail to Refresh Leader", e);
            }
        });
        refreshThread.setName("[Raft-refresh-leader-Thread]");
        refreshThread.setDaemon(true);
        refreshThread.start();
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
        return null;
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws MetaException {
        return null;
    }

    @Override
    public void registerMetadata(Map<String, String> metadataMap) {

    }

    @Override
    public Map<String, String> getMetaData(String key, boolean fuzzyEnabled) {
        return null;
    }

    @Override
    public void getMetaDataWithListener(MetaServiceListener metaServiceListener, String key) {

    }

    @Override
    public void updateMetaData(Map<String, String> metadataMap) {

    }

    @Override
    public void removeMetaData(String key) {

    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws MetaException {
        return false;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws MetaException {
        return false;
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
