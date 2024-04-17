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

import static org.apache.eventmesh.common.Constants.HTTP;

import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerManager;
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
import org.apache.eventmesh.runtime.core.protocol.http.push.HTTPClientPool;
import org.apache.eventmesh.runtime.core.protocol.http.retry.HttpRetryer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
import org.apache.eventmesh.runtime.meta.MetaStorage;
import org.apache.eventmesh.runtime.metrics.http.HTTPMetricsServer;
import org.apache.eventmesh.webhook.receive.WebHookController;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.RateLimiter;

import lombok.extern.slf4j.Slf4j;


/**
 * Add multiple managers to the underlying server
 */
@Slf4j
public class EventMeshHTTPServer extends AbstractHTTPServer {

    private final EventMeshServer eventMeshServer;
    private final EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    private final MetaStorage metaStorage;

    private final Acl acl;
    private final EventBus eventBus = new EventBus();
    private final transient HTTPClientPool httpClientPool = new HTTPClientPool(10);
    private ConsumerManager consumerManager;
    private ProducerManager producerManager;
    private SubscriptionManager subscriptionManager;
    private FilterEngine filterEngine;
    private TransformerEngine transformerEngine;
    private HttpRetryer httpRetryer;
    private transient RateLimiter msgRateLimiter;
    private transient RateLimiter batchRateLimiter;

    public EventMeshHTTPServer(final EventMeshServer eventMeshServer, final EventMeshHTTPConfiguration eventMeshHttpConfiguration) {
        super(eventMeshHttpConfiguration.getHttpServerPort(),
            eventMeshHttpConfiguration.isEventMeshServerUseTls(),
            eventMeshHttpConfiguration);
        this.eventMeshServer = eventMeshServer;
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
        this.metaStorage = eventMeshServer.getMetaStorage();
        this.acl = eventMeshServer.getAcl();
    }

    public void init() throws Exception {
        log.info("==================EventMeshHTTPServer Initialing==================");
        super.init();

        msgRateLimiter = RateLimiter.create(eventMeshHttpConfiguration.getEventMeshHttpMsgReqNumPerSecond());
        batchRateLimiter = RateLimiter.create(eventMeshHttpConfiguration.getEventMeshBatchMsgRequestNumPerSecond());

        // The MetricsRegistry is singleton, so we can use factory method to get.
        final List<MetricsRegistry> metricsRegistries = Lists.newArrayList();
        Optional.ofNullable(eventMeshHttpConfiguration.getEventMeshMetricsPluginType()).ifPresent(
            metricsPlugins -> metricsPlugins.forEach(
                pluginType -> metricsRegistries.add(MetricsPluginFactory.getMetricsRegistry(pluginType))));

        httpRetryer = new HttpRetryer(this);

        super.setMetrics(new HTTPMetricsServer(this, metricsRegistries));
        subscriptionManager = new SubscriptionManager(eventMeshHttpConfiguration.isEventMeshServerMetaStorageEnable(), metaStorage);

        consumerManager = new ConsumerManager(this);
        consumerManager.init();

        producerManager = new ProducerManager(this);
        producerManager.init();

        filterEngine = new FilterEngine(metaStorage, producerManager, consumerManager);

        transformerEngine = new TransformerEngine(metaStorage, producerManager, consumerManager);

        super.setHandlerService(new HandlerService());
        super.getHandlerService().setMetrics(this.getMetrics());

        // get the trace-plugin
        if (StringUtils.isNotEmpty(eventMeshHttpConfiguration.getEventMeshTracePluginType())
            && eventMeshHttpConfiguration.isEventMeshServerTraceEnable()) {
            super.setUseTrace(eventMeshHttpConfiguration.isEventMeshServerTraceEnable());
        }
        super.getHandlerService().setHttpTrace(new HTTPTrace(eventMeshHttpConfiguration.isEventMeshServerTraceEnable()));

        registerHTTPRequestProcessor();

        log.info("==================EventMeshHTTPServer initialized==================");
    }

    @Override
    public void start() throws Exception {
        super.start();
        this.getMetrics().start();

        consumerManager.start();
        producerManager.start();
        httpRetryer.start();
        // filterEngine depend on metaStorage
        if (metaStorage.getStarted().get()) {
            filterEngine.start();
        }

        if (eventMeshHttpConfiguration.isEventMeshServerMetaStorageEnable()) {
            this.register();
        }
        log.info("==================EventMeshHTTPServer started==================");
    }

    @Override
    public void shutdown() throws Exception {

        super.shutdown();

        this.getMetrics().shutdown();

        filterEngine.shutdown();

        transformerEngine.shutdown();

        consumerManager.shutdown();

        httpClientPool.shutdown();

        producerManager.shutdown();

        httpRetryer.shutdown();

        if (eventMeshHttpConfiguration.isEventMeshServerMetaStorageEnable()) {
            this.unRegister();
        }
        log.info("==================EventMeshHTTPServer shutdown==================");
    }

    /**
     * Related to the registry module
     */
    public boolean register() {
        boolean registerResult = false;
        try {
            final String endPoints = IPUtils.getLocalAddress()
                + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshHttpConfiguration.getHttpServerPort();
            final EventMeshRegisterInfo eventMeshRegisterInfo = new EventMeshRegisterInfo();
            eventMeshRegisterInfo.setEventMeshClusterName(eventMeshHttpConfiguration.getEventMeshCluster());
            eventMeshRegisterInfo.setEventMeshName(eventMeshHttpConfiguration.getEventMeshName()
                + "-" + HTTP);
            eventMeshRegisterInfo.setEndPoint(endPoints);
            eventMeshRegisterInfo.setProtocolType(HTTP);
            registerResult = metaStorage.register(eventMeshRegisterInfo);
        } catch (Exception e) {
            log.error("eventMesh register to registry failed", e);
        }

        return registerResult;
    }

