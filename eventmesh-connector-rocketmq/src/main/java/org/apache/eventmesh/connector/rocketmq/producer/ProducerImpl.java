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

package org.apache.eventmesh.connector.rocketmq.producer;

import java.util.Properties;
import java.util.concurrent.ExecutorService;

import io.openmessaging.api.Message;
import io.openmessaging.api.MessageBuilder;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.Producer;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import io.openmessaging.api.exception.OMSRuntimeException;

import org.apache.eventmesh.connector.rocketmq.utils.OMSUtil;
import org.apache.rocketmq.common.message.MessageClientIDSetter;


public class ProducerImpl extends AbstractOMSProducer implements Producer {

    public static final int eventMeshServerAsyncAccumulationThreshold = 1000;

    public ProducerImpl(final Properties properties) {
        super(properties);
    }

    public Properties attributes() {
        return properties;
    }

    public void setExtFields() {
        super.getRocketmqProducer().setRetryTimesWhenSendFailed(0);
        super.getRocketmqProducer().setRetryTimesWhenSendAsyncFailed(0);
        super.getRocketmqProducer().setPollNameServerInterval(60000);

        super.getRocketmqProducer().getDefaultMQProducerImpl().getmQClientFactory()
                .getNettyClientConfig().setClientAsyncSemaphoreValue(eventMeshServerAsyncAccumulationThreshold);
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
