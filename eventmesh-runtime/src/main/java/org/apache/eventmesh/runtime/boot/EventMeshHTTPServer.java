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
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.http.processor.AdminMetricsProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.BatchSendMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.BatchSendMessageV2Processor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.CreateTopicProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.DeleteTopicProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.HandlerService;
import org.apache.eventmesh.runtime.core.protocol.http.processor.HeartBeatProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.LocalSubscribeEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.LocalUnSubscribeEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.QuerySubscriptionProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.RemoteSubscribeEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.RemoteUnSubscribeEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.ReplyMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SendAsyncEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SendAsyncMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SendAsyncRemoteEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SendSyncMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.SubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.UnSubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.WebHookProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.http.push.HTTPClientPool;
import org.apache.eventmesh.runtime.core.protocol.http.retry.HttpRetryer;
import org.apache.eventmesh.runtime.metrics.http.HTTPMetricsServer;
import org.apache.eventmesh.runtime.registry.Registry;
import org.apache.eventmesh.webhook.receive.WebHookController;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import org.assertj.core.util.Lists;


import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.RateLimiter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshHTTPServer extends AbstractHTTPServer {

    private final EventMeshServer eventMeshServer;

    private final EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    private final Registry registry;

    private final Acl acl;

    public final EventBus eventBus = new EventBus();

    private ConsumerManager consumerManager;

    private SubscriptionManager subscriptionManager;

    private ProducerManager producerManager;

    private HttpRetryer httpRetryer;

    private ThreadPoolExecutor batchMsgExecutor;

    private ThreadPoolExecutor sendMsgExecutor;

    private ThreadPoolExecutor remoteMsgExecutor;

    private ThreadPoolExecutor replyMsgExecutor;

    private ThreadPoolExecutor pushMsgExecutor;

    private ThreadPoolExecutor clientManageExecutor;

    private ThreadPoolExecutor adminExecutor;

    private ThreadPoolExecutor webhookExecutor;

    private transient RateLimiter msgRateLimiter;

    private transient RateLimiter batchRateLimiter;

    private final transient HTTPClientPool httpClientPool = new HTTPClientPool(10);

    public EventMeshHTTPServer(final EventMeshServer eventMeshServer, final EventMeshHTTPConfiguration eventMeshHttpConfiguration) {

        super(eventMeshHttpConfiguration.getHttpServerPort(), eventMeshHttpConfiguration.isEventMeshServerUseTls(), eventMeshHttpConfiguration);
        this.eventMeshServer = eventMeshServer;
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
        this.registry = eventMeshServer.getRegistry();
        this.acl = eventMeshServer.getAcl();

    }

    private void initThreadPool() {

        batchMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerBatchMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerBatchMsgThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerBatchBlockQSize()),
            "eventMesh-batchMsg", true);

        sendMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerSendMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerSendMsgThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerSendMsgBlockQSize()),
            "eventMesh-sendMsg", true);

        remoteMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerRemoteMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerRemoteMsgThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerRemoteMsgBlockQSize()),
            "eventMesh-remoteMsg", true);

        pushMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerPushMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerPushMsgThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerPushMsgBlockQSize()),
            "eventMesh-pushMsg", true);

        clientManageExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerClientManageThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerClientManageThreadNum(),
            new LinkedBlockingQueue<>(eventMeshHttpConfiguration.getEventMeshServerClientManageBlockQSize()),
            "eventMesh-clientManage", true);

        adminExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerAdminThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerAdminThreadNum(),
            new LinkedBlockingQueue<>(50), "eventMesh-admin",
            true);

        replyMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerReplyMsgThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerReplyMsgThreadNum(),
            new LinkedBlockingQueue<>(100),
            "eventMesh-replyMsg", true);
    }

    public void shutdownThreadPool() {
        if (batchMsgExecutor != null) {
            batchMsgExecutor.shutdown();
        }
        if (adminExecutor != null) {
            adminExecutor.shutdown();
        }
        if (clientManageExecutor != null) {
            clientManageExecutor.shutdown();
        }
        if (sendMsgExecutor != null) {
            sendMsgExecutor.shutdown();
        }
        if (remoteMsgExecutor != null) {
            remoteMsgExecutor.shutdown();
        }
        if (pushMsgExecutor != null) {
            pushMsgExecutor.shutdown();
        }
        if (replyMsgExecutor != null) {
            replyMsgExecutor.shutdown();
        }
    }

    private void init() throws Exception {
        if (log.isInfoEnabled()) {
            log.info("==================EventMeshHTTPServer Initialing==================");
        }
        super.init("eventMesh-http");

        initThreadPool();

        msgRateLimiter = RateLimiter.create(eventMeshHttpConfiguration.getEventMeshHttpMsgReqNumPerSecond());
        batchRateLimiter = RateLimiter.create(eventMeshHttpConfiguration.getEventMeshBatchMsgRequestNumPerSecond());

        // The MetricsRegistry is singleton, so we can use factory method to get.
        final List<MetricsRegistry> metricsRegistries = Lists.newArrayList();
        Optional.ofNullable(eventMeshHttpConfiguration.getEventMeshMetricsPluginType())
            .ifPresent(
                metricsPlugins -> metricsPlugins.forEach(
                    pluginType -> metricsRegistries.add(MetricsPluginFactory.getMetricsRegistry(pluginType))));

        httpRetryer = new HttpRetryer(this);
        httpRetryer.init();

        this.setMetrics(new HTTPMetricsServer(this, metricsRegistries));

        subscriptionManager = new SubscriptionManager();

        consumerManager = new ConsumerManager(this);
        consumerManager.init();

        producerManager = new ProducerManager(this);
        producerManager.init();

        this.setHandlerService(new HandlerService());
        this.getHandlerService().setMetrics(this.getMetrics());

        //get the trace-plugin
        if (StringUtils.isNotEmpty(eventMeshHttpConfiguration.getEventMeshTracePluginType())
            && eventMeshHttpConfiguration.isEventMeshServerTraceEnable()) {

            this.setUseTrace(eventMeshHttpConfiguration.isEventMeshServerTraceEnable());
        }

        this.getHandlerService().setHttpTrace(new HTTPTrace(eventMeshHttpConfiguration.isEventMeshServerTraceEnable()));

        registerHTTPRequestProcessor();
        this.initWebhook();
        if (log.isInfoEnabled()) {
            log.info("==================EventMeshHTTPServer initialized==================");
        }
    }

    @Override
    public void start() throws Exception {
        init();
        super.start();
        this.getMetrics().start();
        consumerManager.start();
        producerManager.start();
        httpRetryer.start();
        if (eventMeshHttpConfiguration.isEventMeshServerRegistryEnable()) {
            this.register();
        }
        if (log.isInfoEnabled()) {
            log.info("==================EventMeshHTTPServer started==================");
        }
    }

    @Override
    public void shutdown() throws Exception {

        super.shutdown();

        this.getMetrics().shutdown();

        consumerManager.shutdown();

        shutdownThreadPool();

        httpClientPool.shutdown();

        producerManager.shutdown();

        httpRetryer.shutdown();

        if (eventMeshHttpConfiguration.isEventMeshServerRegistryEnable()) {
            this.unRegister();
        }
        if (log.isInfoEnabled()) {
            log.info("==================EventMeshHTTPServer shutdown==================");
        }
    }

    public boolean register() {
        boolean registerResult = false;
        try {
            final String endPoints = IPUtils.getLocalAddress()
                + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshHttpConfiguration.getHttpServerPort();
            final EventMeshRegisterInfo eventMeshRegisterInfo = new EventMeshRegisterInfo();
            eventMeshRegisterInfo.setEventMeshClusterName(eventMeshHttpConfiguration.getEventMeshCluster());
            eventMeshRegisterInfo.setEventMeshName(eventMeshHttpConfiguration.getEventMeshName()
                + "-" + ConfigurationContextUtil.HTTP);
            eventMeshRegisterInfo.setEndPoint(endPoints);
            eventMeshRegisterInfo.setProtocolType(ConfigurationContextUtil.HTTP);
            registerResult = registry.register(eventMeshRegisterInfo);
        } catch (Exception e) {
            log.error("eventMesh register to registry failed", e);
        }

        return registerResult;
    }

    private void unRegister() throws Exception {
        final String endPoints = IPUtils.getLocalAddress()
            + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshHttpConfiguration.getHttpServerPort();
        final EventMeshUnRegisterInfo eventMeshUnRegisterInfo = new EventMeshUnRegisterInfo();
        eventMeshUnRegisterInfo.setEventMeshClusterName(eventMeshHttpConfiguration.getEventMeshCluster());
        eventMeshUnRegisterInfo.setEventMeshName(eventMeshHttpConfiguration.getEventMeshName());
        eventMeshUnRegisterInfo.setEndPoint(endPoints);
        eventMeshUnRegisterInfo.setProtocolType(ConfigurationContextUtil.HTTP);
        final boolean registerResult = registry.unRegister(eventMeshUnRegisterInfo);
        if (!registerResult) {
            throw new EventMeshException("eventMesh fail to unRegister");
        }
    }

    public void registerHTTPRequestProcessor() {
        final BatchSendMessageProcessor batchSendMessageProcessor = new BatchSendMessageProcessor(this);
        registerProcessor(RequestCode.MSG_BATCH_SEND.getRequestCode(), batchSendMessageProcessor, batchMsgExecutor);

        final BatchSendMessageV2Processor batchSendMessageV2Processor = new BatchSendMessageV2Processor(this);
        registerProcessor(RequestCode.MSG_BATCH_SEND_V2.getRequestCode(), batchSendMessageV2Processor,
            batchMsgExecutor);

        final SendSyncMessageProcessor sendSyncMessageProcessor = new SendSyncMessageProcessor(this);
        registerProcessor(RequestCode.MSG_SEND_SYNC.getRequestCode(), sendSyncMessageProcessor, sendMsgExecutor);

        final SendAsyncMessageProcessor sendAsyncMessageProcessor = new SendAsyncMessageProcessor(this);
        registerProcessor(RequestCode.MSG_SEND_ASYNC.getRequestCode(), sendAsyncMessageProcessor, sendMsgExecutor);

        final SendAsyncEventProcessor sendAsyncEventProcessor = new SendAsyncEventProcessor(this);
        this.getHandlerService().register(sendAsyncEventProcessor, sendMsgExecutor);

        final SendAsyncRemoteEventProcessor sendAsyncRemoteEventProcessor = new SendAsyncRemoteEventProcessor(this);
        this.getHandlerService().register(sendAsyncRemoteEventProcessor, remoteMsgExecutor);

        final AdminMetricsProcessor adminMetricsProcessor = new AdminMetricsProcessor(this);
        registerProcessor(RequestCode.ADMIN_METRICS.getRequestCode(), adminMetricsProcessor, adminExecutor);

        final HeartBeatProcessor heartProcessor = new HeartBeatProcessor(this);
        registerProcessor(RequestCode.HEARTBEAT.getRequestCode(), heartProcessor, clientManageExecutor);

        final SubscribeProcessor subscribeProcessor = new SubscribeProcessor(this);
        registerProcessor(RequestCode.SUBSCRIBE.getRequestCode(), subscribeProcessor, clientManageExecutor);

        final LocalSubscribeEventProcessor localSubscribeEventProcessor = new LocalSubscribeEventProcessor(this);
        this.getHandlerService().register(localSubscribeEventProcessor, clientManageExecutor);

        final RemoteSubscribeEventProcessor remoteSubscribeEventProcessor = new RemoteSubscribeEventProcessor(this);
        this.getHandlerService().register(remoteSubscribeEventProcessor, clientManageExecutor);

        final UnSubscribeProcessor unSubscribeProcessor = new UnSubscribeProcessor(this);
        registerProcessor(RequestCode.UNSUBSCRIBE.getRequestCode(), unSubscribeProcessor, clientManageExecutor);

        final LocalUnSubscribeEventProcessor localUnSubscribeEventProcessor = new LocalUnSubscribeEventProcessor(this);
        this.getHandlerService().register(localUnSubscribeEventProcessor, clientManageExecutor);

        final RemoteUnSubscribeEventProcessor remoteUnSubscribeEventProcessor = new RemoteUnSubscribeEventProcessor(this);
        this.getHandlerService().register(remoteUnSubscribeEventProcessor, clientManageExecutor);

        final ReplyMessageProcessor replyMessageProcessor = new ReplyMessageProcessor(this);
        registerProcessor(RequestCode.REPLY_MESSAGE.getRequestCode(), replyMessageProcessor, replyMsgExecutor);

        CreateTopicProcessor createTopicProcessor = new CreateTopicProcessor(this);
        this.getHandlerService().register(createTopicProcessor, clientManageExecutor);

        DeleteTopicProcessor deleteTopicProcessor = new DeleteTopicProcessor(this);
        this.getHandlerService().register(deleteTopicProcessor, clientManageExecutor);

        QuerySubscriptionProcessor querySubscriptionProcessor = new QuerySubscriptionProcessor(this);
        this.getHandlerService().register(querySubscriptionProcessor, clientManageExecutor);

    }

    private void initWebhook() throws Exception {

        webhookExecutor = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshHttpConfiguration.getEventMeshServerWebhookThreadNum(),
            eventMeshHttpConfiguration.getEventMeshServerWebhookThreadNum(),
            new LinkedBlockingQueue<>(100), "eventMesh-webhook", true);
        final WebHookProcessor webHookProcessor = new WebHookProcessor();

        final WebHookController webHookController = new WebHookController();
        webHookController.init();
        webHookProcessor.setWebHookController(webHookController);
        this.getHandlerService().register(webHookProcessor, webhookExecutor);
    }

    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    public ConsumerManager getConsumerManager() {
        return consumerManager;
    }

    public ProducerManager getProducerManager() {
        return producerManager;
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

    public Acl getAcl() {
        return acl;
    }

    public EventMeshServer getEventMeshServer() {
        return eventMeshServer;
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

    public HTTPClientPool getHttpClientPool() {
        return httpClientPool;
    }
}
