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
import cn.webank.defibus.broker.plugin.DeFiPluginMessageStore;
import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.message.DeFiBusMessageConst;
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import cn.webank.defibus.common.protocol.header.ReplyMessageRequestHeader;
import io.netty.channel.ChannelHandlerContext;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.broker.mqtrace.SendMessageContext;
import org.apache.rocketmq.broker.processor.AbstractSendMessageProcessor;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.TopicFilterType;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.constant.PermName;
import org.apache.rocketmq.common.help.FAQUrl;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeaderV2;
import org.apache.rocketmq.common.protocol.header.SendMessageResponseHeader;
import org.apache.rocketmq.common.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.common.sysflag.MessageSysFlag;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.store.DefaultMessageStore;
import org.apache.rocketmq.store.MessageExtBrokerInner;
import org.apache.rocketmq.store.PutMessageResult;
import org.apache.rocketmq.store.config.StorePathConfigHelper;
import org.apache.rocketmq.store.stats.BrokerStatsManager;

public class DeFiReplyMessageProcessor extends AbstractSendMessageProcessor implements NettyRequestProcessor {
    private DeFiBrokerController deFiBrokerController;

    public DeFiReplyMessageProcessor(final DeFiBrokerController deFiBrokerController) {
        super(deFiBrokerController);
        this.deFiBrokerController = deFiBrokerController;
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx,
        RemotingCommand request) throws RemotingCommandException {
        SendMessageContext mqtraceContext = null;
        switch (request.getCode()) {
            case DeFiBusRequestCode.SEND_DIRECT_MESSAGE_V2:
            case DeFiBusRequestCode.SEND_DIRECT_MESSAGE:
                SendMessageRequestHeader requestHeader = parseRequestHeader(request);
                if (requestHeader == null) {
                    return null;
                }

                mqtraceContext = buildMsgContext(ctx, requestHeader);
                this.executeSendMessageHookBefore(ctx, request, mqtraceContext);
                final RemotingCommand response = this.processReplyMessageRequest(ctx, request, mqtraceContext, requestHeader);

                this.executeSendMessageHookAfter(response, mqtraceContext);
                return response;
            default:
                log.warn("Unsupported request code :" + request.getCode());
        }
        return null;
    }

    @Override
    protected SendMessageRequestHeader parseRequestHeader(RemotingCommand request) throws RemotingCommandException {
        SendMessageRequestHeaderV2 requestHeaderV2 = null;
        SendMessageRequestHeader requestHeader = null;
        switch (request.getCode()) {
            case DeFiBusRequestCode.SEND_DIRECT_MESSAGE_V2:
                requestHeaderV2 =
                    (SendMessageRequestHeaderV2) request
                        .decodeCommandCustomHeader(SendMessageRequestHeaderV2.class);
            case DeFiBusRequestCode.SEND_DIRECT_MESSAGE:
                if (null == requestHeaderV2) {
                    requestHeader =
                        (SendMessageRequestHeader) request
                            .decodeCommandCustomHeader(SendMessageRequestHeader.class);
                } else {
                    requestHeader = SendMessageRequestHeaderV2.createSendMessageRequestHeaderV1(requestHeaderV2);
                }
            default:
                break;
        }
        return requestHeader;
    }

    @Override
    public boolean rejectRequest() {
        return this.deFiBrokerController.getMessageStore().isOSPageCacheBusy();
    }

