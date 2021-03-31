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

import com.webank.eventmesh.connector.rocketmq.utils.OMSUtil;
import io.openmessaging.api.*;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.rocketmq.common.message.MessageClientIDSetter;

import java.util.Properties;
import java.util.concurrent.ExecutorService;



public class ProducerImpl extends AbstractOMSProducer implements Producer {

    public static final int proxyServerAsyncAccumulationThreshold = 1000;

    public ProducerImpl(final Properties properties) {
        super(properties);
    }

    public Properties attributes() {
        return properties;
    }

//    @Override
//    public SendResult send(final Message message) {
//        return send(message, this.rocketmqProducer.getSendMsgTimeout());
//    }

//    @Override
//    public SendResult send(final Message message, final KeyValue properties) {
//        long timeout = properties.containsKey(Message.BuiltinKeys.TIMEOUT)
//            ? properties.getInt(Message.BuiltinKeys.TIMEOUT) : this.rocketmqProducer.getSendMsgTimeout();
//        return send(message, timeout);
//    }

//    @Override
//    public SendResult send(Message message, LocalTransactionExecutor branchExecutor, KeyValue attributes) {
//        return null;
//    }

//    @Override
//    public Future<SendResult> sendAsync(Message message) {
//        return null;
//    }
//
//    @Override
//    public Future<SendResult> sendAsync(Message message, KeyValue attributes) {
//        return null;
//    }
//
//    private SendResult send(final Message message, long timeout) {
//        checkMessageType(message);
//        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
//        try {
//            org.apache.rocketmq.client.producer.SendResult rmqResult = this.rocketmqProducer.send(rmqMessage, timeout);
//            if (!rmqResult.getSendStatus().equals(SendStatus.SEND_OK)) {
//                log.error(String.format("Send message to RocketMQ failed, %s", message));
//                throw new OMSRuntimeException("-1", "Send message to RocketMQ broker failed.");
//            }
//            message.sysHeaders().put(Message.BuiltinKeys.MESSAGE_ID, rmqResult.getMsgId());
//            return OMSUtil.sendResultConvert(rmqResult);
//        } catch (Exception e) {
//            log.error(String.format("Send message to RocketMQ failed, %s", message), e);
//            throw checkProducerException(rmqMessage.getTopic(), message.sysHeaders().getString(Message.BuiltinKeys.MESSAGE_ID), e);
//        }
//    }
//
//    public Promise<SendResult> sendAsync(final Message message, SendCallback sendCallback) {
//        return sendAsync(message, this.rocketmqProducer.getSendMsgTimeout(), sendCallback);
//    }
//
//    private Promise<SendResult> sendAsync(final Message message, long timeout, SendCallback sendCallback) {
//        checkMessageType(message);
//        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
//        final Promise<SendResult> promise = new DefaultPromise<>();
//        try {
//            this.rocketmqProducer.send(rmqMessage, new org.apache.rocketmq.client.producer.SendCallback() {
//                @Override
//                public void onSuccess(final org.apache.rocketmq.client.producer.SendResult rmqResult) {
//                    message.sysHeaders().put(Message.BuiltinKeys.MESSAGE_ID, rmqResult.getMsgId());
//                    SendResult omsSendResult = OMSUtil.sendResultConvert(rmqResult);
//                    promise.set(omsSendResult);
//                    sendCallback.onSuccess(omsSendResult);
//                }
//
//                @Override
//                public void onException(final Throwable e) {
//                    promise.setFailure(e);
//                    sendCallback.onException(e);
//                }
//            }, timeout);
//        } catch (Exception e) {
//            promise.setFailure(e);
//        }
//        return promise;
//    }
//
//    @Override
//    public void sendOneway(final Message message) {
//        checkMessageType(message);
//        org.apache.rocketmq.common.message.Message rmqMessage = msgConvert((BytesMessage) message);
//        try {
//            this.rocketmqProducer.sendOneway(rmqMessage);
//        } catch (Exception ignore) { //Ignore the oneway exception.
//        }
//    }



//    @Override
//    public void sendOneway(final Message message, final KeyValue properties) {
//        sendOneway(message);
//    }
//
//    @Override
//    public BatchMessageSender createBatchMessageSender() {
//        return null;
//    }
//
//    @Override
//    public void addInterceptor(ProducerInterceptor interceptor) {
//
//    }
//
//    @Override
//    public void removeInterceptor(ProducerInterceptor interceptor) {
//
//    }

