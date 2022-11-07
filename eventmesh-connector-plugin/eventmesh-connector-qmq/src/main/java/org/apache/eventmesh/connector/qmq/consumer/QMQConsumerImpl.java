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

package org.apache.eventmesh.connector.qmq.consumer;


import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.qmq.common.EventMeshConstants;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

import qunar.tc.qmq.Message;
import qunar.tc.qmq.common.ClientInfo;
import qunar.tc.qmq.consumer.MessageConsumerProvider;


public class QMQConsumerImpl implements Consumer {
    private final static Logger LOG = LoggerFactory.getLogger(QMQConsumerImpl.class);

    private final AtomicBoolean started = new AtomicBoolean(false);
    private Properties properties;
    private MessageConsumerProvider consumer = new MessageConsumerProvider();

    private BlockingThreadPoolExecutor executor;

    private String topic;

    private String consumerGroup;

    private boolean isBroadcast = false;

    private final static QMQMessageCache messageMap = new QMQMessageCache();

    private QMQPullMessageTask qmqPullMessageTask;

    //for test
    public void setConsumer(MessageConsumerProvider consumer) {
        this.consumer = consumer;
    }

    @Override
    public void init(Properties properties) throws Exception {
        this.properties = properties;

        try {
            this.consumerGroup = this.properties.getProperty(EventMeshConstants.QMQ_CONSUMERGROUP_KEY, "default_group");
            this.isBroadcast = Boolean.parseBoolean(this.properties.getProperty(Constants.IS_BROADCAST, "false"));

            String threadPoolSizeStr = this.properties.getProperty(EventMeshConstants.QMQ_CONSUMER_THREADPOOLSIZE_KEY,
                    "" + Runtime.getRuntime().availableProcessors());

            String threadPoolQueueSizeStr = this.properties.getProperty(EventMeshConstants.QMQ_CONSUMER_THREADPOOLQUEUESIZE_KEY,
                    "500");

            int threadPoolSize = Integer.parseInt(threadPoolSizeStr);
            int threadPoolQueueSize = Integer.parseInt(threadPoolQueueSizeStr);

            QMQThreadFactory threadFactory = new QMQThreadFactory();
            this.executor = new BlockingThreadPoolExecutor(threadPoolSize, threadPoolSize,
                    30, TimeUnit.SECONDS, threadPoolQueueSize, threadFactory);

            String metaServer = this.properties.getProperty(EventMeshConstants.QMQ_METASERVER_KEY,
                    "http://127.0.0.1:8080/meta/address");
            String appCode = this.properties.getProperty(EventMeshConstants.QMQ_APPCODE_KEY, "eventmesh");
            String idc = this.properties.getProperty(EventMeshConstants.QMQ_IDC_KEY, "default");

            this.consumer.setMetaServer(metaServer);
            this.consumer.setAppCode(appCode);
            this.consumer.setClientInfo(ClientInfo.of(appCode, idc));
            this.consumer.init();
            this.consumer.online();

        } catch (Exception ex) {
            throw new ConnectorRuntimeException(
                    String.format("Failed to connect qmq with exception: {}", ex.getMessage()));
        }
    }

    @Override
    public void start() {
        this.started.compareAndSet(false, true);
    }

    @Override
    public void subscribe(String topic) throws Exception {
        this.topic = topic;
    }

    @Override
    public void unsubscribe(String topic) {
        try {
            this.qmqPullMessageTask.stop();
        } catch (Exception ex) {
            throw new ConnectorRuntimeException(
                    String.format("Failed to unsubscribe the topic:%s with exception: %s", topic, ex.getMessage()));
        }
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        if (cloudEvents == null) return;

        for (CloudEvent event : cloudEvents) {
            try {
                Message message = messageMap.removeMessage(event.getId());
                message.ack(System.currentTimeMillis() - message.getCreatedTime().getTime(), null);
            } catch (Exception eex) {
                LOG.error("message ack error", eex);
            }
        }
    }

    @Override
    public void registerEventListener(EventListener listener) {
        if (this.consumer == null) {
            throw new ConnectorRuntimeException(
                    String.format("Cann't find the qmq consumer for topic: %s", topic));
        }

        if (this.qmqPullMessageTask != null) {
            return;
        }

        final EventListener eventListener = listener;
        this.qmqPullMessageTask = new QMQPullMessageTask(this.consumer, this.topic, this.consumerGroup,
                this.isBroadcast, eventListener, this.executor, this.messageMap);

        Thread dat = new Thread(this.qmqPullMessageTask);
        dat.setDaemon(true);
        dat.start();

    }

    @Override
    public boolean isStarted() {
        return this.started.get();
    }

    @Override
    public boolean isClosed() {
        return !this.isStarted();
    }

    private void clearQMQMessageCache() {
        for (Map.Entry<String, Message> entry : messageMap.entrySet()) {
            try {
                Message message = entry.getValue();
                message.ack(System.currentTimeMillis() - message.getCreatedTime().getTime(), null);
            } catch (Exception eex) {
                LOG.error("message ack error", eex);
            }
        }
    }

    @Override
    public void shutdown() {
        this.started.compareAndSet(true, false);
        try {
            this.qmqPullMessageTask.stop();
            this.executor.shutdown();
            this.clearQMQMessageCache();
            this.consumer.destroy();
        } catch (Exception ex) {
            throw new ConnectorRuntimeException(
                    String.format("Failed to close the qmq provider with exception: %s", ex.getMessage()));
        }
    }
}
