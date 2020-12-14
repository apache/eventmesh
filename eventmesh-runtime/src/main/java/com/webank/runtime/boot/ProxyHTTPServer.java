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

package com.webank.runtime.boot;

import com.google.common.eventbus.EventBus;
import com.webank.runtime.common.ServiceState;
import com.webank.runtime.configuration.ProxyConfiguration;
import com.webank.runtime.core.protocol.http.consumer.ConsumerManager;
import com.webank.runtime.core.protocol.http.processor.*;
import com.webank.runtime.core.protocol.http.producer.ProducerManager;
import com.webank.runtime.core.protocol.http.push.AbstractHTTPPushRequest;
import com.webank.runtime.core.protocol.http.retry.HttpRetryer;
import com.webank.runtime.metrics.http.HTTPMetricsServer;
import com.webank.eventmesh.common.ThreadPoolFactory;
import com.webank.eventmesh.common.protocol.http.common.RequestCode;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

public class ProxyHTTPServer extends AbrstractHTTPServer {

    private ProxyServer proxyServer;

    public ServiceState serviceState;

    private ProxyConfiguration proxyConfiguration;

    public ProxyHTTPServer(ProxyServer proxyServer,
                           ProxyConfiguration proxyConfiguration) {
        super(proxyConfiguration.httpServerPort, proxyConfiguration.proxyServerUseTls);
        this.proxyServer = proxyServer;
        this.proxyConfiguration = proxyConfiguration;
    }

    public ProxyServer getProxyServer() {
        return proxyServer;
    }

    public EventBus eventBus = new EventBus();

    private ConsumerManager consumerManager;

    private ProducerManager producerManager;

    private HttpRetryer httpRetryer;

    public ThreadPoolExecutor batchMsgExecutor;

    public ThreadPoolExecutor sendMsgExecutor;

    public ThreadPoolExecutor replyMsgExecutor;

    public ThreadPoolExecutor pushMsgExecutor;

    public ThreadPoolExecutor clientManageExecutor;

    public ThreadPoolExecutor adminExecutor;

    public void shutdownThreadPool() throws Exception {
        batchMsgExecutor.shutdown();
        adminExecutor.shutdown();
        clientManageExecutor.shutdown();
        sendMsgExecutor.shutdown();
        pushMsgExecutor.shutdown();
        replyMsgExecutor.shutdown();
    }

