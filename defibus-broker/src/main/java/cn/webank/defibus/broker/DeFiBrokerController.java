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

package cn.webank.defibus.broker;

import cn.webank.defibus.broker.client.AdjustQueueNumStrategy;
import cn.webank.defibus.broker.client.DeFiConsumerManager;
import cn.webank.defibus.broker.client.DeFiProducerManager;
import cn.webank.defibus.broker.consumequeue.ClientRebalanceResultManager;
import cn.webank.defibus.broker.consumequeue.ConsumeQueueManager;
import cn.webank.defibus.broker.consumequeue.MessageRedirectManager;
import cn.webank.defibus.broker.monitor.QueueListeningMonitor;
import cn.webank.defibus.broker.net.DeFiBusBroker2Client;
import cn.webank.defibus.broker.processor.DeFiAdminBrokerProcessor;
import cn.webank.defibus.broker.processor.DeFiClientManageProcessor;
import cn.webank.defibus.broker.processor.DeFiPullMessageProcessor;
import cn.webank.defibus.broker.processor.DeFiReplyMessageProcessor;
import cn.webank.defibus.broker.processor.DeFiSendMessageProcessor;
import cn.webank.defibus.broker.topic.DeFiTopicConfigManager;
import cn.webank.defibus.common.DeFiBusBrokerConfig;
import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import cn.webank.defibus.common.util.ReflectUtil;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.Validate;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.broker.client.ConsumerIdsChangeListener;
import org.apache.rocketmq.broker.out.BrokerOuterAPI;
import org.apache.rocketmq.broker.processor.PullMessageProcessor;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.ThreadFactoryImpl;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.constant.PermName;
import org.apache.rocketmq.common.namesrv.TopAddressing;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.remoting.RemotingServer;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiBrokerController extends BrokerController {
    private static final Logger LOG = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
    private static final Logger LOG_WATER_MARK = LoggerFactory.getLogger(LoggerName.WATER_MARK_LOGGER_NAME);

    private final DeFiProducerManager producerManager;
    private final DeFiConsumerManager consumerManager;
    private final DeFiBusBroker2Client deFiBusBroker2Client;
    private final ExecutorService deFiManageExecutor;
    private final ExecutorService sendReplyMessageExecutor;
    private final ExecutorService pushReplyMessageExecutor;
    private final BlockingQueue<Runnable> sendReplyThreadPoolQueue;
    private final BlockingQueue<Runnable> pushReplyThreadPoolQueue;
    private final ScheduledExecutorService deFiScheduledExecutorService;
    private final ScheduledThreadPoolExecutor sendReplyScheduledExecutorService;

    private RemotingServer fastRemotingServer = null;
    private final ConsumeQueueManager consumeQueueManager;
    private final DeFiBusBrokerConfig deFiBusBrokerConfig;
    private final DeFiTopicConfigManager extTopicConfigManager;
    private final QueueListeningMonitor queueListeningMonitor;

    private DeFiPullMessageProcessor deFiPullMessageProcessor;
    private MessageRedirectManager messageRedirectManager;
    private ClientRebalanceResultManager clientRebalanceResultManager;

    public DeFiBrokerController(BrokerConfig brokerConfig, NettyServerConfig nettyServerConfig,
        NettyClientConfig nettyClientConfig, MessageStoreConfig messageStoreConfig,
        DeFiBusBrokerConfig deFiBusBrokerConfig) {
        super(brokerConfig, nettyServerConfig, nettyClientConfig, messageStoreConfig);
        producerManager = new DeFiProducerManager();

        ConsumerIdsChangeListener consumerIdsChangeListener = (ConsumerIdsChangeListener) ReflectUtil.getSimpleProperty(BrokerController.class, this, "consumerIdsChangeListener");
        AdjustQueueNumStrategy adjustQueueNumStrategy = new AdjustQueueNumStrategy(this);
        consumerManager = new DeFiConsumerManager(consumerIdsChangeListener, adjustQueueNumStrategy);

        this.deFiManageExecutor =
            Executors.newFixedThreadPool(brokerConfig.getClientManageThreadPoolNums(), new ThreadFactoryImpl(
                "ClientManageThread_"));
        deFiBusBroker2Client = new DeFiBusBroker2Client(this);
        this.sendReplyThreadPoolQueue = new LinkedBlockingQueue<Runnable>(deFiBusBrokerConfig.getSendReplyThreadPoolQueueCapacity());
        this.pushReplyThreadPoolQueue = new LinkedBlockingQueue<Runnable>(deFiBusBrokerConfig.getPushReplyThreadPoolQueueCapacity());

        this.sendReplyMessageExecutor = new ThreadPoolExecutor(//
            deFiBusBrokerConfig.getSendReplyMessageThreadPoolNums(),//
            deFiBusBrokerConfig.getSendReplyMessageThreadPoolNums(),//
            1000 * 60,//
            TimeUnit.MILLISECONDS,//
            this.sendReplyThreadPoolQueue,//
            new ThreadFactoryImpl("sendReplyMessageThread_"));
        this.pushReplyMessageExecutor = new ThreadPoolExecutor(//
            deFiBusBrokerConfig.getPushReplyMessageThreadPoolNums(),//
            deFiBusBrokerConfig.getPushReplyMessageThreadPoolNums(),//
            1000 * 60,//
            TimeUnit.MILLISECONDS,//
            this.pushReplyThreadPoolQueue,//
            new ThreadFactoryImpl("pushReplyMessageThread_"));

        this.consumeQueueManager = ConsumeQueueManager.onlyInstance();
        consumeQueueManager.setBrokerController(this);
        extTopicConfigManager = new DeFiTopicConfigManager(this);

        BrokerOuterAPI brokerOuterAPI = super.getBrokerOuterAPI();
        ReflectUtil.setSimpleProperty(BrokerController.class, this, "brokerOuterAPI", brokerOuterAPI);

        String wsAddr = deFiBusBrokerConfig.getRmqAddressServerDomain() + "/" + deFiBusBrokerConfig.getRmqAddressServerSubGroup();
        TopAddressing topAddressing = (TopAddressing) ReflectUtil.getSimpleProperty(BrokerOuterAPI.class, brokerOuterAPI, "topAddressing");
        ReflectUtil.setSimpleProperty(TopAddressing.class, topAddressing, "wsAddr", wsAddr);

        if (this.getBrokerConfig().getNamesrvAddr() != null) {
            brokerOuterAPI.updateNameServerAddressList(this.getBrokerConfig().getNamesrvAddr());
            LOG.info("user specfied name server address: {}", this.getBrokerConfig().getNamesrvAddr());
        }

        deFiScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "brokerControllerScheduledThread");
                t.setDaemon(true);
                return t;
            }
        });

        sendReplyScheduledExecutorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "sendReplyScheduledThread");
                t.setDaemon(true);
                return t;
            }
        });

        this.deFiBusBrokerConfig = deFiBusBrokerConfig;
        this.getConfiguration().registerConfig(deFiBusBrokerConfig);
        this.messageRedirectManager = new MessageRedirectManager(this);
        this.clientRebalanceResultManager = new ClientRebalanceResultManager(this);
        this.queueListeningMonitor = new QueueListeningMonitor(this);
        DeFiBusBrokerStartup.setDeFiBrokerController(this);
    }

    @Override
    public boolean initialize() throws CloneNotSupportedException {
        boolean result = super.initialize();

        result = result && this.extTopicConfigManager.load();

        //reset the lastDeliverOffsetTable as offsetTable of consumer
        consumeQueueManager.load();

        String rrTopic = this.getBrokerConfig().getBrokerClusterName() + "-" + DeFiBusConstant.RR_REPLY_TOPIC;
        if (getTopicConfigManager().selectTopicConfig(rrTopic) == null) {
            TopicConfig topicConfig = new TopicConfig(rrTopic);
            topicConfig.setWriteQueueNums(1);
            topicConfig.setReadQueueNums(1);
            topicConfig.setPerm(PermName.PERM_INHERIT | PermName.PERM_READ | PermName.PERM_WRITE);
            this.getTopicConfigManager().updateTopicConfig(topicConfig);
        }
        this.getTopicConfigManager().getSystemTopic().add(rrTopic);

        return result;
    }

    public DeFiTopicConfigManager getExtTopicConfigManager() {
        return DeFiBrokerController.this.extTopicConfigManager;
    }

    public void start() throws Exception {
        super.start();
        deFiScheduledExecutorService.scheduleAtFixedRate(() -> {
            this.getConsumerOffsetManager().scanUnsubscribedTopic();
            this.getConsumeQueueManager().scanUnsubscribedTopic();
        }, 3600 * 1000, 3600 * 1000, TimeUnit.MILLISECONDS);
        this.queueListeningMonitor.start();
    }

    public void scheduleTask(Runnable task, long delay) {
        deFiScheduledExecutorService.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    public void scheduleTaskAtFixedRate(Runnable task, long delay, long period) {
        deFiScheduledExecutorService.scheduleAtFixedRate(task, delay, period, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        deFiManageExecutor.shutdown();
        sendReplyMessageExecutor.shutdown();
        pushReplyMessageExecutor.shutdown();

        deFiScheduledExecutorService.shutdown();
        sendReplyScheduledExecutorService.shutdown();

        messageRedirectManager.shutdown();
        this.queueListeningMonitor.shutdown();

        super.shutdown();
    }

    public void registerProcessor() {
        super.registerProcessor();
        fastRemotingServer = (RemotingServer) ReflectUtil.getSimpleProperty(BrokerController.class, this, "fastRemotingServer");
        Validate.notNull(fastRemotingServer, "fastRemotingServer is null");

        DeFiReplyMessageProcessor sendDirectMessageProcessor = new DeFiReplyMessageProcessor(this);
        super.getRemotingServer().registerProcessor(DeFiBusRequestCode.SEND_DIRECT_MESSAGE, sendDirectMessageProcessor, this.sendReplyMessageExecutor);
        super.getRemotingServer().registerProcessor(DeFiBusRequestCode.SEND_DIRECT_MESSAGE_V2, sendDirectMessageProcessor, this.sendReplyMessageExecutor);
        fastRemotingServer.registerProcessor(DeFiBusRequestCode.SEND_DIRECT_MESSAGE, sendDirectMessageProcessor, this.sendReplyMessageExecutor);
        fastRemotingServer.registerProcessor(DeFiBusRequestCode.SEND_DIRECT_MESSAGE_V2, sendDirectMessageProcessor, this.sendReplyMessageExecutor);

        DeFiAdminBrokerProcessor extAdminBrokerProcessor = new DeFiAdminBrokerProcessor(this);
        ExecutorService adminBrokerExecutor = (ExecutorService) ReflectUtil.getSimpleProperty(BrokerController.class,
            this, "adminBrokerExecutor");
        super.getRemotingServer().registerProcessor(DeFiBusRequestCode.GET_CONSUME_STATS_V2, extAdminBrokerProcessor, adminBrokerExecutor);
        super.getRemotingServer().registerProcessor(RequestCode.GET_BROKER_RUNTIME_INFO, extAdminBrokerProcessor, adminBrokerExecutor);
        fastRemotingServer.registerProcessor(DeFiBusRequestCode.GET_CONSUME_STATS_V2, extAdminBrokerProcessor, adminBrokerExecutor);
        fastRemotingServer.registerProcessor(RequestCode.GET_BROKER_RUNTIME_INFO, extAdminBrokerProcessor, adminBrokerExecutor);
        super.getRemotingServer().registerProcessor(RequestCode.UPDATE_AND_CREATE_TOPIC, extAdminBrokerProcessor, adminBrokerExecutor);
        fastRemotingServer.registerProcessor(RequestCode.UPDATE_AND_CREATE_TOPIC, extAdminBrokerProcessor, adminBrokerExecutor);

        DeFiSendMessageProcessor deFiSendMessageProcessor = new DeFiSendMessageProcessor(this);
        ExecutorService sendMessageExecutor = (ExecutorService) ReflectUtil.getSimpleProperty(BrokerController.class,
            this, "sendMessageExecutor");
        super.getRemotingServer().registerProcessor(RequestCode.SEND_MESSAGE, deFiSendMessageProcessor, sendMessageExecutor);
        super.getRemotingServer().registerProcessor(RequestCode.SEND_MESSAGE_V2, deFiSendMessageProcessor, sendMessageExecutor);
        super.getRemotingServer().registerProcessor(RequestCode.SEND_BATCH_MESSAGE, deFiSendMessageProcessor, sendMessageExecutor);
        super.getRemotingServer().registerProcessor(RequestCode.CONSUMER_SEND_MSG_BACK, deFiSendMessageProcessor, sendMessageExecutor);
        this.fastRemotingServer.registerProcessor(RequestCode.SEND_MESSAGE, deFiSendMessageProcessor, sendMessageExecutor);
        this.fastRemotingServer.registerProcessor(RequestCode.SEND_MESSAGE_V2, deFiSendMessageProcessor, sendMessageExecutor);
        this.fastRemotingServer.registerProcessor(RequestCode.SEND_BATCH_MESSAGE, deFiSendMessageProcessor, sendMessageExecutor);
        this.fastRemotingServer.registerProcessor(RequestCode.CONSUMER_SEND_MSG_BACK, deFiSendMessageProcessor, sendMessageExecutor);

        deFiPullMessageProcessor = new DeFiPullMessageProcessor(this);
        ExecutorService pullMessageExecutor = (ExecutorService) ReflectUtil.getSimpleProperty(BrokerController.class,
            this, "pullMessageExecutor");
        super.getRemotingServer().registerProcessor(RequestCode.PULL_MESSAGE, deFiPullMessageProcessor, pullMessageExecutor);
        this.fastRemotingServer.registerProcessor(RequestCode.PULL_MESSAGE, deFiPullMessageProcessor, pullMessageExecutor);

        DeFiClientManageProcessor deFiClientManageProcessor = new DeFiClientManageProcessor(this);
        super.getRemotingServer().registerProcessor(DeFiBusRequestCode.GET_CONSUMER_LIST_BY_GROUP_AND_TOPIC, deFiClientManageProcessor, deFiManageExecutor);
        fastRemotingServer.registerProcessor(DeFiBusRequestCode.GET_CONSUMER_LIST_BY_GROUP_AND_TOPIC, deFiClientManageProcessor, deFiManageExecutor);

    }

    public DeFiBusBroker2Client getDeFiBusBroker2Client() {
        return deFiBusBroker2Client;
    }

    @Override
    public DeFiProducerManager getProducerManager() {
        return producerManager;
    }

    @Override
    public void printWaterMark() {
        LOG_WATER_MARK.info("{\"SendQueueSize\":\"{}\",\"PullQueueSize\":\"{}\",\"GotQueueSize\":\"{}\",\"PushQueueSize\":\"{}\",\"SendSlowTimeMills\":\"{}\",\"PullSlowTimeMills\":\"{}\",\"HeartbeatQueueSize\":\"{}\"}",
            this.getSendThreadPoolQueue().size(),
            this.getPullThreadPoolQueue().size(),
            this.sendReplyThreadPoolQueue.size(),
            this.pushReplyThreadPoolQueue.size(),
            this.headSlowTimeMills4SendThreadPoolQueue(),
            this.headSlowTimeMills4PullThreadPoolQueue(),
            this.getHeartbeatThreadPoolQueue().size());
    }

    public DeFiBusBrokerConfig getDeFiBusBrokerConfig() {
        return deFiBusBrokerConfig;
    }

    public ExecutorService getPushReplyMessageExecutor() {
        return pushReplyMessageExecutor;
    }

    public ExecutorService getSendReplyMessageExecutor() {
        return sendReplyMessageExecutor;
    }

    public ScheduledThreadPoolExecutor getSendReplyScheduledExecutorService() {
        return sendReplyScheduledExecutorService;
    }

    public ConsumeQueueManager getConsumeQueueManager() {
        return consumeQueueManager;
    }

    public PullMessageProcessor getPullMessageProcessor() {
        return deFiPullMessageProcessor;
    }

    @Override
    public DeFiConsumerManager getConsumerManager() {
        return this.consumerManager;
    }

    public MessageRedirectManager getMessageRedirectManager() {
        return messageRedirectManager;
    }

    public ClientRebalanceResultManager getClientRebalanceResultManager() {
        return clientRebalanceResultManager;
    }
}
