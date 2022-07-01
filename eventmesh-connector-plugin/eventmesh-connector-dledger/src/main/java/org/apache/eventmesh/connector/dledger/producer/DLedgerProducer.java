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

package org.apache.eventmesh.connector.dledger.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.connector.dledger.config.DLedgerClientConfiguration;
import org.apache.eventmesh.connector.dledger.DLedgerClientFactory;
import org.apache.eventmesh.connector.dledger.DLedgerClientPool;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ConcurrentMap<String, Long> messageTopicAndIndexMap = new ConcurrentHashMap<>();

    @Override
    public void init(Properties properties) throws Exception {
        final DLedgerClientConfiguration clientConfiguration = new DLedgerClientConfiguration();
        clientConfiguration.init();
        String group = properties.getProperty("producerGroup", "default");
        String peers = clientConfiguration.getPeers();
        int poolSize = clientConfiguration.getClientPoolSize();

        DLedgerClientFactory factory = new DLedgerClientFactory(group, peers);
        clientPool = DLedgerClientPool.getInstance(factory, poolSize);
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

        try {
            SendResult sendResult = clientPool.append(cloudEvent.getSubject(), cloudEvent.getData().toBytes());
            messageTopicAndIndexMap.putIfAbsent(sendResult.getTopic(), Long.valueOf(sendResult.getMessageId()));
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
        try {
            SendResult sendResult = clientPool.append(cloudEvent.getSubject(), cloudEvent.getData().toBytes());
            messageTopicAndIndexMap.putIfAbsent(sendResult.getTopic(), Long.valueOf(sendResult.getMessageId()));
        } catch (Exception e) {
            throw new DLedgerConnectorException(e);
        }
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        // TODO request asynchronously, see RocketMQ Connector implementation
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        publish(cloudEvent, sendCallback);
        return true;
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        boolean exist = messageTopicAndIndexMap.containsKey(topic);
        if (!exist) {
            throw new DLedgerConnectorException(String.format("topic: %s is not exist.", topic));
        }
    }

    @Override
    public void setExtFields() {

    }
}
