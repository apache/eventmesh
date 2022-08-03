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

package org.apache.eventmesh.connector.dledger;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.connector.dledger.broker.CloudEventMessage;
import org.apache.eventmesh.connector.dledger.broker.DLedgerMessageWriter;
import org.apache.eventmesh.connector.dledger.broker.DLedgerTopicIndexesStore;
import org.apache.eventmesh.connector.dledger.clientpool.DLedgerClientPool;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DLedgerProducer implements Producer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DLedgerProducer.class);

    private DLedgerClientPool clientPool;
    private DLedgerTopicIndexesStore topicIndexesStore;
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void init(Properties properties) throws Exception {
        clientPool = DLedgerClientPool.getInstance();
        topicIndexesStore = DLedgerTopicIndexesStore.getInstance();
    }

    @Override
    public void start() {
        try {
            clientPool.preparePool();
            started.compareAndSet(false, true);
        } catch (Exception e) {
            throw new DLedgerConnectorException("start clientPool fail.");
        }
    }

    @Override
    public void shutdown() {
        clientPool.close();
        started.compareAndSet(true, false);
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isClosed() {
        return !started.get();
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        Preconditions.checkNotNull(cloudEvent);
        Preconditions.checkNotNull(sendCallback);

        DLedgerMessageWriter<CloudEventMessage> messageWriter = new DLedgerMessageWriter<>(cloudEvent.getSubject());
        CloudEventMessage message = messageWriter.writeBinary(cloudEvent);
        try {
            SendResult sendResult = clientPool.append(message.getTopic(), CloudEventMessage.toByteArray(message));
            topicIndexesStore.publish(sendResult.getTopic(), Long.parseLong(sendResult.getMessageId()));
            sendCallback.onSuccess(sendResult);
        } catch (Exception ex) {
            OnExceptionContext onExceptionContext = OnExceptionContext.builder()
                                                                      .messageId(cloudEvent.getId())
                                                                      .topic(cloudEvent.getSubject())
                                                                      .exception(new ConnectorRuntimeException(ex))
                                                                      .build();
            sendCallback.onException(onExceptionContext);
        }
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        boolean exist = topicIndexesStore.contain(topic);
        if (!exist) {
            throw new DLedgerConnectorException(String.format("topic: %s is not exist.", topic));
        }
    }

    @Override
    public void setExtFields() {
    }
}
