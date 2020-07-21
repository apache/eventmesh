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

package cn.webank.defibus.client.impl;

import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.protocol.DeFiBusRequestCode;
import cn.webank.defibus.common.protocol.body.GetConsumerListByGroupAndTopicResponseBody;
import cn.webank.defibus.common.protocol.header.GetConsumerListByGroupAndTopicRequestHeader;
import cn.webank.defibus.common.util.ReflectUtil;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.impl.ClientRemotingProcessor;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.impl.producer.TopicPublishInfo;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.namesrv.TopAddressing;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeaderV2;
import org.apache.rocketmq.common.protocol.header.SendMessageResponseHeader;
import org.apache.rocketmq.remoting.InvokeCallback;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.exception.RemotingTooMuchRequestException;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.ResponseFuture;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiBusClientAPIImpl extends MQClientAPIImpl {
    public static final Logger LOGGER = LoggerFactory.getLogger(DeFiBusClientAPIImpl.class);
    private static boolean sendSmartMsg =
        Boolean.parseBoolean(System.getProperty("org.apache.rocketmq.client.sendSmartMsg", "true"));
    private TopAddressing topAddressing;

    public DeFiBusClientAPIImpl(NettyClientConfig nettyClientConfig,
        ClientRemotingProcessor clientRemotingProcessor,
        RPCHook rpcHook,
        ClientConfig clientConfig) {

        super(nettyClientConfig, clientRemotingProcessor, rpcHook, clientConfig);

    }

    public void setWsAddr(String wsAddr) {
        try {
            topAddressing = (TopAddressing) ReflectUtil.getSimpleProperty(MQClientAPIImpl.class, this, "topAddressing");
            ReflectUtil.setSimpleProperty(TopAddressing.class, topAddressing, "wsAddr", wsAddr);
            LOGGER.debug("activate configure center, address: " + wsAddr);
        } catch (Exception e) {
            LOGGER.warn("Error when set ws address. configure center may not work.", e);
        }
    }

    @Override
    public SendResult sendMessage(//
        final String addr, // 1
        final String brokerName, // 2
        final Message msg, // 3
        final SendMessageRequestHeader requestHeader, // 4
        final long timeoutMillis, // 5
        final CommunicationMode communicationMode, // 6
        final SendCallback sendCallback, // 7
        final TopicPublishInfo topicPublishInfo, // 8
        final MQClientInstance instance, // 9
        final int retryTimesWhenSendFailed, // 10
        final SendMessageContext context, // 11
        final DefaultMQProducerImpl producer // 12
    ) throws RemotingException, MQBrokerException, InterruptedException {
        RemotingCommand request = null;
        if (DeFiBusConstant.REPLY.equals(msg.getProperties().get(DeFiBusConstant.KEY)) ||
            DeFiBusConstant.DIRECT.equals(msg.getProperties().get(DeFiBusConstant.KEY))) {
            if (sendSmartMsg) {
                SendMessageRequestHeaderV2 requestHeaderV2 = SendMessageRequestHeaderV2.createSendMessageRequestHeaderV2(requestHeader);
                request = RemotingCommand.createRequestCommand(DeFiBusRequestCode.SEND_DIRECT_MESSAGE_V2, requestHeaderV2);
            } else {
                request = RemotingCommand.createRequestCommand(DeFiBusRequestCode.SEND_DIRECT_MESSAGE, requestHeader);
            }
            request.setBody(msg.getBody());
            final AtomicInteger times = new AtomicInteger();
            this.sendMessageAsync(addr, brokerName, msg, timeoutMillis, request, sendCallback, topicPublishInfo, instance,
                retryTimesWhenSendFailed, times, context, producer);
            return null;

        } else {
            return super.sendMessage(addr, brokerName, msg, requestHeader, timeoutMillis, communicationMode, sendCallback, topicPublishInfo, instance, retryTimesWhenSendFailed, context, producer);
        }
    }

    private void sendMessageAsync(//
        final String addr, //
        final String brokerName, //
        final Message msg, //
        final long timeoutMillis, //
        final RemotingCommand request, //
        final SendCallback sendCallback, //
        final TopicPublishInfo topicPublishInfo, //
        final MQClientInstance instance, //
        final int retryTimesWhenSendFailed, //
        final AtomicInteger times, //
        final SendMessageContext context, //
        final DefaultMQProducerImpl producer //
    ) throws InterruptedException, RemotingException {
        super.getRemotingClient().invokeAsync(addr, request, timeoutMillis, new InvokeCallback() {
            @Override
            public void operationComplete(ResponseFuture responseFuture) {
                RemotingCommand response = responseFuture.getResponseCommand();
                if (null == sendCallback && response != null) {

                    try {
                        SendResult sendResult = processSendResponse(brokerName, msg, response);
                        if (context != null && sendResult != null) {
                            context.setSendResult(sendResult);
                            context.getProducer().executeSendMessageHookAfter(context);
                        }
                    } catch (Throwable e) {
                        //
                    }

                    producer.updateFaultItem(brokerName, System.currentTimeMillis() - responseFuture.getBeginTimestamp(), false);
                    return;
                }

                if (response != null) {
                    try {
                        SendResult sendResult = processSendResponse(brokerName, msg, response);
                        assert sendResult != null;
                        if (context != null) {
                            context.setSendResult(sendResult);
                            context.getProducer().executeSendMessageHookAfter(context);
                        }

                        try {
                            sendCallback.onSuccess(sendResult);
                        } catch (Throwable e) {
                        }

                        producer.updateFaultItem(brokerName, System.currentTimeMillis() - responseFuture.getBeginTimestamp(), false);
                    } catch (Exception e) {
                        producer.updateFaultItem(brokerName, System.currentTimeMillis() - responseFuture.getBeginTimestamp(), true);
                        onExceptionImpl(brokerName, msg, 0L, request, sendCallback, topicPublishInfo, instance,
                            retryTimesWhenSendFailed, times, e, context, false, producer);
                    }
                } else {
                    producer.updateFaultItem(brokerName, System.currentTimeMillis() - responseFuture.getBeginTimestamp(), true);
                    if (!responseFuture.isSendRequestOK()) {
                        MQClientException ex = new MQClientException("send request failed", responseFuture.getCause());
                        onExceptionImpl(brokerName, msg, 0L, request, sendCallback, topicPublishInfo, instance,
                            retryTimesWhenSendFailed, times, ex, context, true, producer);
                    } else if (responseFuture.isTimeout()) {
                        MQClientException ex = new MQClientException("wait response timeout " + responseFuture.getTimeoutMillis() + "ms",
                            responseFuture.getCause());
                        onExceptionImpl(brokerName, msg, 0L, request, sendCallback, topicPublishInfo, instance,
                            retryTimesWhenSendFailed, times, ex, context, true, producer);
                    } else {
                        MQClientException ex = new MQClientException("unknow reseaon", responseFuture.getCause());
                        onExceptionImpl(brokerName, msg, 0L, request, sendCallback, topicPublishInfo, instance,
                            retryTimesWhenSendFailed, times, ex, context, true, producer);
                    }
                }
            }
        });
    }

    private void onExceptionImpl(final String brokerName, //
        final Message msg, //
        final long timeoutMillis, //
        final RemotingCommand request, //
        final SendCallback sendCallback, //
        final TopicPublishInfo topicPublishInfo, //
        final MQClientInstance instance, //
        final int timesTotal, //
        final AtomicInteger curTimes, //
        final Exception e, //
        final SendMessageContext context, //
        final boolean needRetry, //
        final DefaultMQProducerImpl producer // 12
    ) {
        int tmp = curTimes.incrementAndGet();
        if (needRetry && tmp <= timesTotal) {
            String retryBrokerName = brokerName;//by default, it will send to the same broker
            if (topicPublishInfo != null) { //select one message queue accordingly, in order to determine which broker to send
                MessageQueue mqChosen = producer.selectOneMessageQueue(topicPublishInfo, brokerName);
                retryBrokerName = mqChosen.getBrokerName();
            }
            String addr = instance.findBrokerAddressInPublish(retryBrokerName);
            LOGGER.info("async send msg by retry {} times. topic={}, brokerAddr={}, brokerName={}", tmp, msg.getTopic(), addr,
                retryBrokerName);
            try {
                request.setOpaque(RemotingCommand.createNewRequestId());
                sendMessageAsync(addr, retryBrokerName, msg, timeoutMillis, request, sendCallback, topicPublishInfo, instance,
                    timesTotal, curTimes, context, producer);
            } catch (InterruptedException e1) {
                onExceptionImpl(retryBrokerName, msg, timeoutMillis, request, sendCallback, topicPublishInfo, instance, timesTotal, curTimes, e1,
                    context, false, producer);
            } catch (RemotingConnectException e1) {
                producer.updateFaultItem(brokerName, 3000, true);
                onExceptionImpl(retryBrokerName, msg, timeoutMillis, request, sendCallback, topicPublishInfo, instance, timesTotal, curTimes, e1,
                    context, true, producer);
            } catch (RemotingTooMuchRequestException e1) {
                onExceptionImpl(retryBrokerName, msg, timeoutMillis, request, sendCallback, topicPublishInfo, instance, timesTotal, curTimes, e1,
                    context, false, producer);
            } catch (RemotingException e1) {
                producer.updateFaultItem(brokerName, 3000, true);
                onExceptionImpl(retryBrokerName, msg, timeoutMillis, request, sendCallback, topicPublishInfo, instance, timesTotal, curTimes, e1,
                    context, true, producer);
            }
        } else {

            if (context != null) {
                context.setException(e);
                context.getProducer().executeSendMessageHookAfter(context);
            }

            try {
                sendCallback.onException(e);
            } catch (Exception ignored) {
            }
        }
    }

    private SendResult processSendResponse(//
        final String brokerName, //
        final Message msg, //
        final RemotingCommand response//
    ) throws MQBrokerException, RemotingCommandException {
        switch (response.getCode()) {
            case ResponseCode.FLUSH_DISK_TIMEOUT:
                LOGGER.warn("publish success, but flush disk timeout " + response.getRemark());
            case ResponseCode.FLUSH_SLAVE_TIMEOUT:
                LOGGER.warn("publish success, but flush slave timeout " + response.getRemark());
            case ResponseCode.SLAVE_NOT_AVAILABLE: {
                LOGGER.warn("publish success, but slave is not available " + response.getRemark());
            }
            case ResponseCode.SUCCESS: {
                SendStatus sendStatus = SendStatus.SEND_OK;
                switch (response.getCode()) {
                    case ResponseCode.FLUSH_DISK_TIMEOUT:
                        sendStatus = SendStatus.FLUSH_DISK_TIMEOUT;
                        break;
                    case ResponseCode.FLUSH_SLAVE_TIMEOUT:
                        sendStatus = SendStatus.FLUSH_SLAVE_TIMEOUT;
                        break;
                    case ResponseCode.SLAVE_NOT_AVAILABLE:
                        sendStatus = SendStatus.SLAVE_NOT_AVAILABLE;
                        break;
                    case ResponseCode.SUCCESS:
                        sendStatus = SendStatus.SEND_OK;
                        break;
                    default:
                        assert false;
                        break;
                }

                SendMessageResponseHeader responseHeader =
                    (SendMessageResponseHeader) response.decodeCommandCustomHeader(SendMessageResponseHeader.class);

                MessageQueue messageQueue = new MessageQueue(msg.getTopic(), brokerName, responseHeader.getQueueId());

                SendResult sendResult = new SendResult(sendStatus,
                    MessageClientIDSetter.getUniqID(msg),
                    responseHeader.getMsgId(), messageQueue, responseHeader.getQueueOffset());
                sendResult.setTransactionId(responseHeader.getTransactionId());
                String regionId = response.getExtFields().get(MessageConst.PROPERTY_MSG_REGION);
                if (regionId == null || regionId.isEmpty()) {
                    regionId = "DefaultRegion";
                }
                sendResult.setRegionId(regionId);
                return sendResult;
            }
            default:
                break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }

    public List<String> getConsumerIdListByGroupAndTopic(
        final String addr,
        final String consumerGroup,
        final String topic,
        final long timeoutMillis) throws RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException,
        MQBrokerException, InterruptedException {
        GetConsumerListByGroupAndTopicRequestHeader requestHeader = new GetConsumerListByGroupAndTopicRequestHeader();
        requestHeader.setConsumerGroup(consumerGroup);
        requestHeader.setTopic(topic);
        RemotingCommand request = RemotingCommand.createRequestCommand(DeFiBusRequestCode.GET_CONSUMER_LIST_BY_GROUP_AND_TOPIC, requestHeader);

        RemotingCommand response = this.getRemotingClient().invokeSync(MixAll.brokerVIPChannel(false, addr),
            request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
            case ResponseCode.SUCCESS: {
                if (response.getBody() != null) {
                    GetConsumerListByGroupAndTopicResponseBody body =
                        GetConsumerListByGroupAndTopicResponseBody.decode(response.getBody(), GetConsumerListByGroupAndTopicResponseBody.class);
                    return body.getConsumerIdList();
                }
            }
            default:
                break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }

}
