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

package cn.webank.defibus.broker.processor;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.common.DeFiBusVersion;
import cn.webank.defibus.common.admin.DeFiBusConsumeStats;
import cn.webank.defibus.common.admin.DeFiBusOffsetWrapper;
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import cn.webank.defibus.common.protocol.DeFiBusTopicConfig;
import io.netty.channel.ChannelHandlerContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.body.KVTable;
import org.apache.rocketmq.common.protocol.header.CreateTopicRequestHeader;
import org.apache.rocketmq.common.protocol.header.GetConsumeStatsRequestHeader;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.store.DefaultMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiAdminBrokerProcessor implements NettyRequestProcessor {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
    private final DeFiBrokerController deFiBrokerController;

    public DeFiAdminBrokerProcessor(final DeFiBrokerController deFiBrokerController) {
        this.deFiBrokerController = deFiBrokerController;
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx,
        RemotingCommand request) throws RemotingCommandException {
        switch (request.getCode()) {
            case RequestCode.UPDATE_AND_CREATE_TOPIC:
                return this.updateAndCreateTopic(ctx, request);
            case RequestCode.GET_BROKER_RUNTIME_INFO:
                return this.getBrokerRuntimeInfo(ctx, request);
            case DeFiBusRequestCode.GET_CONSUME_STATS_V2:
                return this.getConsumeStatsV2(ctx, request);
            default:
                break;
        }

        return null;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    private RemotingCommand getBrokerRuntimeInfo(ChannelHandlerContext ctx, RemotingCommand request) {
        final RemotingCommand response = RemotingCommand.createResponseCommand(null);

        HashMap<String, String> runtimeInfo = this.prepareRuntimeInfo();
        KVTable kvTable = new KVTable();
        kvTable.setTable(runtimeInfo);

        byte[] body = kvTable.encode();
        response.setBody(body);
        response.setCode(ResponseCode.SUCCESS);
        response.setRemark(null);
        return response;
    }

    private HashMap<String, String> prepareRuntimeInfo() {
        HashMap<String, String> runtimeInfo = this.deFiBrokerController.getMessageStore().getRuntimeInfo();
        runtimeInfo.put("brokerVersionDesc", DeFiBusVersion.getVersionDesc(DeFiBusVersion.CURRENT_VERSION));
        runtimeInfo.put("brokerVersion", String.valueOf(DeFiBusVersion.CURRENT_VERSION));

        runtimeInfo.put("msgPutTotalYesterdayMorning",
            String.valueOf(this.deFiBrokerController.getBrokerStats().getMsgPutTotalYesterdayMorning()));
        runtimeInfo.put("msgPutTotalTodayMorning", String.valueOf(this.deFiBrokerController.getBrokerStats().getMsgPutTotalTodayMorning()));
        runtimeInfo.put("msgPutTotalTodayNow", String.valueOf(this.deFiBrokerController.getBrokerStats().getMsgPutTotalTodayNow()));

        runtimeInfo.put("msgGetTotalYesterdayMorning",
            String.valueOf(this.deFiBrokerController.getBrokerStats().getMsgGetTotalYesterdayMorning()));
        runtimeInfo.put("msgGetTotalTodayMorning", String.valueOf(this.deFiBrokerController.getBrokerStats().getMsgGetTotalTodayMorning()));
        runtimeInfo.put("msgGetTotalTodayNow", String.valueOf(this.deFiBrokerController.getBrokerStats().getMsgGetTotalTodayNow()));

        runtimeInfo.put("sendThreadPoolQueueSize", String.valueOf(this.deFiBrokerController.getSendThreadPoolQueue().size()));

        runtimeInfo.put("sendThreadPoolQueueCapacity",
            String.valueOf(this.deFiBrokerController.getBrokerConfig().getSendThreadPoolQueueCapacity()));

        runtimeInfo.put("pullThreadPoolQueueSize", String.valueOf(this.deFiBrokerController.getPullThreadPoolQueue().size()));
        runtimeInfo.put("pullThreadPoolQueueCapacity",
            String.valueOf(this.deFiBrokerController.getBrokerConfig().getPullThreadPoolQueueCapacity()));

        runtimeInfo.put("dispatchBehindBytes", String.valueOf(this.deFiBrokerController.getMessageStore().dispatchBehindBytes()));
        runtimeInfo.put("pageCacheLockTimeMills", String.valueOf(this.deFiBrokerController.getMessageStore().lockTimeMills()));

        runtimeInfo.put("sendThreadPoolQueueHeadWaitTimeMills", String.valueOf(this.deFiBrokerController.headSlowTimeMills4SendThreadPoolQueue()));
        runtimeInfo.put("pullThreadPoolQueueHeadWaitTimeMills", String.valueOf(this.deFiBrokerController.headSlowTimeMills4PullThreadPoolQueue()));
        runtimeInfo.put("earliestMessageTimeStamp", String.valueOf(this.deFiBrokerController.getMessageStore().getEarliestMessageTime()));
        runtimeInfo.put("startAcceptSendRequestTimeStamp", String.valueOf(this.deFiBrokerController.getBrokerConfig().getStartAcceptSendRequestTimeStamp()));
        if (this.deFiBrokerController.getMessageStore() instanceof DefaultMessageStore) {
            DefaultMessageStore defaultMessageStore = (DefaultMessageStore) this.deFiBrokerController.getMessageStore();
            runtimeInfo.put("remainTransientStoreBufferNumbs", String.valueOf(defaultMessageStore.remainTransientStoreBufferNumbs()));
            if (defaultMessageStore.getMessageStoreConfig().isTransientStorePoolEnable()) {
                runtimeInfo.put("remainHowManyDataToCommit", MixAll.humanReadableByteCount(defaultMessageStore.getCommitLog().remainHowManyDataToCommit(), false));
            }
            runtimeInfo.put("remainHowManyDataToFlush", MixAll.humanReadableByteCount(defaultMessageStore.getCommitLog().remainHowManyDataToFlush(), false));
        }

        java.io.File commitLogDir = new java.io.File(this.deFiBrokerController.getMessageStoreConfig().getStorePathRootDir());
        if (commitLogDir.exists()) {
            runtimeInfo.put("commitLogDirCapacity", String.format("Total : %s, Free : %s.", MixAll.humanReadableByteCount(commitLogDir.getTotalSpace(), false), MixAll.humanReadableByteCount(commitLogDir.getFreeSpace(), false)));
        }

//        runtimeInfo.put("producerCount", String.valueOf(this.deFiBrokerController.getProducerManager().getProducerChannelTable().size()));
//        runtimeInfo.put("consumerCount", String.valueOf(this.deFiBrokerController.getConsumerManager().getConsumerChannelMap().size()));

        return runtimeInfo;
    }

    private RemotingCommand getConsumeStatsV2(ChannelHandlerContext ctx,
        RemotingCommand request) throws RemotingCommandException {
        final RemotingCommand response = RemotingCommand.createResponseCommand(null);
        final GetConsumeStatsRequestHeader requestHeader =
            (GetConsumeStatsRequestHeader) request.decodeCommandCustomHeader(GetConsumeStatsRequestHeader.class);

        DeFiBusConsumeStats consumeStats = new DeFiBusConsumeStats();

        Set<String> topics = new HashSet<String>();
        if (UtilAll.isBlank(requestHeader.getTopic())) {
            topics = this.deFiBrokerController.getConsumerOffsetManager().whichTopicByConsumer(requestHeader.getConsumerGroup());
        } else {
            topics.add(requestHeader.getTopic());
        }

        for (String topic : topics) {
            TopicConfig topicConfig = this.deFiBrokerController.getTopicConfigManager().selectTopicConfig(topic);
            if (null == topicConfig) {
                log.warn("consumeStats, topic config not exist, {}", topic);
                continue;
            }

            {
                SubscriptionData findSubscriptionData =
                    this.deFiBrokerController.getConsumerManager().findSubscriptionData(requestHeader.getConsumerGroup(), topic);

                if (null == findSubscriptionData //
                    && this.deFiBrokerController.getConsumerManager().findSubscriptionDataCount(requestHeader.getConsumerGroup()) > 0) {
                    log.warn("consumeStats, the consumer group[{}], topic[{}] not exist", requestHeader.getConsumerGroup(), topic);
                    continue;
                }
            }

            for (int i = 0; i < topicConfig.getReadQueueNums(); i++) {
                MessageQueue mq = new MessageQueue();
                mq.setTopic(topic);
                mq.setBrokerName(this.deFiBrokerController.getBrokerConfig().getBrokerName());
                mq.setQueueId(i);

                DeFiBusOffsetWrapper offsetWrapper = new DeFiBusOffsetWrapper();

                long brokerOffset = this.deFiBrokerController.getMessageStore().getMaxOffsetInQueue(topic, i);
                if (brokerOffset < 0)
                    brokerOffset = 0;

                long consumerOffset = this.deFiBrokerController.getConsumerOffsetManager().queryOffset(//
                    requestHeader.getConsumerGroup(), //
                    topic, //
                    i);
                if (consumerOffset < 0)
                    consumerOffset = 0;

                long lastDeliverOffset = this.deFiBrokerController.getConsumeQueueManager().queryDeliverOffset(//
                    requestHeader.getConsumerGroup(), //
                    topic, //
                    i);
                if (lastDeliverOffset < consumerOffset) {
                    lastDeliverOffset = consumerOffset;
                    this.deFiBrokerController.getConsumeQueueManager().recordLastDeliverOffset(requestHeader.getConsumerGroup(), //
                        topic, i, consumerOffset);
                }

                offsetWrapper.setBrokerOffset(brokerOffset);
                offsetWrapper.setConsumerOffset(consumerOffset);
                offsetWrapper.setLastDeliverOffset(lastDeliverOffset);

                long timeOffset = consumerOffset - 1;
                if (timeOffset >= 0) {
                    long lastTimestamp = this.deFiBrokerController.getMessageStore().getMessageStoreTimeStamp(topic, i, timeOffset);
                    if (lastTimestamp > 0) {
                        offsetWrapper.setLastTimestamp(lastTimestamp);
                    }
                }

                consumeStats.getOffsetTable().put(mq, offsetWrapper);
            }

            double consumeTps = this.deFiBrokerController.getBrokerStatsManager().tpsGroupGetNums(requestHeader.getConsumerGroup(), topic);

            consumeTps += consumeStats.getConsumeTps();
            consumeStats.setConsumeTps(consumeTps);
        }

        byte[] body = consumeStats.encode();
        response.setBody(body);
        response.setCode(ResponseCode.SUCCESS);
        response.setRemark(null);
        return response;
    }

    private RemotingCommand updateAndCreateTopic(ChannelHandlerContext ctx,
        RemotingCommand request) throws RemotingCommandException {
        final RemotingCommand response = RemotingCommand.createResponseCommand(null);
        final CreateTopicRequestHeader requestHeader =
            (CreateTopicRequestHeader) request.decodeCommandCustomHeader(CreateTopicRequestHeader.class);
        log.info("updateAndCreateTopic called by {}", RemotingHelper.parseChannelRemoteAddr(ctx.channel()));

        if (requestHeader.getTopic().equals(this.deFiBrokerController.getBrokerConfig().getBrokerClusterName())) {
            String errorMsg = "the topic[" + requestHeader.getTopic() + "] is conflict with system reserved words.";
            log.warn(errorMsg);
            response.setCode(ResponseCode.SYSTEM_ERROR);
            response.setRemark(errorMsg);
            return response;
        }

        try {
            response.setCode(ResponseCode.SUCCESS);
            response.setOpaque(request.getOpaque());
            response.markResponseType();
            response.setRemark(null);
            ctx.writeAndFlush(response);
        } catch (Exception e) {
            log.error("Failed to produce a proper response", e);
        }

        TopicConfig topicConfig = new TopicConfig(requestHeader.getTopic());
        topicConfig.setReadQueueNums(requestHeader.getReadQueueNums());
        topicConfig.setWriteQueueNums(requestHeader.getWriteQueueNums());
        topicConfig.setTopicFilterType(requestHeader.getTopicFilterTypeEnum());
        topicConfig.setPerm(requestHeader.getPerm());
        topicConfig.setTopicSysFlag(requestHeader.getTopicSysFlag() == null ? 0 : requestHeader.getTopicSysFlag());

        this.deFiBrokerController.getTopicConfigManager().updateTopicConfig(topicConfig);

        //set topic queue depth
        HashMap<String, String> extFields = request.getExtFields();
        long maxLength = extFields.get("maxQueueDepth") == null ? DeFiBusTopicConfig.DEFAULT_QUEUE_LENGTH : Long.valueOf(extFields.get("maxQueueDepth"));
        this.deFiBrokerController.getExtTopicConfigManager().updateTopicConfig(
            new DeFiBusTopicConfig(requestHeader.getTopic(), maxLength));

        if (this.deFiBrokerController.getBrokerConfig().getBrokerId() != MixAll.MASTER_ID) {
            return null;
        }

        this.deFiBrokerController.registerIncrementBrokerData(topicConfig, this.deFiBrokerController.getTopicConfigManager().getDataVersion());

        this.deFiBrokerController.getConsumerManager().notifyWhenTopicConfigChange(requestHeader.getTopic());

        return null;
    }
}
