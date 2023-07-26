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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.GrpcRetryer;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ConsumerService;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.HeartbeatService;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.PublisherService;
import org.apache.eventmesh.runtime.metrics.grpc.EventMeshGrpcMonitor;
import org.apache.eventmesh.runtime.registry.Registry;

import org.apache.commons.lang3.RandomUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import org.assertj.core.util.Lists;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import com.google.common.util.concurrent.RateLimiter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshGrpcServer {

    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    private static final int MIN_LIMIT = 5;

    private static final int MAX_LIMIT = 10;

    private Server server;

    private ProducerManager producerManager;

    private ConsumerManager consumerManager;

    private GrpcRetryer grpcRetryer;

    private ThreadPoolExecutor sendMsgExecutor;

    private ThreadPoolExecutor replyMsgExecutor;

    private ThreadPoolExecutor clientMgmtExecutor;

    private ThreadPoolExecutor pushMsgExecutor;

    private List<CloseableHttpClient> httpClientPool;

    private RateLimiter msgRateLimiter;

    private final Registry registry;

    private final Acl acl;

    private final EventMeshServer eventMeshServer;

    private EventMeshGrpcMonitor eventMeshGrpcMonitor;

    public EventMeshGrpcServer(final EventMeshServer eventMeshServer, final EventMeshGrpcConfiguration eventMeshGrpcConfiguration) {
        this.eventMeshServer = eventMeshServer;
        this.eventMeshGrpcConfiguration = eventMeshGrpcConfiguration;
        this.registry = eventMeshServer.getRegistry();
        this.acl = eventMeshServer.getAcl();
    }

    public void init() throws Exception {
        log.info("==================EventMeshGRPCServer Initializing==================");

        initThreadPool();

        initHttpClientPool();

        msgRateLimiter = RateLimiter.create(eventMeshGrpcConfiguration.getEventMeshMsgReqNumPerSecond());

        producerManager = new ProducerManager(this);
        producerManager.init();

        consumerManager = new ConsumerManager(this);
        consumerManager.init();

        grpcRetryer = new GrpcRetryer(this);
        grpcRetryer.init();

        int serverPort = eventMeshGrpcConfiguration.getGrpcServerPort();

        server = ServerBuilder.forPort(serverPort)
            .addService(new ConsumerService(this, sendMsgExecutor, replyMsgExecutor))
            .addService(new HeartbeatService(this, sendMsgExecutor))
            .addService(new PublisherService(this, sendMsgExecutor))
            .build();

        initMetricsMonitor();

        log.info("GRPCServer[port={}] started", serverPort);
        log.info("-----------------EventMeshGRPCServer initialized");
    }

    public void start() throws Exception {
        log.info("---------------EventMeshGRPCServer starting-------------------");

        producerManager.start();
        consumerManager.start();
        grpcRetryer.start();
        server.start();

        if (eventMeshGrpcConfiguration.isEventMeshServerRegistryEnable()) {
            this.register();
        }

        eventMeshGrpcMonitor.start();
        log.info("---------------EventMeshGRPCServer running-------------------");
    }

    public void shutdown() throws Exception {
        log.info("---------------EventMeshGRPCServer stopping-------------------");

        producerManager.shutdown();
        consumerManager.shutdown();
        grpcRetryer.shutdown();

        shutdownThreadPools();
        shutdownHttpClientPool();

        server.shutdown();

        if (eventMeshGrpcConfiguration.isEventMeshServerRegistryEnable()) {
            this.unRegister();
        }

        eventMeshGrpcMonitor.shutdown();
        log.info("---------------EventMeshGRPCServer stopped-------------------");
    }

    public boolean register() {
        boolean registerResult = false;
        try {
            String endPoints = IPUtils.getLocalAddress()
                + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshGrpcConfiguration.getGrpcServerPort();
            EventMeshRegisterInfo eventMeshRegisterInfo = new EventMeshRegisterInfo();
            eventMeshRegisterInfo.setEventMeshClusterName(eventMeshGrpcConfiguration.getEventMeshCluster());
            eventMeshRegisterInfo.setEventMeshName(eventMeshGrpcConfiguration.getEventMeshName() + "-"
                + ConfigurationContextUtil.GRPC);
            eventMeshRegisterInfo.setEndPoint(endPoints);
            eventMeshRegisterInfo.setProtocolType(ConfigurationContextUtil.GRPC);
            registerResult = registry.register(eventMeshRegisterInfo);
        } catch (Exception e) {
            log.warn("eventMesh register to registry failed", e);
        }

        return registerResult;
    }

    private void unRegister() throws Exception {
        String endPoints = IPUtils.getLocalAddress()
            + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshGrpcConfiguration.getGrpcServerPort();
        EventMeshUnRegisterInfo eventMeshUnRegisterInfo = new EventMeshUnRegisterInfo();
        eventMeshUnRegisterInfo.setEventMeshClusterName(eventMeshGrpcConfiguration.getEventMeshCluster());
        eventMeshUnRegisterInfo.setEventMeshName(eventMeshGrpcConfiguration.getEventMeshName());
        eventMeshUnRegisterInfo.setEndPoint(endPoints);
        eventMeshUnRegisterInfo.setProtocolType(ConfigurationContextUtil.GRPC);
        boolean registerResult = registry.unRegister(eventMeshUnRegisterInfo);
        if (!registerResult) {
            throw new EventMeshException("eventMesh fail to unRegister");
        }
    }

    public EventMeshGrpcConfiguration getEventMeshGrpcConfiguration() {
        return this.eventMeshGrpcConfiguration;
    }

    public ProducerManager getProducerManager() {
        return producerManager;
    }

    public ConsumerManager getConsumerManager() {
        return consumerManager;
    }

    public GrpcRetryer getGrpcRetryer() {
        return grpcRetryer;
    }

    public ThreadPoolExecutor getSendMsgExecutor() {
        return sendMsgExecutor;
    }

    public ThreadPoolExecutor getClientMgmtExecutor() {
        return clientMgmtExecutor;
    }

    public ThreadPoolExecutor getPushMsgExecutor() {
        return pushMsgExecutor;
    }

    public RateLimiter getMsgRateLimiter() {
        return msgRateLimiter;
    }

    public CloseableHttpClient getHttpClient() {
        int size = httpClientPool.size();
        return httpClientPool.get(RandomUtils.nextInt(size, 2 * size) % size);
    }

    public EventMeshGrpcMonitor getMetricsMonitor() {
        return eventMeshGrpcMonitor;
    }

    private void initThreadPool() {
        BlockingQueue<Runnable> sendMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.getEventMeshServerSendMsgBlockQueueSize());

        sendMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshGrpcConfiguration.getEventMeshServerSendMsgThreadNum(),
            eventMeshGrpcConfiguration.getEventMeshServerSendMsgThreadNum(), sendMsgThreadPoolQueue,
            "eventMesh-grpc-sendMsg", true);

        BlockingQueue<Runnable> subscribeMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.getEventMeshServerSubscribeMsgBlockQueueSize());

        clientMgmtExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshGrpcConfiguration.getEventMeshServerSubscribeMsgThreadNum(),
            eventMeshGrpcConfiguration.getEventMeshServerSubscribeMsgThreadNum(), subscribeMsgThreadPoolQueue,
            "eventMesh-grpc-clientMgmt", true);

        BlockingQueue<Runnable> pushMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.getEventMeshServerPushMsgBlockQueueSize());

        pushMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshGrpcConfiguration.getEventMeshServerPushMsgThreadNum(),
            eventMeshGrpcConfiguration.getEventMeshServerPushMsgThreadNum(), pushMsgThreadPoolQueue,
            "eventMesh-grpc-pushMsg", true);

        replyMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshGrpcConfiguration.getEventMeshServerReplyMsgThreadNum(),
            eventMeshGrpcConfiguration.getEventMeshServerReplyMsgThreadNum(), sendMsgThreadPoolQueue,
            "eventMesh-grpc-replyMsg", true);
    }

    private void initHttpClientPool() {
        httpClientPool = new ArrayList<>();
        int clientPool = RandomUtils.nextInt(MIN_LIMIT, MAX_LIMIT);
        for (int i = 0; i < clientPool; i++) {
            CloseableHttpClient client = HttpClients.createDefault();
            httpClientPool.add(client);
        }
    }

    private void initMetricsMonitor() throws Exception {
        final List<MetricsRegistry> metricsRegistries = Lists.newArrayList();
        Optional.ofNullable(eventMeshGrpcConfiguration.getEventMeshMetricsPluginType())
            .ifPresent(
                metricsPlugins -> metricsPlugins.forEach(
                    pluginType -> metricsRegistries.add(MetricsPluginFactory.getMetricsRegistry(pluginType))));
        eventMeshGrpcMonitor = new EventMeshGrpcMonitor(this, metricsRegistries);
        eventMeshGrpcMonitor.init();
    }

    private void shutdownThreadPools() {
        sendMsgExecutor.shutdown();
        clientMgmtExecutor.shutdown();
        pushMsgExecutor.shutdown();
        replyMsgExecutor.shutdown();
    }

    private void shutdownHttpClientPool() {
        Iterator<CloseableHttpClient> itr = httpClientPool.iterator();
        while (itr.hasNext()) {
            CloseableHttpClient client = itr.next();
            try {
                client.close();
            } catch (Exception e) {
                // ignored
            }
            itr.remove();
        }
    }

    public Registry getRegistry() {
        return registry;
    }

    public Acl getAcl() {
        return acl;
    }
}
