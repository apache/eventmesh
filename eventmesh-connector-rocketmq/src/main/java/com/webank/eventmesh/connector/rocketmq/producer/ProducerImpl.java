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
package com.webank.eventmesh.connector.rocketmq.producer;

import com.webank.eventmesh.connector.rocketmq.promise.DefaultPromise;
import com.webank.eventmesh.connector.rocketmq.utils.OMSUtil;
import com.webank.eventmesh.api.SendCallback;
import io.openmessaging.*;
import io.openmessaging.exception.OMSRuntimeException;
import io.openmessaging.interceptor.ProducerInterceptor;
import io.openmessaging.producer.BatchMessageSender;
import io.openmessaging.producer.LocalTransactionExecutor;
import io.openmessaging.producer.Producer;
import io.openmessaging.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;

import static com.webank.eventmesh.connector.rocketmq.utils.OMSUtil.msgConvert;


public class ProducerImpl extends AbstractOMSProducer implements Producer {

    public static final int proxyServerAsyncAccumulationThreshold = 1000;

    public ProducerImpl(final KeyValue properties) {
        super(properties);
    }

    @Override
    public KeyValue attributes() {
        return properties;
    }

    @Override
    public SendResult send(final Message message) {
        return send(message, this.rocketmqProducer.getSendMsgTimeout());
    }

    @Override
    public SendResult send(final Message message, final KeyValue properties) {
        long timeout = properties.containsKey(Message.BuiltinKeys.TIMEOUT)
            ? properties.getInt(Message.BuiltinKeys.TIMEOUT) : this.rocketmqProducer.getSendMsgTimeout();
        return send(message, timeout);
    }

    @Override
    public SendResult send(Message message, LocalTransactionExecutor branchExecutor, KeyValue attributes) {
        return null;
    }

    @Override
    public Future<SendResult> sendAsync(Message message) {
        return null;
    }

    @Override
    public Future<SendResult> sendAsync(Message message, KeyValue attributes) {
        return null;
    }

    private SendResult send(final Message message, long timeout) {
        checkMessageType(message);
        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
        try {
            org.apache.rocketmq.client.producer.SendResult rmqResult = this.rocketmqProducer.send(rmqMessage, timeout);
            if (!rmqResult.getSendStatus().equals(SendStatus.SEND_OK)) {
                log.error(String.format("Send message to RocketMQ failed, %s", message));
                throw new OMSRuntimeException("-1", "Send message to RocketMQ broker failed.");
            }
            message.sysHeaders().put(Message.BuiltinKeys.MESSAGE_ID, rmqResult.getMsgId());
            return OMSUtil.sendResultConvert(rmqResult);
        } catch (Exception e) {
            log.error(String.format("Send message to RocketMQ failed, %s", message), e);
            throw checkProducerException(rmqMessage.getTopic(), message.sysHeaders().getString(Message.BuiltinKeys.MESSAGE_ID), e);
        }
    }

    public Promise<SendResult> sendAsync(final Message message, SendCallback sendCallback) {
        return sendAsync(message, this.rocketmqProducer.getSendMsgTimeout(), sendCallback);
    }

    private Promise<SendResult> sendAsync(final Message message, long timeout, SendCallback sendCallback) {
        checkMessageType(message);
        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
        final Promise<SendResult> promise = new DefaultPromise<>();
        try {
            this.rocketmqProducer.send(rmqMessage, new org.apache.rocketmq.client.producer.SendCallback() {
                @Override
                public void onSuccess(final org.apache.rocketmq.client.producer.SendResult rmqResult) {
                    message.sysHeaders().put(Message.BuiltinKeys.MESSAGE_ID, rmqResult.getMsgId());
                    SendResult omsSendResult = OMSUtil.sendResultConvert(rmqResult);
                    promise.set(omsSendResult);
                    sendCallback.onSuccess(omsSendResult);
                }

                @Override
                public void onException(final Throwable e) {
                    promise.setFailure(e);
                    sendCallback.onException(e);
                }
            }, timeout);
        } catch (Exception e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public void sendOneway(final Message message) {
        checkMessageType(message);
        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
        try {
            this.rocketmqProducer.sendOneway(rmqMessage);
        } catch (Exception ignore) { //Ignore the oneway exception.
        }
    }

    @Override
    public void sendOneway(final Message message, final KeyValue properties) {
        sendOneway(message);
    }

    @Override
    public BatchMessageSender createBatchMessageSender() {
        return null;
    }

    @Override
    public void addInterceptor(ProducerInterceptor interceptor) {

    }

    @Override
    public void removeInterceptor(ProducerInterceptor interceptor) {

    }

    public void setExtFields(){
        super.getRocketmqProducer().setRetryTimesWhenSendFailed(0);
        super.getRocketmqProducer().setRetryTimesWhenSendAsyncFailed(0);
        super.getRocketmqProducer().setPollNameServerInterval(60000);

        super.getRocketmqProducer().getDefaultMQProducerImpl().getmQClientFactory()
                .getNettyClientConfig().setClientAsyncSemaphoreValue(proxyServerAsyncAccumulationThreshold);
        super.getRocketmqProducer().setCompressMsgBodyOverHowmuch(10);
    }

}