    public void setExtFields(){
        super.getRocketmqProducer().setRetryTimesWhenSendFailed(0);
        super.getRocketmqProducer().setRetryTimesWhenSendAsyncFailed(0);
        super.getRocketmqProducer().setPollNameServerInterval(60000);

        super.getRocketmqProducer().getDefaultMQProducerImpl().getmQClientFactory()
                .getNettyClientConfig().setClientAsyncSemaphoreValue(proxyServerAsyncAccumulationThreshold);
        super.getRocketmqProducer().setCompressMsgBodyOverHowmuch(10);
    }

    @Override
    public SendResult send(Message message) {
        this.checkProducerServiceState(rocketmqProducer.getDefaultMQProducerImpl());
        org.apache.rocketmq.common.message.Message msgRMQ = OMSUtil.msgConvert(message);

        try {
            org.apache.rocketmq.client.producer.SendResult sendResultRMQ = this.rocketmqProducer.send(msgRMQ);
            message.setMsgID(sendResultRMQ.getMsgId());
            SendResult sendResult = new SendResult();
            sendResult.setTopic(sendResultRMQ.getMessageQueue().getTopic());
            sendResult.setMessageId(sendResultRMQ.getMsgId());
            return sendResult;
        } catch (Exception e) {
            log.error(String.format("Send message Exception, %s", message), e);
            throw this.checkProducerException(message.getTopic(), message.getMsgID(), e);
        }
    }

    @Override
    public void sendOneway(Message message) {
        this.checkProducerServiceState(this.rocketmqProducer.getDefaultMQProducerImpl());
        org.apache.rocketmq.common.message.Message msgRMQ = OMSUtil.msgConvert(message);

        try {
            this.rocketmqProducer.sendOneway(msgRMQ);
            message.setMsgID(MessageClientIDSetter.getUniqID(msgRMQ));
        } catch (Exception e) {
            log.error(String.format("Send message oneway Exception, %s", message), e);
            throw this.checkProducerException(message.getTopic(), message.getMsgID(), e);
        }
    }

    @Override
    public void sendAsync(Message message, SendCallback sendCallback) {
        this.checkProducerServiceState(this.rocketmqProducer.getDefaultMQProducerImpl());
        org.apache.rocketmq.common.message.Message msgRMQ = OMSUtil.msgConvert(message);

        try {
            this.rocketmqProducer.send(msgRMQ, this.sendCallbackConvert(message, sendCallback));
            message.setMsgID(MessageClientIDSetter.getUniqID(msgRMQ));
        } catch (Exception e) {
            log.error(String.format("Send message async Exception, %s", message), e);
            throw this.checkProducerException(message.getTopic(), message.getMsgID(), e);
        }
    }

    private org.apache.rocketmq.client.producer.SendCallback sendCallbackConvert(final Message message, final SendCallback sendCallback) {
        org.apache.rocketmq.client.producer.SendCallback rmqSendCallback = new org.apache.rocketmq.client.producer.SendCallback() {
            @Override
            public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                sendCallback.onSuccess(OMSUtil.sendResultConvert(sendResult));
            }

            @Override
            public void onException(Throwable e) {
                String topic = message.getTopic();
                String msgId = message.getMsgID();
                OMSRuntimeException onsEx = ProducerImpl.this.checkProducerException(topic, msgId, e);
                OnExceptionContext context = new OnExceptionContext();
                context.setTopic(topic);
                context.setMessageId(msgId);
                context.setException(onsEx);
                sendCallback.onException(context);
            }
        };
        return rmqSendCallback;
    }

    @Override
    public void setCallbackExecutor(ExecutorService callbackExecutor) {
//        this.rocketmqProducer.setCallbackExecutor(callbackExecutor);
    }

    @Override
    public void updateCredential(Properties credentialProperties) {

    }

    @Override
    public <T> MessageBuilder<T> messageBuilder() {
        return null;
    }
}
