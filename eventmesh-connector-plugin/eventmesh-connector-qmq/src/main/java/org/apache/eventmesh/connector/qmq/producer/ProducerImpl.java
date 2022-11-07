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

package org.apache.eventmesh.connector.qmq.producer;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.connector.qmq.common.EventMeshConstants;


import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;


import qunar.tc.qmq.Message;
import qunar.tc.qmq.MessageSendStateListener;
import qunar.tc.qmq.TransactionProvider;
import qunar.tc.qmq.common.ClientInfo;
import qunar.tc.qmq.producer.MessageProducerProvider;

public class ProducerImpl extends AbstractProducer {
    private static final Logger LOG = LoggerFactory.getLogger(ProducerImpl.class);

    private final AtomicBoolean started = new AtomicBoolean(false);

    private MessageProducerProvider producer = new MessageProducerProvider();

    private final TransactionProvider transactionProvider = null;

    public ProducerImpl(final Properties properties) {
        super(properties);
    }

    //TODO
    //delay message
    //transaction message
    //client tag
    //client once only

    //for test
    public void setMessageProducerProvider(MessageProducerProvider producer) {
        this.producer = producer;
    }

    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) {

        try {
            String subject = cloudEvent.getSubject();
            final Message message = producer.generateMessage(subject);

            byte[] serializedCloudEvent = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(cloudEvent);

            message.setLargeString(EventMeshConstants.QMQ_MSG_BODY, new String(serializedCloudEvent, StandardCharsets.UTF_8));


            producer.sendMessage(message, new MessageSendStateListener() {
                @Override
                public void onSuccess(Message message) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("message send success id:{}", message.getMessageId());
                    }

                    final SendResult sr = new SendResult();
                    sr.setTopic(message.getSubject());
                    sr.setMessageId(message.getMessageId());
                    sendCallback.onSuccess(sr);
                }

                @Override
                public void onFailed(Message message) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("message send failed id:{}", message.getMessageId());
                    }

                    Exception e = new Exception("message send failed id:" + message.getMessageId());
                    final ConnectorRuntimeException onsEx = checkProducerException(cloudEvent, e);

                    OnExceptionContext context = new OnExceptionContext();
                    context.setTopic(message.getSubject());
                    context.setMessageId(message.getMessageId());
                    context.setException(onsEx);
                    sendCallback.onException(context);
                }
            });

        } catch (Exception e) {
            ConnectorRuntimeException onsEx = this.checkProducerException(cloudEvent, e);
            OnExceptionContext context = new OnExceptionContext();
            context.setTopic(cloudEvent.getSubject());
            context.setException(onsEx);
            sendCallback.onException(context);
        }
    }

    public void init() {

        try {
            String metaServer = this.properties.getProperty(EventMeshConstants.QMQ_METASERVER_KEY, "http://127.0.0.1:8080/meta/address");
            String appCode = this.properties.getProperty(EventMeshConstants.QMQ_APPCODE_KEY, "eventmesh");
            String idc = this.properties.getProperty(EventMeshConstants.QMQ_IDC_KEY, "default");

            String producerSendThreadsStr = this.properties.getProperty(EventMeshConstants.QMQ_PRODUCER_THREADCOUNT_KEY, "3");
            String producerSendBatchStr = this.properties.getProperty(EventMeshConstants.QMQ_PRODUCER_BATCHSIZE_KEY, "30");
            String producerMaxQueueSizeStr = this.properties.getProperty(EventMeshConstants.QMQ_PRODUCER_MAXQUEUESIZE_KEY, "10000");
            String producerSendTryCountStr = this.properties.getProperty(EventMeshConstants.QMQ_PRODUCER_TRYCOUNT_KEY, "10");

            this.producer.setMetaServer(metaServer);
            this.producer.setAppCode(appCode);
            this.producer.setClientInfo(ClientInfo.of(appCode, idc));
            this.producer.setSendThreads(Integer.parseInt(producerSendThreadsStr));
            this.producer.setSendBatch(Integer.parseInt(producerSendBatchStr));
            this.producer.setMaxQueueSize(Integer.parseInt(producerMaxQueueSizeStr));
            this.producer.setSendTryCount(Integer.parseInt(producerSendTryCountStr));
            this.producer.init();

        } catch (Exception ex) {
            throw new ConnectorRuntimeException(String.format("Failed to connect qmq with exception: {}", ex.getMessage()));
        }
    }

    public void start() {
        try {
            this.started.compareAndSet(false, true);
        } catch (Exception ignored) {
            // ignored
        }
    }

    public void shutdown() {
        try {
            this.started.compareAndSet(true, false);
            this.producer.destroy();
        } catch (Exception ignored) {
            // ignored
        }
    }

    public boolean isStarted() {
        return this.started.get();
    }

    public boolean isClosed() {
        return !this.isStarted();
    }
}