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

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.connector.rocketmq.cloudevent.RocketMQMessageFactory;
import org.apache.eventmesh.connector.rocketmq.utils.OMSUtil;
import org.apache.eventmesh.connector.rocketmq.utils.CloudEventUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.RequestCallback;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class ProducerImpl extends AbstractProducer {

    public static final int eventMeshServerAsyncAccumulationThreshold = 1000;

    private final Logger logger = LoggerFactory.getLogger(ProducerImpl.class);

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
            .getNettyClientConfig()
            .setClientAsyncSemaphoreValue(eventMeshServerAsyncAccumulationThreshold);
        super.getRocketmqProducer().setCompressMsgBodyOverHowmuch(10);
    }


    public SendResult send(CloudEvent cloudEvent) {
        this.checkProducerServiceState(rocketmqProducer.getDefaultMQProducerImpl());
        org.apache.rocketmq.common.message.Message msg =
            RocketMQMessageFactory.createWriter(cloudEvent.getSubject()).writeBinary(cloudEvent);
        String messageId = null;
        try {
            org.apache.rocketmq.client.producer.SendResult sendResultRmq =
                this.rocketmqProducer.send(msg);
            SendResult sendResult = new SendResult();
            sendResult.setTopic(sendResultRmq.getMessageQueue().getTopic());
            messageId = sendResultRmq.getMsgId();
            sendResult.setMessageId(messageId);
            return sendResult;
        } catch (Exception e) {
            log.error(String.format("Send message Exception, %s", msg), e);
            throw this.checkProducerException(msg.getTopic(), messageId, e);
        }
    }


    public void sendOneway(CloudEvent cloudEvent) {
        this.checkProducerServiceState(this.rocketmqProducer.getDefaultMQProducerImpl());
        org.apache.rocketmq.common.message.Message msg =
            RocketMQMessageFactory.createWriter(cloudEvent.getSubject()).writeBinary(cloudEvent);
        try {
            this.rocketmqProducer.sendOneway(msg);
        } catch (Exception e) {
            log.error(String.format("Send message oneway Exception, %s", msg), e);
            throw this.checkProducerException(msg.getTopic(), MessageClientIDSetter.getUniqID(msg),
                e);
        }
    }


    public void sendAsync(CloudEvent cloudEvent, SendCallback sendCallback) {
        this.checkProducerServiceState(this.rocketmqProducer.getDefaultMQProducerImpl());
        org.apache.rocketmq.common.message.Message msg =
            RocketMQMessageFactory.createWriter(cloudEvent.getSubject()).writeBinary(cloudEvent);

        try {
            this.rocketmqProducer.send(msg, this.sendCallbackConvert(msg, sendCallback));
        } catch (Exception e) {
            log.error(String.format("Send message async Exception, %s", msg), e);
            throw this.checkProducerException(msg.getTopic(), MessageClientIDSetter.getUniqID(msg),
                e);
        }
    }

    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout)
        throws InterruptedException, RemotingException, MQClientException, MQBrokerException {

        this.checkProducerServiceState(this.rocketmqProducer.getDefaultMQProducerImpl());
        org.apache.rocketmq.common.message.Message msg =
            RocketMQMessageFactory.createWriter(cloudEvent.getSubject()).writeBinary(cloudEvent);
        rocketmqProducer.request(msg, rrCallbackConvert(msg, rrCallback), timeout);
    }

    public boolean reply(final CloudEvent cloudEvent, final SendCallback sendCallback) {
        this.checkProducerServiceState(this.rocketmqProducer.getDefaultMQProducerImpl());
        org.apache.rocketmq.common.message.Message msg =
            RocketMQMessageFactory.createWriter(cloudEvent.getSubject()).writeBinary(cloudEvent);
        MessageAccessor.putProperty(msg, MessageConst.PROPERTY_MESSAGE_TYPE, MixAll.REPLY_MESSAGE_FLAG);
        if (StringUtils.isNotEmpty(cloudEvent.getExtension("cluster").toString())) {
            MessageAccessor.putProperty(msg, MessageConst.PROPERTY_CLUSTER, cloudEvent.getExtension("cluster").toString());
        }
        if (StringUtils.isNotEmpty(cloudEvent.getExtension("replytoclient").toString())) {
            MessageAccessor.putProperty(msg, MessageConst.PROPERTY_MESSAGE_REPLY_TO_CLIENT, cloudEvent.getExtension("replytoclient").toString());
        }
        if (StringUtils.isNotEmpty(cloudEvent.getExtension("correlationid").toString())) {
            MessageAccessor.putProperty(msg, MessageConst.PROPERTY_CORRELATION_ID, cloudEvent.getExtension("correlationid").toString());
        }

        try {
            this.rocketmqProducer.send(msg, this.sendCallbackConvert(msg, sendCallback));
        } catch (Exception e) {
            log.error(String.format("Send message async Exception, %s", msg), e);
            throw this.checkProducerException(msg.getTopic(), MessageClientIDSetter.getUniqID(msg),
                e);
        }
        return true;

    }

    private RequestCallback rrCallbackConvert(final Message message, final RequestReplyCallback rrCallback) {
        return new RequestCallback() {
            @Override
            public void onSuccess(org.apache.rocketmq.common.message.Message message) {
                CloudEvent event = RocketMQMessageFactory.createReader(message).toEvent();
                rrCallback.onSuccess(event);
            }

            @Override
            public void onException(Throwable e) {
                String topic = message.getTopic();
                ConnectorRuntimeException onsEx =
                    ProducerImpl.this.checkProducerException(topic, null, e);
                OnExceptionContext context = new OnExceptionContext();
                context.setTopic(topic);
                context.setException(onsEx);
                rrCallback.onException(e);

            }
        };
    }

    private org.apache.rocketmq.client.producer.SendCallback sendCallbackConvert(
        final Message message, final SendCallback sendCallback) {
        org.apache.rocketmq.client.producer.SendCallback rmqSendCallback =
            new org.apache.rocketmq.client.producer.SendCallback() {
                @Override
                public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    sendCallback.onSuccess(CloudEventUtils.convertSendResult(sendResult));
                }

                @Override
                public void onException(Throwable e) {
                    String topic = message.getTopic();
                    ConnectorRuntimeException onsEx =
                        ProducerImpl.this.checkProducerException(topic, null, e);
                    OnExceptionContext context = new OnExceptionContext();
                    context.setTopic(topic);
                    context.setException(onsEx);
                    sendCallback.onException(context);
                }
            };
        return rmqSendCallback;
    }

}
