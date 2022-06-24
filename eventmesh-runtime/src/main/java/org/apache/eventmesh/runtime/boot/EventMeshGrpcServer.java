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
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.GrpcRetryer;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ConsumerService;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.HeartbeatService;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ProducerService;
import org.apache.eventmesh.runtime.registry.Registry;

import org.apache.commons.lang3.RandomUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import com.google.common.util.concurrent.RateLimiter;

public class EventMeshGrpcServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

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

    private Registry registry;

    public EventMeshGrpcServer(EventMeshGrpcConfiguration eventMeshGrpcConfiguration, Registry registry) {
        this.eventMeshGrpcConfiguration = eventMeshGrpcConfiguration;
        this.registry = registry;
    }

    public void init() throws Exception {
        logger.info("==================EventMeshGRPCServer Initializing==================");

        initThreadPool();

        initHttpClientPool();

        msgRateLimiter = RateLimiter.create(eventMeshGrpcConfiguration.eventMeshMsgReqNumPerSecond);

        producerManager = new ProducerManager(this);
        producerManager.init();

        consumerManager = new ConsumerManager(this);
        consumerManager.init();

        grpcRetryer = new GrpcRetryer(this);
        grpcRetryer.init();

        int serverPort = eventMeshGrpcConfiguration.grpcServerPort;

        server = ServerBuilder.forPort(serverPort)
            .addService(new ProducerService(this, sendMsgExecutor))
            .addService(new ConsumerService(this, clientMgmtExecutor, replyMsgExecutor))
            .addService(new HeartbeatService(this, clientMgmtExecutor))
            .build();

        logger.info("GRPCServer[port={}] started", serverPort);
        logger.info("-----------------EventMeshGRPCServer initialized");
    }

    public void start() throws Exception {
        logger.info("---------------EventMeshGRPCServer starting-------------------");

        producerManager.start();
        consumerManager.start();
        grpcRetryer.start();
        server.start();

        if (eventMeshGrpcConfiguration.eventMeshServerRegistryEnable) {
            this.register();
        }

        logger.info("---------------EventMeshGRPCServer running-------------------");
    }

    public void shutdown() throws Exception {
        logger.info("---------------EventMeshGRPCServer stopping-------------------");

        producerManager.shutdown();
        consumerManager.shutdown();
        grpcRetryer.shutdown();

        shutdownThreadPools();
        shutdownHttpClientPool();

        server.shutdown();

        if (eventMeshGrpcConfiguration.eventMeshServerRegistryEnable) {
            this.unRegister();
            registry.shutdown();
        }

        logger.info("---------------EventMeshGRPCServer stopped-------------------");
    }

    public boolean register() {
        boolean registerResult = false;
        try {
            String endPoints = IPUtils.getLocalAddress()
                + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshGrpcConfiguration.grpcServerPort;
            EventMeshRegisterInfo eventMeshRegisterInfo = new EventMeshRegisterInfo();
            eventMeshRegisterInfo.setEventMeshClusterName(eventMeshGrpcConfiguration.eventMeshCluster);
            eventMeshRegisterInfo.setEventMeshName(eventMeshGrpcConfiguration.eventMeshName + "-" + ConfigurationContextUtil.GRPC);
            eventMeshRegisterInfo.setEndPoint(endPoints);
            eventMeshRegisterInfo.setProtocolType(ConfigurationContextUtil.GRPC);
            registerResult = registry.register(eventMeshRegisterInfo);
        } catch (Exception e) {
            logger.warn("eventMesh register to registry failed", e);
        }

        return registerResult;
    }

    private void unRegister() throws Exception {
        String endPoints = IPUtils.getLocalAddress()
            + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshGrpcConfiguration.grpcServerPort;
        EventMeshUnRegisterInfo eventMeshUnRegisterInfo = new EventMeshUnRegisterInfo();
        eventMeshUnRegisterInfo.setEventMeshClusterName(eventMeshGrpcConfiguration.eventMeshCluster);
        eventMeshUnRegisterInfo.setEventMeshName(eventMeshGrpcConfiguration.eventMeshName);
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

    private void initThreadPool() {
        BlockingQueue<Runnable> sendMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.eventMeshServerSendMsgBlockQueueSize);

        sendMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(eventMeshGrpcConfiguration.eventMeshServerSendMsgThreadNum,
            eventMeshGrpcConfiguration.eventMeshServerSendMsgThreadNum, sendMsgThreadPoolQueue,
            "eventMesh-grpc-sendMsg-%d", true);

        BlockingQueue<Runnable> subscribeMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.eventMeshServerSubscribeMsgBlockQueueSize);

        clientMgmtExecutor = ThreadPoolFactory.createThreadPoolExecutor(eventMeshGrpcConfiguration.eventMeshServerSubscribeMsgThreadNum,
            eventMeshGrpcConfiguration.eventMeshServerSubscribeMsgThreadNum, subscribeMsgThreadPoolQueue,
            "eventMesh-grpc-clientMgmt-%d", true);

        BlockingQueue<Runnable> pushMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.eventMeshServerPushMsgBlockQueueSize);

        pushMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(eventMeshGrpcConfiguration.eventMeshServerPushMsgThreadNum,
            eventMeshGrpcConfiguration.eventMeshServerPushMsgThreadNum, pushMsgThreadPoolQueue,
            "eventMesh-grpc-pushMsg-%d", true);

        BlockingQueue<Runnable> replyMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.eventMeshServerSendMsgBlockQueueSize);

        replyMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(eventMeshGrpcConfiguration.eventMeshServerReplyMsgThreadNum,
            eventMeshGrpcConfiguration.eventMeshServerReplyMsgThreadNum, sendMsgThreadPoolQueue,
            "eventMesh-grpc-replyMsg-%d", true);
    }

    private void initHttpClientPool() {
        httpClientPool = new LinkedList<>();
        for (int i = 0; i < 8; i++) {
            CloseableHttpClient client = HttpClients.createDefault();
            httpClientPool.add(client);
        }
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
}