    public void initThreadPool() throws Exception {

        BlockingQueue<Runnable> batchMsgThreadPoolQueue = new LinkedBlockingQueue<Runnable>(proxyConfiguration.proxyServerBatchBlockQSize);
        batchMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(proxyConfiguration.proxyServerBatchMsgThreadNum,
                proxyConfiguration.proxyServerBatchMsgThreadNum, batchMsgThreadPoolQueue, "proxy-batchmsg-", true);

        BlockingQueue<Runnable> sendMsgThreadPoolQueue = new LinkedBlockingQueue<Runnable>(proxyConfiguration.proxyServerSendMsgBlockQSize);
        sendMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(proxyConfiguration.proxyServerSendMsgThreadNum,
                proxyConfiguration.proxyServerSendMsgThreadNum, sendMsgThreadPoolQueue, "proxy-sendmsg-",true);

        BlockingQueue<Runnable> pushMsgThreadPoolQueue = new LinkedBlockingQueue<Runnable>(proxyConfiguration.proxyServerPushMsgBlockQSize);
        pushMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(proxyConfiguration.proxyServerPushMsgThreadNum,
                proxyConfiguration.proxyServerPushMsgThreadNum, pushMsgThreadPoolQueue, "proxy-pushmsg-",true);

        BlockingQueue<Runnable> clientManageThreadPoolQueue = new LinkedBlockingQueue<Runnable>(proxyConfiguration.proxyServerClientManageBlockQSize);
        clientManageExecutor = ThreadPoolFactory.createThreadPoolExecutor(proxyConfiguration.proxyServerClientManageThreadNum,
                proxyConfiguration.proxyServerClientManageThreadNum, clientManageThreadPoolQueue, "proxy-clientmanage-",true);

        BlockingQueue<Runnable> adminThreadPoolQueue = new LinkedBlockingQueue<Runnable>(50);
        adminExecutor = ThreadPoolFactory.createThreadPoolExecutor(proxyConfiguration.proxyServerAdminThreadNum,
                proxyConfiguration.proxyServerAdminThreadNum, adminThreadPoolQueue, "proxy-admin-",true);

        BlockingQueue<Runnable> replyMessageThreadPoolQueue = new LinkedBlockingQueue<Runnable>(100);
        replyMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(proxyConfiguration.proxyServerReplyMsgThreadNum,
                proxyConfiguration.proxyServerReplyMsgThreadNum, replyMessageThreadPoolQueue, "proxy-replymsg-",true);
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

    public void init() throws Exception {
        logger.info("==================ProxyHTTPServer Initialing==================");
        super.init("proxy-http");

        initThreadPool();

        metrics = new HTTPMetricsServer(this);
        metrics.init();

        consumerManager = new ConsumerManager(this);
        consumerManager.init();

        producerManager = new ProducerManager(this);
        producerManager.init();

        httpRetryer = new HttpRetryer(this);
        httpRetryer.init();

        registerHTTPRequestProcessor();

        logger.info("--------------------------ProxyHTTPServer inited");
    }

    @Override
    public void start() throws Exception {
        super.start();
        metrics.start();
        consumerManager.start();
        producerManager.start();
        httpRetryer.start();
        logger.info("--------------------------ProxyHTTPServer started");
    }

    @Override
    public void shutdown() throws Exception {

        super.shutdown();

        metrics.shutdown();

        consumerManager.shutdown();

        shutdownThreadPool();

        AbstractHTTPPushRequest.httpClientPool.shutdown();

        producerManager.shutdown();

        httpRetryer.shutdown();
        logger.info("--------------------------ProxyHTTPServer shutdown");
    }

    public void registerHTTPRequestProcessor() {
        BatchSendMessageProcessor batchSendMessageProcessor = new BatchSendMessageProcessor(this);
        registerProcessor(RequestCode.MSG_BATCH_SEND.getRequestCode(),
                batchSendMessageProcessor, batchMsgExecutor);

        BatchSendMessageV2Processor batchSendMessageV2Processor = new BatchSendMessageV2Processor(this);
        registerProcessor(RequestCode.MSG_BATCH_SEND_V2.getRequestCode(),
                batchSendMessageV2Processor, batchMsgExecutor);

        SendSyncMessageProcessor sendSyncMessageProcessor = new SendSyncMessageProcessor(this);
        registerProcessor(RequestCode.MSG_SEND_SYNC.getRequestCode(),
                sendSyncMessageProcessor, sendMsgExecutor);

        SendAsyncMessageProcessor sendAsyncMessageProcessor = new SendAsyncMessageProcessor(this);
        registerProcessor(RequestCode.MSG_SEND_ASYNC.getRequestCode(),
                sendAsyncMessageProcessor, sendMsgExecutor);

        AdminMetricsProcessor adminMetricsProcessor = new AdminMetricsProcessor(this);
        registerProcessor(RequestCode.ADMIN_METRICS.getRequestCode(), adminMetricsProcessor, adminExecutor);

        SubscribeProcessor subscribeProcessor = new SubscribeProcessor(this);
        registerProcessor(RequestCode.SUBSCRIBE.getRequestCode(), subscribeProcessor, clientManageExecutor);

        UnSubscribeProcessor unSubscribeProcessor = new UnSubscribeProcessor(this);
        registerProcessor(RequestCode.UNSUBSCRIBE.getRequestCode(), unSubscribeProcessor, clientManageExecutor);

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

    public ProxyConfiguration getProxyConfiguration() {
        return proxyConfiguration;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public HttpRetryer getHttpRetryer() {
        return httpRetryer;
    }
}