    /**
     * Related to the registry module
     */
    private void unRegister() {
        final String endPoints = IPUtils.getLocalAddress()
            + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshHttpConfiguration.getHttpServerPort();
        final EventMeshUnRegisterInfo eventMeshUnRegisterInfo = new EventMeshUnRegisterInfo();
        eventMeshUnRegisterInfo.setEventMeshClusterName(eventMeshHttpConfiguration.getEventMeshCluster());
        eventMeshUnRegisterInfo.setEventMeshName(eventMeshHttpConfiguration.getEventMeshName());
        eventMeshUnRegisterInfo.setEndPoint(endPoints);
        eventMeshUnRegisterInfo.setProtocolType(HTTP);
        final boolean registerResult = metaStorage.unRegister(eventMeshUnRegisterInfo);
        if (!registerResult) {
            throw new EventMeshException("eventMesh fail to unRegister");
        }
    }

    private void registerHTTPRequestProcessor() throws Exception {
        final BatchSendMessageProcessor batchSendMessageProcessor = new BatchSendMessageProcessor(this);
        registerProcessor(RequestCode.MSG_BATCH_SEND.getRequestCode(), batchSendMessageProcessor);

        final BatchSendMessageV2Processor batchSendMessageV2Processor = new BatchSendMessageV2Processor(this);
        registerProcessor(RequestCode.MSG_BATCH_SEND_V2.getRequestCode(), batchSendMessageV2Processor);

        final SendSyncMessageProcessor sendSyncMessageProcessor = new SendSyncMessageProcessor(this);
        registerProcessor(RequestCode.MSG_SEND_SYNC.getRequestCode(), sendSyncMessageProcessor);

        final SendAsyncMessageProcessor sendAsyncMessageProcessor = new SendAsyncMessageProcessor(this);
        registerProcessor(RequestCode.MSG_SEND_ASYNC.getRequestCode(), sendAsyncMessageProcessor);

        final SendAsyncEventProcessor sendAsyncEventProcessor = new SendAsyncEventProcessor(this);
        this.getHandlerService().register(sendAsyncEventProcessor);

        final SendAsyncRemoteEventProcessor sendAsyncRemoteEventProcessor = new SendAsyncRemoteEventProcessor(this);
        this.getHandlerService().register(sendAsyncRemoteEventProcessor);

        final HeartBeatProcessor heartProcessor = new HeartBeatProcessor(this);
        registerProcessor(RequestCode.HEARTBEAT.getRequestCode(), heartProcessor);

        final SubscribeProcessor subscribeProcessor = new SubscribeProcessor(this);
        registerProcessor(RequestCode.SUBSCRIBE.getRequestCode(), subscribeProcessor);

        final LocalSubscribeEventProcessor localSubscribeEventProcessor = new LocalSubscribeEventProcessor(this);
        this.getHandlerService().register(localSubscribeEventProcessor);

        final RemoteSubscribeEventProcessor remoteSubscribeEventProcessor = new RemoteSubscribeEventProcessor(this);
        this.getHandlerService().register(remoteSubscribeEventProcessor);

        final UnSubscribeProcessor unSubscribeProcessor = new UnSubscribeProcessor(this);
        registerProcessor(RequestCode.UNSUBSCRIBE.getRequestCode(), unSubscribeProcessor);

        final LocalUnSubscribeEventProcessor localUnSubscribeEventProcessor = new LocalUnSubscribeEventProcessor(this);
        this.getHandlerService().register(localUnSubscribeEventProcessor);

        final RemoteUnSubscribeEventProcessor remoteUnSubscribeEventProcessor = new RemoteUnSubscribeEventProcessor(this);
        this.getHandlerService().register(remoteUnSubscribeEventProcessor);

        final ReplyMessageProcessor replyMessageProcessor = new ReplyMessageProcessor(this);
        registerProcessor(RequestCode.REPLY_MESSAGE.getRequestCode(), replyMessageProcessor);

        final CreateTopicProcessor createTopicProcessor = new CreateTopicProcessor(this);
        this.getHandlerService().register(createTopicProcessor);

        final DeleteTopicProcessor deleteTopicProcessor = new DeleteTopicProcessor(this);
        this.getHandlerService().register(deleteTopicProcessor);

        final QuerySubscriptionProcessor querySubscriptionProcessor = new QuerySubscriptionProcessor(this);
        this.getHandlerService().register(querySubscriptionProcessor);

        registerWebhook();
    }

    private void registerWebhook() throws Exception {
        final WebHookProcessor webHookProcessor = new WebHookProcessor();
        final WebHookController webHookController = new WebHookController();

        webHookController.init();
        webHookProcessor.setWebHookController(webHookController);

        this.getHandlerService().register(webHookProcessor, super.getHttpThreadPoolGroup().getWebhookExecutor());
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

    public RateLimiter getMsgRateLimiter() {
        return msgRateLimiter;
    }

    public RateLimiter getBatchRateLimiter() {
        return batchRateLimiter;
    }

    public FilterEngine getFilterEngine() {
        return filterEngine;
    }

    public TransformerEngine getTransformerEngine() {
        return transformerEngine;
    }

    public MetaStorage getMetaStorage() {
        return metaStorage;
    }

    public HTTPClientPool getHttpClientPool() {
        return httpClientPool;
    }


}