    private RemotingCommand processReplyMessageRequest(final ChannelHandlerContext ctx, //
        final RemotingCommand request, //
        final SendMessageContext sendMessageContext, //
        final SendMessageRequestHeader requestHeader) throws RemotingCommandException {
        long arriveBrokerTime = System.currentTimeMillis();
        final RemotingCommand response = RemotingCommand.createResponseCommand(SendMessageResponseHeader.class);
        final SendMessageResponseHeader responseHeader = (SendMessageResponseHeader) response.readCustomHeader();

        response.setOpaque(request.getOpaque());

        response.addExtField(MessageConst.PROPERTY_MSG_REGION, this.deFiBrokerController.getBrokerConfig().getRegionId());

        log.debug("receive SendDirectMessage request command, " + request);

        final long startTimestamp = this.deFiBrokerController.getBrokerConfig().getStartAcceptSendRequestTimeStamp();
        if (this.deFiBrokerController.getMessageStore().now() < startTimestamp) {
            response.setCode(ResponseCode.SYSTEM_ERROR);
            response.setRemark(String.format("broker unable to service, until %s", UtilAll.timeMillisToHumanString2(startTimestamp)));
            return response;
        }

        response.setCode(-1);
        super.msgCheck(ctx, requestHeader, response);
        if (response.getCode() != -1) {
            return response;
        }

        final byte[] body = request.getBody();

        int queueIdInt = requestHeader.getQueueId();
        TopicConfig topicConfig = this.deFiBrokerController.getTopicConfigManager().selectTopicConfig(requestHeader.getTopic());

        if (queueIdInt < 0) {
            queueIdInt = Math.abs(this.random.nextInt() % 99999999) % topicConfig.getWriteQueueNums();
        }

        int sysFlag = requestHeader.getSysFlag();

        if (TopicFilterType.MULTI_TAG == topicConfig.getTopicFilterType()) {
            sysFlag |= MessageSysFlag.MULTI_TAGS_FLAG;
        }

        String newTopic = requestHeader.getTopic();
        if ((null != newTopic && newTopic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX))) {

            String groupName = newTopic.substring(MixAll.RETRY_GROUP_TOPIC_PREFIX.length());

            SubscriptionGroupConfig subscriptionGroupConfig =
                this.deFiBrokerController.getSubscriptionGroupManager().findSubscriptionGroupConfig(groupName);
            if (null == subscriptionGroupConfig) {
                response.setCode(ResponseCode.SUBSCRIPTION_GROUP_NOT_EXIST);
                response.setRemark(
                    "subscription group not exist, " + groupName + " " + FAQUrl.suggestTodo(FAQUrl.SUBSCRIPTION_GROUP_NOT_EXIST));
                return response;
            }

            int maxReconsumeTimes = subscriptionGroupConfig.getRetryMaxTimes();
            if (request.getVersion() >= MQVersion.Version.V3_4_9.ordinal()) {
                maxReconsumeTimes = requestHeader.getMaxReconsumeTimes();
            }
            int reconsumeTimes = requestHeader.getReconsumeTimes();
            if (reconsumeTimes >= maxReconsumeTimes) {
                newTopic = MixAll.getDLQTopic(groupName);
                queueIdInt = Math.abs(this.random.nextInt() % 99999999) % DLQ_NUMS_PER_GROUP;
                topicConfig = this.deFiBrokerController.getTopicConfigManager().createTopicInSendMessageBackMethod(newTopic, //
                    DLQ_NUMS_PER_GROUP, //
                    PermName.PERM_WRITE, 0
                );
                if (null == topicConfig) {
                    response.setCode(ResponseCode.SYSTEM_ERROR);
                    response.setRemark("topic[" + newTopic + "] not exist");
                    return response;
                }
            }
        }
        MessageExtBrokerInner msgInner = new MessageExtBrokerInner();
        msgInner.setTopic(newTopic);
        msgInner.setBody(body);
        msgInner.setFlag(requestHeader.getFlag());
        MessageAccessor.setProperties(msgInner, MessageDecoder.string2messageProperties(requestHeader.getProperties()));
        msgInner.setPropertiesString(requestHeader.getProperties());
        msgInner.setTagsCode(MessageExtBrokerInner.tagsString2tagsCode(topicConfig.getTopicFilterType(), msgInner.getTags()));

        msgInner.setQueueId(queueIdInt);
        msgInner.setSysFlag(sysFlag);
        msgInner.setBornTimestamp(requestHeader.getBornTimestamp());
        msgInner.setBornHost(ctx.channel().remoteAddress());
        msgInner.setStoreHost(this.getStoreHost());
        msgInner.setReconsumeTimes(requestHeader.getReconsumeTimes() == null ? 0 : requestHeader.getReconsumeTimes());

        if (this.deFiBrokerController.getBrokerConfig().isRejectTransactionMessage()) {
            String traFlag = msgInner.getProperty(MessageConst.PROPERTY_TRANSACTION_PREPARED);
            if (traFlag != null) {
                response.setCode(ResponseCode.NO_PERMISSION);
                response.setRemark(
                    "the broker[" + this.deFiBrokerController.getBrokerConfig().getBrokerIP1() + "] sending transaction constant is forbidden");
                return response;
            }
        }

        ReplyMessageRequestHeader replyMessageRequestHeader = new ReplyMessageRequestHeader();
        replyMessageRequestHeader.setBornHost(ctx.channel().remoteAddress().toString());
        replyMessageRequestHeader.setStoreHost(this.getStoreHost().toString());
        replyMessageRequestHeader.setStoreTimestamp(arriveBrokerTime);

        replyMessageRequestHeader.setProducerGroup(requestHeader.getProducerGroup());
        replyMessageRequestHeader.setTopic(requestHeader.getTopic());
        replyMessageRequestHeader.setDefaultTopic(requestHeader.getDefaultTopic());
        replyMessageRequestHeader.setDefaultTopicQueueNums(requestHeader.getDefaultTopicQueueNums());
        replyMessageRequestHeader.setQueueId(requestHeader.getQueueId());
        replyMessageRequestHeader.setSysFlag(requestHeader.getSysFlag());
        replyMessageRequestHeader.setBornTimestamp(requestHeader.getBornTimestamp());
        replyMessageRequestHeader.setFlag(requestHeader.getFlag());
        replyMessageRequestHeader.setProperties(requestHeader.getProperties());
        replyMessageRequestHeader.setReconsumeTimes(requestHeader.getReconsumeTimes());
        replyMessageRequestHeader.setUnitMode(requestHeader.isUnitMode());

