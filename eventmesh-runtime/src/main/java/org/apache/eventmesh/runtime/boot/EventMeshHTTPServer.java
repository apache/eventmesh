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
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.http.processor.AdminMetricsProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.BatchSendMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.BatchSendMessageV2Processor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.HeartBeatProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.LocalSubscribeEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.LocalUnSubscribeEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.RemoteSubscribeEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.RemoteUnSubscribeEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.ReplyMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SendAsyncEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SendAsyncMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SendAsyncRemoteEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SendSyncMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.UnSubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.core.protocol.http.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.http.push.HTTPClientPool;
import org.apache.eventmesh.runtime.core.protocol.http.retry.HttpRetryer;
import org.apache.eventmesh.runtime.metrics.http.HTTPMetricsServer;
import org.apache.eventmesh.runtime.registry.Registry;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import org.assertj.core.util.Lists;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.RateLimiter;

public class EventMeshHTTPServer extends AbstractHTTPServer {

    private EventMeshServer eventMeshServer;

    public ServiceState serviceState;

    private EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    private Registry registry;

    public final ConcurrentHashMap<String /**group*/, ConsumerGroupConf> localConsumerGroupMapping =
        new ConcurrentHashMap<>();

    public final ConcurrentHashMap<String /**group@topic*/, List<Client>> localClientInfoMapping =
        new ConcurrentHashMap<>();

    public EventMeshHTTPServer(EventMeshServer eventMeshServer,
                               EventMeshHTTPConfiguration eventMeshHttpConfiguration) {
        super(eventMeshHttpConfiguration.httpServerPort, eventMeshHttpConfiguration.eventMeshServerUseTls, eventMeshHttpConfiguration);
        this.eventMeshServer = eventMeshServer;
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
        this.registry = eventMeshServer.getRegistry();
    }

    public EventMeshServer getEventMeshServer() {
        return eventMeshServer;
    }

    public EventBus eventBus = new EventBus();

    private ConsumerManager consumerManager;

    private ProducerManager producerManager;

    private HttpRetryer httpRetryer;

    public ThreadPoolExecutor batchMsgExecutor;

    public ThreadPoolExecutor sendMsgExecutor;

    public ThreadPoolExecutor remoteMsgExecutor;

    public ThreadPoolExecutor replyMsgExecutor;

    public ThreadPoolExecutor pushMsgExecutor;

    public ThreadPoolExecutor clientManageExecutor;

    public ThreadPoolExecutor adminExecutor;

    private RateLimiter msgRateLimiter;

    private RateLimiter batchRateLimiter;

    public HTTPClientPool httpClientPool = new HTTPClientPool(10);

    public void shutdownThreadPool() throws Exception {
        batchMsgExecutor.shutdown();
        adminExecutor.shutdown();
        clientManageExecutor.shutdown();
        sendMsgExecutor.shutdown();
        remoteMsgExecutor.shutdown();
        pushMsgExecutor.shutdown();
        replyMsgExecutor.shutdown();
    }

