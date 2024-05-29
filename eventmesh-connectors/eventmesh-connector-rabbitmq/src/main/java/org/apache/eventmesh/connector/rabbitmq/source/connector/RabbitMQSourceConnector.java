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

package org.apache.eventmesh.connector.rabbitmq.source.connector;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.mq.rabbitmq.RabbitMQSourceConfig;
import org.apache.eventmesh.common.config.connector.mq.rabbitmq.SourceConnectorConfig;
import org.apache.eventmesh.connector.rabbitmq.client.RabbitmqClient;
import org.apache.eventmesh.connector.rabbitmq.client.RabbitmqConnectionFactory;
import org.apache.eventmesh.connector.rabbitmq.cloudevent.RabbitmqCloudEvent;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitMQSourceConnector implements Source {

    private RabbitMQSourceConfig sourceConfig;

    private volatile boolean started = false;

    private static final int DEFAULT_BATCH_SIZE = 10;

    private BlockingQueue<CloudEvent> queue;

    private final RabbitmqConnectionFactory rabbitmqConnectionFactory = new RabbitmqConnectionFactory();

    private RabbitMQSourceHandler rabbitMQSourceHandler;

    private RabbitmqClient rabbitmqClient;

    private Connection connection;

    private Channel channel;

    private final ThreadPoolExecutor executor = ThreadPoolFactory.createThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() * 2,
        Runtime.getRuntime().availableProcessors() * 2,
        "EventMesh-RabbitMQSourceConnector-");

    @Override
    public Class<? extends Config> configClass() {
        return RabbitMQSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        this.queue = new LinkedBlockingQueue<>(1000);
        this.sourceConfig = (RabbitMQSourceConfig) ((SourceConnectorContext) connectorContext).getSourceConfig();
        this.rabbitmqClient = new RabbitmqClient(rabbitmqConnectionFactory);
        this.connection = rabbitmqClient.getConnection(sourceConfig.getConnectorConfig().getHost(),
            sourceConfig.getConnectorConfig().getUsername(),
            sourceConfig.getConnectorConfig().getPasswd(),
            sourceConfig.getConnectorConfig().getPort(),
            sourceConfig.getConnectorConfig().getVirtualHost());
        this.channel = rabbitmqConnectionFactory.createChannel(connection);
        this.rabbitMQSourceHandler = new RabbitMQSourceHandler(channel, sourceConfig.getConnectorConfig());
    }

    @Override
    public void start() throws Exception {
        if (!started) {
            BuiltinExchangeType builtinExchangeType = BuiltinExchangeType.valueOf(sourceConfig.getConnectorConfig().getExchangeType());
            rabbitmqClient.binding(channel, builtinExchangeType, sourceConfig.getConnectorConfig().getExchangeName(),
                sourceConfig.getConnectorConfig().getRoutingKey(), sourceConfig.getConnectorConfig().getQueueName());
            executor.execute(this.rabbitMQSourceHandler);
            started = true;
        }
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        if (started) {
            try {
                rabbitmqClient.unbinding(channel, sourceConfig.getConnectorConfig().getExchangeName(),
                    sourceConfig.getConnectorConfig().getRoutingKey(), sourceConfig.getConnectorConfig().getQueueName());
                rabbitmqClient.closeConnection(connection);
                rabbitmqClient.closeChannel(channel);
                rabbitMQSourceHandler.stop();
            } finally {
                started = false;
            }
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> connectRecords = new ArrayList<>(DEFAULT_BATCH_SIZE);
        for (int count = 0; count < DEFAULT_BATCH_SIZE; ++count) {
            try {
                CloudEvent event = queue.poll(3, TimeUnit.SECONDS);
                if (event == null) {
                    break;
                }

                connectRecords.add(CloudEventUtil.convertEventToRecord(event));
            } catch (InterruptedException e) {
                break;
            }
        }
        return connectRecords;
    }

    public class RabbitMQSourceHandler implements Runnable {

        private final Channel channel;
        private final SourceConnectorConfig connectorConfig;
        private final AtomicBoolean stop = new AtomicBoolean(false);

        public RabbitMQSourceHandler(Channel channel, SourceConnectorConfig connectorConfig) {
            this.channel = channel;
            this.connectorConfig = connectorConfig;
        }

        @Override
        public void run() {
            while (!stop.get()) {
                try {
                    GetResponse response = channel.basicGet(connectorConfig.getQueueName(), connectorConfig.isAutoAck());
                    if (response != null) {
                        RabbitmqCloudEvent rabbitmqCloudEvent = RabbitmqCloudEvent.getFromByteArray(response.getBody());
                        CloudEvent event = rabbitmqCloudEvent.convertToCloudEvent();
                        if (event != null) {
                            queue.add(event);
                        }
                        if (!connectorConfig.isAutoAck()) {
                            channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                        }
                    }
                } catch (Exception ex) {
                    log.error("[RabbitMQSourceHandler] thread run happen exception.", ex);
                }
            }
        }

        public void stop() {
            stop.compareAndSet(false, true);
        }
    }
}
