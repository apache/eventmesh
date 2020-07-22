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

package cn.webank.defibus.broker.monitor;

import cn.webank.defibus.broker.DeFiBrokerController;
import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.broker.client.ConsumerGroupInfo;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.ThreadFactoryImpl;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.common.protocol.header.GetConsumerRunningInfoRequestHeader;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueListeningMonitor {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
    private final DeFiBrokerController deFiBrokerController;
    private final ScheduledExecutorService scheduledExecutorService;

    public QueueListeningMonitor(DeFiBrokerController deFiBrokerController) {
        this.deFiBrokerController = deFiBrokerController;
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryImpl(
            "ScanQueueListeningScheduledThread"));
    }

    public void start() {
        scheduledExecutorService.scheduleAtFixedRate(
            () -> {
                try {
                    scanQueueMissListening();
                } catch (Throwable e) {
                    log.warn("scan queue miss listening : ", e);
                }
            },
            deFiBrokerController.getDeFiBusBrokerConfig().getCheckQueueListeningPeriod(),
            deFiBrokerController.getDeFiBusBrokerConfig().getCheckQueueListeningPeriod(),
            TimeUnit.MINUTES);
    }

    public void shutdown() {
        scheduledExecutorService.shutdown();
    }

    private void scanQueueMissListening() {
        if (!deFiBrokerController.getDeFiBusBrokerConfig().isCheckQueueListening()) {
            return;
        }

        for (Map.Entry<String, ConsumerGroupInfo> entry : deFiBrokerController.getConsumerManager().getConsumerTable().entrySet()) {
            String groupName = entry.getKey();
            ConsumerGroupInfo groupInfo = entry.getValue();
            if (groupInfo.getMessageModel().equals(MessageModel.BROADCASTING)) {
                continue;
            }

            boolean rebalanceRecently = false;

            Map<String/*topic*/, Integer/*listening queue count*/> listenQueueCountMap = new HashMap<>();

            groupLoop:
            for (Channel channel : groupInfo.getChannelInfoTable().keySet()) {
                ClientChannelInfo clientChannelInfo = groupInfo.getChannelInfoTable().get(channel);

                if (clientChannelInfo.getVersion() < MQVersion.Version.V3_1_8_SNAPSHOT.ordinal()) {
                    log.warn("The Consumer <{}> Version <{}> too low to finish, please upgrade it to V3_1_8_SNAPSHOT",
                        clientChannelInfo.getClientId(),
                        MQVersion.getVersionDesc(clientChannelInfo.getVersion()));
                    continue;
                }

                ConsumerRunningInfo runningInfo = callConsumer(groupName, clientChannelInfo);

                if (runningInfo == null) {
                    log.warn("[MISS LISTENING] clientId <{}> group <{}>, runningInfo is null", clientChannelInfo.getClientId(), groupName);
                    continue;
                }

                for (SubscriptionData subscriptionData : runningInfo.getSubscriptionSet()) {
                    if (System.currentTimeMillis() - subscriptionData.getSubVersion() < 2 * 60 * 1000) {
                        rebalanceRecently = true;
                        break groupLoop;
                    }
                }

                //miss listening when subscribing a topic but not listening any queue
                if (runningInfo.getSubscriptionSet() != null && !runningInfo.getSubscriptionSet().isEmpty() && runningInfo.getMqTable().isEmpty()) {
                    log.warn("[MISS LISTENING] clientId <{}> group <{}>, listening none queue", clientChannelInfo.getClientId(), groupName);
                    continue;
                }

                String analyzeProcessQueue = ConsumerRunningInfo.analyzeProcessQueue(clientChannelInfo.getClientId(), runningInfo);
                if (StringUtils.isNotEmpty(analyzeProcessQueue)) {
                    log.warn("[BLOCKED SUBSCRIBER] " + analyzeProcessQueue);
                }

                Set<MessageQueue> mqSet = runningInfo.getMqTable().keySet();
                for (MessageQueue mq : mqSet) {
                    if (!mq.getBrokerName().equals(this.deFiBrokerController.getBrokerConfig().getBrokerName())) {
                        continue;
                    }
                    Integer listenQueueNum = listenQueueCountMap.get(mq.getTopic());
                    if (listenQueueNum == null)
                        listenQueueNum = 0;

                    listenQueueCountMap.put(mq.getTopic(), listenQueueNum + 1);
                }
            }

            //skip if do rebalance in 2 min
            if (!rebalanceRecently) {
                for (String topic : listenQueueCountMap.keySet()) {
                    TopicConfig topicConfig = this.deFiBrokerController.getTopicConfigManager().selectTopicConfig(topic);
                    if (null == topicConfig) {
                        continue;
                    }
                    int queueNum = topicConfig.getReadQueueNums();
                    int listeningNum = listenQueueCountMap.get(topic);
                    if (queueNum != listeningNum) {
                        log.warn("[MISS LISTENING] group <{}>, topic={}, queueNum={}, listeningNum={}", groupName, topic, queueNum, listeningNum);
                    }
                }
            }

        }
    }

    private ConsumerRunningInfo callConsumer(String groupName, ClientChannelInfo clientChannelInfo) {

        String clientId = clientChannelInfo.getClientId();

        GetConsumerRunningInfoRequestHeader requestHeader = new GetConsumerRunningInfoRequestHeader();
        requestHeader.setConsumerGroup(groupName);
        requestHeader.setClientId(clientId);
        requestHeader.setJstackEnable(false);

        try {

            RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.GET_CONSUMER_RUNNING_INFO, requestHeader);

            RemotingCommand response = this.deFiBrokerController.getBroker2Client().callClient(clientChannelInfo.getChannel(), request);
            assert response != null;
            switch (response.getCode()) {
                case ResponseCode.SUCCESS: {
                    byte[] body = response.getBody();
                    if (body != null) {
                        return ConsumerRunningInfo.decode(body, ConsumerRunningInfo.class);
                    }
                }
                default:
                    break;
            }

        } catch (RemotingTimeoutException e) {
            log.warn("consumer <{}> <{}> Timeout: {}", groupName, clientId, RemotingHelper.exceptionSimpleDesc(e));
        } catch (Exception e) {
            log.warn("invoke consumer <{}> <{}> Exception: {}", groupName, clientId, RemotingHelper.exceptionSimpleDesc(e));
        }

        log.warn("consumer <{}> <{}> running info result null", groupName, clientId);
        return null;
    }

}