    public void initThreadPool() throws Exception {

        BlockingQueue<Runnable> batchMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshHttpConfiguration.eventMeshServerBatchBlockQSize);
        batchMsgExecutor =
            ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpConfiguration.eventMeshServerBatchMsgThreadNum,
                eventMeshHttpConfiguration.eventMeshServerBatchMsgThreadNum, batchMsgThreadPoolQueue,
                "eventMesh-batchMsg-", true);

        BlockingQueue<Runnable> sendMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshHttpConfiguration.eventMeshServerSendMsgBlockQSize);
        sendMsgExecutor =
            ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpConfiguration.eventMeshServerSendMsgThreadNum,
                eventMeshHttpConfiguration.eventMeshServerSendMsgThreadNum, sendMsgThreadPoolQueue,
                "eventMesh-sendMsg-", true);

        BlockingQueue<Runnable> remoteMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshHttpConfiguration.eventMeshServerRemoteMsgBlockQSize);
        remoteMsgExecutor =
            ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpConfiguration.eventMeshServerRemoteMsgThreadNum,
                eventMeshHttpConfiguration.eventMeshServerRemoteMsgThreadNum, remoteMsgThreadPoolQueue,
                "eventMesh-remoteMsg-", true);

        BlockingQueue<Runnable> pushMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshHttpConfiguration.eventMeshServerPushMsgBlockQSize);
        pushMsgExecutor =
            ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpConfiguration.eventMeshServerPushMsgThreadNum,
                eventMeshHttpConfiguration.eventMeshServerPushMsgThreadNum, pushMsgThreadPoolQueue,
                "eventMesh-pushMsg-", true);

        BlockingQueue<Runnable> clientManageThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshHttpConfiguration.eventMeshServerClientManageBlockQSize);
        clientManageExecutor =
            ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpConfiguration.eventMeshServerClientManageThreadNum,
                eventMeshHttpConfiguration.eventMeshServerClientManageThreadNum, clientManageThreadPoolQueue,
                "eventMesh-clientManage-", true);

        BlockingQueue<Runnable> adminThreadPoolQueue = new LinkedBlockingQueue<Runnable>(50);
        adminExecutor =
            ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpConfiguration.eventMeshServerAdminThreadNum,
                eventMeshHttpConfiguration.eventMeshServerAdminThreadNum, adminThreadPoolQueue, "eventMesh-admin-",
                true);

        BlockingQueue<Runnable> replyMessageThreadPoolQueue = new LinkedBlockingQueue<Runnable>(100);
        replyMsgExecutor =
            ThreadPoolFactory.createThreadPoolExecutor(eventMeshHttpConfiguration.eventMeshServerReplyMsgThreadNum,
                eventMeshHttpConfiguration.eventMeshServerReplyMsgThreadNum, replyMessageThreadPoolQueue,
                "eventMesh-replyMsg-", true);
    }

    public ThreadPoolExecutor getBatchMsgExecutor() {
        return batchMsgExecutor;
    }

    public ThreadPoolExecutor getSendMsgExecutor() {
        return sendMsgExecutor;
    }

    public ThreadPoolExecutor getReplyMsgExecutor() {
        return replyMsgExecutor;
    }

    public ThreadPoolExecutor getPushMsgExecutor() {
        return pushMsgExecutor;
    }

    public ThreadPoolExecutor getClientManageExecutor() {
        return clientManageExecutor;
    }

    public ThreadPoolExecutor getAdminExecutor() {
        return adminExecutor;
    }

    public RateLimiter getMsgRateLimiter() {
        return msgRateLimiter;
    }

    public RateLimiter getBatchRateLimiter() {
        return batchRateLimiter;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void init() throws Exception {
        logger.info("==================EventMeshHTTPServer Initialing==================");
        super.init("eventMesh-http");

        initThreadPool();

        msgRateLimiter = RateLimiter.create(eventMeshHttpConfiguration.eventMeshHttpMsgReqNumPerSecond);
        batchRateLimiter = RateLimiter.create(eventMeshHttpConfiguration.eventMeshBatchMsgRequestNumPerSecond);

        // The MetricsRegistry is singleton, so we can use factory method to get.
        final List<MetricsRegistry> metricsRegistries = Lists.newArrayList();
        Optional.ofNullable(eventMeshHttpConfiguration.eventMeshMetricsPluginType)
            .ifPresent(
                metricsPlugins -> metricsPlugins.forEach(
                    pluginType -> metricsRegistries.add(MetricsPluginFactory.getMetricsRegistry(pluginType))));

        httpRetryer = new HttpRetryer(this);
        httpRetryer.init();

        metrics = new HTTPMetricsServer(this, metricsRegistries);
        metrics.init();

        consumerManager = new ConsumerManager(this);
        consumerManager.init();

        producerManager = new ProducerManager(this);
        producerManager.init();

        registerHTTPRequestProcessor();

        //get the trace-plugin
        if (StringUtils.isNotEmpty(eventMeshHttpConfiguration.eventMeshTracePluginType) && eventMeshHttpConfiguration.eventMeshServerTraceEnable) {

            super.useTrace = eventMeshHttpConfiguration.eventMeshServerTraceEnable;
        }

        logger.info("--------------------------EventMeshHTTPServer inited");
    }

    @Override
    public void start() throws Exception {
        super.start();
        metrics.start();
        consumerManager.start();
        producerManager.start();
        httpRetryer.start();
        if (eventMeshHttpConfiguration.eventMeshServerRegistryEnable) {
            this.register();
        }
        logger.info("--------------------------EventMeshHTTPServer started");
    }

    @Override
    public void shutdown() throws Exception {

        super.shutdown();

        metrics.shutdown();

        consumerManager.shutdown();

        shutdownThreadPool();

        httpClientPool.shutdown();

        producerManager.shutdown();

        httpRetryer.shutdown();

        if (eventMeshHttpConfiguration.eventMeshServerRegistryEnable) {
            this.unRegister();
            registry.shutdown();
        }
        logger.info("--------------------------EventMeshHTTPServer shutdown");
    }

    public boolean register() {
        boolean registerResult = false;
        try {
            String endPoints = IPUtils.getLocalAddress()
                + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshHttpConfiguration.httpServerPort;
            EventMeshRegisterInfo eventMeshRegisterInfo = new EventMeshRegisterInfo();
            eventMeshRegisterInfo.setEventMeshClusterName(eventMeshHttpConfiguration.eventMeshCluster);
            eventMeshRegisterInfo.setEventMeshName(eventMeshHttpConfiguration.eventMeshName + "-" + ConfigurationContextUtil.HTTP);
            eventMeshRegisterInfo.setEndPoint(endPoints);
            eventMeshRegisterInfo.setProtocolType(ConfigurationContextUtil.HTTP);
            registerResult = registry.register(eventMeshRegisterInfo);
        } catch (Exception e) {
            logger.warn("eventMesh register to registry failed", e);
        }

        return registerResult;
    }

    private void unRegister() throws Exception {
        String endPoints = IPUtils.getLocalAddress()
            + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshHttpConfiguration.httpServerPort;
        EventMeshUnRegisterInfo eventMeshUnRegisterInfo = new EventMeshUnRegisterInfo();
        eventMeshUnRegisterInfo.setEventMeshClusterName(eventMeshHttpConfiguration.eventMeshCluster);
        eventMeshUnRegisterInfo.setEventMeshName(eventMeshHttpConfiguration.eventMeshName);
        eventMeshUnRegisterInfo.setEndPoint(endPoints);
        eventMeshUnRegisterInfo.setProtocolType(ConfigurationContextUtil.HTTP);
        boolean registerResult = registry.unRegister(eventMeshUnRegisterInfo);
        if (!registerResult) {
            throw new EventMeshException("eventMesh fail to unRegister");
        }
    }

    public void registerHTTPRequestProcessor() {
        BatchSendMessageProcessor batchSendMessageProcessor = new BatchSendMessageProcessor(this);
        registerProcessor(RequestCode.MSG_BATCH_SEND.getRequestCode(), batchSendMessageProcessor, batchMsgExecutor);

        BatchSendMessageV2Processor batchSendMessageV2Processor = new BatchSendMessageV2Processor(this);
        registerProcessor(RequestCode.MSG_BATCH_SEND_V2.getRequestCode(), batchSendMessageV2Processor,
            batchMsgExecutor);

        SendSyncMessageProcessor sendSyncMessageProcessor = new SendSyncMessageProcessor(this);
        registerProcessor(RequestCode.MSG_SEND_SYNC.getRequestCode(), sendSyncMessageProcessor, sendMsgExecutor);

        SendAsyncMessageProcessor sendAsyncMessageProcessor = new SendAsyncMessageProcessor(this);
        registerProcessor(RequestCode.MSG_SEND_ASYNC.getRequestCode(), sendAsyncMessageProcessor, sendMsgExecutor);

        SendAsyncEventProcessor sendAsyncEventProcessor = new SendAsyncEventProcessor(this);
        registerProcessor(RequestURI.PUBLISH.getRequestURI(), sendAsyncEventProcessor, sendMsgExecutor);

        SendAsyncRemoteEventProcessor sendAsyncRemoteEventProcessor = new SendAsyncRemoteEventProcessor(this);
        registerProcessor(RequestURI.PUBLISH_BRIDGE.getRequestURI(), sendAsyncRemoteEventProcessor, remoteMsgExecutor);

        AdminMetricsProcessor adminMetricsProcessor = new AdminMetricsProcessor(this);
        registerProcessor(RequestCode.ADMIN_METRICS.getRequestCode(), adminMetricsProcessor, adminExecutor);

        HeartBeatProcessor heartProcessor = new HeartBeatProcessor(this);
        registerProcessor(RequestCode.HEARTBEAT.getRequestCode(), heartProcessor, clientManageExecutor);

        SubscribeProcessor subscribeProcessor = new SubscribeProcessor(this);
        registerProcessor(RequestCode.SUBSCRIBE.getRequestCode(), subscribeProcessor, clientManageExecutor);

        LocalSubscribeEventProcessor localSubscribeEventProcessor = new LocalSubscribeEventProcessor(this);
        registerProcessor(RequestURI.SUBSCRIBE_LOCAL.getRequestURI(), localSubscribeEventProcessor, clientManageExecutor);

        RemoteSubscribeEventProcessor remoteSubscribeEventProcessor = new RemoteSubscribeEventProcessor(this);
        registerProcessor(RequestURI.SUBSCRIBE_REMOTE.getRequestURI(), remoteSubscribeEventProcessor, clientManageExecutor);

        UnSubscribeProcessor unSubscribeProcessor = new UnSubscribeProcessor(this);
        registerProcessor(RequestCode.UNSUBSCRIBE.getRequestCode(), unSubscribeProcessor, clientManageExecutor);

        LocalUnSubscribeEventProcessor localUnSubscribeEventProcessor = new LocalUnSubscribeEventProcessor(this);
        registerProcessor(RequestURI.UNSUBSCRIBE_LOCAL.getRequestURI(), localUnSubscribeEventProcessor, clientManageExecutor);

        RemoteUnSubscribeEventProcessor remoteUnSubscribeEventProcessor = new RemoteUnSubscribeEventProcessor(this);
        registerProcessor(RequestURI.UNSUBSCRIBE_REMOTE.getRequestURI(), remoteUnSubscribeEventProcessor, clientManageExecutor);

        ReplyMessageProcessor replyMessageProcessor = new ReplyMessageProcessor(this);
        registerProcessor(RequestCode.REPLY_MESSAGE.getRequestCode(), replyMessageProcessor, replyMsgExecutor);
    }

    public ConsumerManager getConsumerManager() {
        return consumerManager;
    }

    public ProducerManager getProducerManager() {
        return producerManager;
    }

    public ServiceState getServiceState() {
        return serviceState;
    }

    public EventMeshHTTPConfiguration getEventMeshHttpConfiguration() {
        return eventMeshHttpConfiguration;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public HttpRetryer getHttpRetryer() {
        return httpRetryer;
    }
}