        if (msgInner.getProperties() != null && DeFiBusConstant.REPLY.equals(msgInner.getProperties().get(DeFiBusConstant.KEY))) {
            String senderId = msgInner.getProperties().get(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO);
            if (senderId == null) {
                log.warn("senderId is null, can not reply message");
            } else {
                ClientChannelInfo clientChannelInfo = this.deFiBrokerController.getProducerManager().getClientChannel(senderId);
                if (clientChannelInfo == null || clientChannelInfo.getChannel() == null || !clientChannelInfo.getChannel().isActive()) {

                    if (System.currentTimeMillis() - replyMessageRequestHeader.getBornTimestamp() <= 1000) {
                        log.warn("try to push rr reply message:{} later for clientId:{}", msgInner, senderId);
                        processRequestLater(ctx, request);
                        return null;
                    } else {
                        log.warn("ignore rr reply message:{} after retry, no channel for this client:{}", msgInner, senderId);
                    }

                } else {
                    Map<String, String> map = MessageDecoder.string2messageProperties(replyMessageRequestHeader.getProperties());
                    map.put(DeFiBusMessageConst.LEAVE_TIME, String.valueOf(System.currentTimeMillis()));
                    replyMessageRequestHeader.setProperties(MessageDecoder.messageProperties2String(map));

                    try {
                        this.deFiBrokerController.getPushReplyMessageExecutor().submit(new Runnable() {
                            @Override public void run() {
                                boolean isPushSuccess = deFiBrokerController.getDeFiBusBroker2Client().pushRRReplyMessageToClient
                                    (clientChannelInfo.getChannel(), replyMessageRequestHeader, msgInner);
                                if (isPushSuccess) {
                                    deFiBrokerController.getBrokerStatsManager().incBrokerGetNums(1);
                                    if (deFiBrokerController.getMessageStore() instanceof DeFiPluginMessageStore) {
                                        DefaultMessageStore defaultMessageStore = (DefaultMessageStore)
                                            ((DeFiPluginMessageStore) deFiBrokerController.getMessageStore()).getDefaultMessageStore();
                                        defaultMessageStore.getStoreStatsService().getGetMessageTransferedMsgCount().incrementAndGet();
                                        defaultMessageStore.getStoreStatsService().getSinglePutMessageTopicTimesTotal(requestHeader.getTopic()).incrementAndGet();
                                    }
                                } else {
                                    log.warn("push reply msg to client failed. [{}]", msgInner);
                                }
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        log.warn("too many push rr rely requests, and system thread pool busy");
                    }
                }
            }
        }

        PutMessageResult putMessageResult = this.deFiBrokerController.getMessageStore().putMessage(msgInner);

        if (putMessageResult != null) {
            boolean sendOK = true;
            response.setCode(ResponseCode.SUCCESS);

            switch (putMessageResult.getPutMessageStatus()) {
                case PUT_OK:
                    sendOK = true;
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case FLUSH_DISK_TIMEOUT:
                    response.setCode(ResponseCode.FLUSH_DISK_TIMEOUT);
                    sendOK = true;
                    break;
                case FLUSH_SLAVE_TIMEOUT:
                    response.setCode(ResponseCode.FLUSH_SLAVE_TIMEOUT);
                    sendOK = true;
                    break;
                case SLAVE_NOT_AVAILABLE:
                    response.setCode(ResponseCode.SLAVE_NOT_AVAILABLE);
                    sendOK = true;
                    break;

                case CREATE_MAPEDFILE_FAILED:
                    response.setCode(ResponseCode.SYSTEM_ERROR);
                    response.setRemark("create mapped file failed, server is busy or broken.");
                    break;
                case MESSAGE_ILLEGAL:
                case PROPERTIES_SIZE_EXCEEDED:
                    response.setCode(ResponseCode.MESSAGE_ILLEGAL);
                    response.setRemark(
                        "the message is illegal, maybe msg body or properties length not matched. msg body length limit 128k, msg properties length limit 32k.");
                    break;
                case SERVICE_NOT_AVAILABLE:
                    response.setCode(ResponseCode.SERVICE_NOT_AVAILABLE);
                    response.setRemark(
                        "service not available now, maybe disk full, " + diskUtil() + ", maybe your broker machine memory too small.");
                    break;
                case OS_PAGECACHE_BUSY:
                    response.setCode(ResponseCode.SYSTEM_ERROR);
                    response.setRemark("[PC_SYNCHRONIZED]broker busy, start flow control for a while");
                    break;
                case UNKNOWN_ERROR:
                    response.setCode(ResponseCode.SYSTEM_ERROR);
                    response.setRemark("UNKNOWN_ERROR");
                    break;

                default:
                    response.setCode(ResponseCode.SYSTEM_ERROR);
                    response.setRemark("UNKNOWN_ERROR DEFAULT");
                    break;
            }

            String owner = request.getExtFields().get(BrokerStatsManager.COMMERCIAL_OWNER);
            if (sendOK) {

                this.deFiBrokerController.getBrokerStatsManager().incTopicPutNums(msgInner.getTopic());
                this.deFiBrokerController.getBrokerStatsManager().incTopicPutSize(msgInner.getTopic(),
                    putMessageResult.getAppendMessageResult().getWroteBytes());
                this.deFiBrokerController.getBrokerStatsManager().incBrokerPutNums();

                response.setRemark(null);

                responseHeader.setMsgId(putMessageResult.getAppendMessageResult().getMsgId());
                responseHeader.setQueueId(queueIdInt);
                responseHeader.setQueueOffset(putMessageResult.getAppendMessageResult().getLogicsOffset());

                doResponse(ctx, request, response);

                if (hasSendMessageHook()) {
                    sendMessageContext.setMsgId(responseHeader.getMsgId());
                    sendMessageContext.setQueueId(responseHeader.getQueueId());
                    sendMessageContext.setQueueOffset(responseHeader.getQueueOffset());

                    int commercialBaseCount = deFiBrokerController.getBrokerConfig().getCommercialBaseCount();
                    int wroteSize = putMessageResult.getAppendMessageResult().getWroteBytes();
                    int incValue = (int) Math.ceil(wroteSize / BrokerStatsManager.SIZE_PER_COUNT) * commercialBaseCount;

                    sendMessageContext.setCommercialSendStats(BrokerStatsManager.StatsType.SEND_SUCCESS);
                    sendMessageContext.setCommercialSendTimes(incValue);
                    sendMessageContext.setCommercialSendSize(wroteSize);
                    sendMessageContext.setCommercialOwner(owner);
                }
                return null;
            } else {
                if (hasSendMessageHook()) {
                    int wroteSize = request.getBody().length;
                    int incValue = (int) Math.ceil(wroteSize / BrokerStatsManager.SIZE_PER_COUNT);

                    sendMessageContext.setCommercialSendStats(BrokerStatsManager.StatsType.SEND_FAILURE);
                    sendMessageContext.setCommercialSendTimes(incValue);
                    sendMessageContext.setCommercialSendSize(wroteSize);
                    sendMessageContext.setCommercialOwner(owner);
                }
            }
        } else {
            response.setCode(ResponseCode.SYSTEM_ERROR);
            response.setRemark("store putMessage return null");
        }

        return response;
    }

    public SocketAddress getStoreHost() {
        return storeHost;
    }

    private String diskUtil() {
        String storePathPhysic = this.deFiBrokerController.getMessageStoreConfig().getStorePathCommitLog();
        double physicRatio = UtilAll.getDiskPartitionSpaceUsedPercent(storePathPhysic);

        String storePathLogis =
            StorePathConfigHelper.getStorePathConsumeQueue(this.deFiBrokerController.getMessageStoreConfig().getStorePathRootDir());
        double logisRatio = UtilAll.getDiskPartitionSpaceUsedPercent(storePathLogis);

        String storePathIndex =
            StorePathConfigHelper.getStorePathIndex(this.deFiBrokerController.getMessageStoreConfig().getStorePathRootDir());
        double indexRatio = UtilAll.getDiskPartitionSpaceUsedPercent(storePathIndex);

        return String.format("CL: %5.2f CQ: %5.2f INDEX: %5.2f", physicRatio, logisRatio, indexRatio);
    }

    public void processRequestLater(ChannelHandlerContext ctx, RemotingCommand request) {
        ScheduledThreadPoolExecutor threadPoolExecutor = this.deFiBrokerController.getSendReplyScheduledExecutorService();
        if (threadPoolExecutor.getQueue().size() > this.deFiBrokerController.getDeFiBusBrokerConfig().getSendReplyThreadPoolQueueCapacity()) {
            log.warn("Task rejected from ScheduledThreadPoolExecutor when try to push rr reply message again");
            return;
        }

        //submit to threadpool 2 times to push the request per 100 ms
        this.deFiBrokerController.getSendReplyScheduledExecutorService().schedule(new Runnable() {
            @Override
            public void run() {
                deFiBrokerController.getSendReplyMessageExecutor().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DeFiReplyMessageProcessor.this.processRequest(ctx, request);
                        } catch (Throwable e) {
                            log.warn("failed to processRequest", e);
                        }
                    }
                });
            }
        }, 100, TimeUnit.MILLISECONDS);

    }

}
