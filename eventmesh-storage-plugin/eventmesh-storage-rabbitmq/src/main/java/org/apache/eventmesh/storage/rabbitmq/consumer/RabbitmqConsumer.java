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

package org.apache.eventmesh.storage.rabbitmq.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.storage.rabbitmq.client.RabbitmqClient;
import org.apache.eventmesh.storage.rabbitmq.client.RabbitmqConnectionFactory;
import org.apache.eventmesh.storage.rabbitmq.config.ConfigurationHolder;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;

import io.cloudevents.CloudEvent;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import lombok.extern.slf4j.Slf4j;

@Config(field = "configurationHolder")

@Slf4j
public class RabbitmqConsumer implements Consumer {

    private RabbitmqConnectionFactory rabbitmqConnectionFactory = new RabbitmqConnectionFactory();

    private RabbitmqClient rabbitmqClient;

    private Connection connection;

    private Channel channel;

    private volatile boolean started = false;

    /**
     * Unified configuration class corresponding to rabbitmq-client.properties
     */
    private ConfigurationHolder configurationHolder;

    private final ThreadPoolExecutor executor = ThreadPoolFactory.createThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() * 2,
        Runtime.getRuntime().availableProcessors() * 2,
        "EventMesh-Rabbitmq-Consumer");

    private RabbitmqConsumerHandler rabbitmqConsumerHandler;

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isClosed() {
        return !isStarted();
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
        }
    }

    @Override
    public void shutdown() {
        if (started) {
            try {
                rabbitmqClient.closeConnection(connection);
                rabbitmqClient.closeChannel(channel);
                rabbitmqConsumerHandler.stop();
            } finally {
                started = false;
            }
        }
    }

    @Override
    public void init(Properties keyValue) throws Exception {
        this.rabbitmqClient = new RabbitmqClient(rabbitmqConnectionFactory);
        this.connection = rabbitmqClient.getConnection(configurationHolder.getHost(), configurationHolder.getUsername(),
            configurationHolder.getPasswd(), configurationHolder.getPort(), configurationHolder.getVirtualHost());
        this.channel = rabbitmqConnectionFactory.createChannel(connection);
        this.rabbitmqConsumerHandler = new RabbitmqConsumerHandler(channel, configurationHolder);
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {

    }

    @Override
    public void subscribe(String topic) {
        rabbitmqClient.binding(channel, configurationHolder.getExchangeType(), configurationHolder.getExchangeName(),
            configurationHolder.getRoutingKey(), configurationHolder.getQueueName());
        executor.execute(rabbitmqConsumerHandler);
    }

    @Override
    public void unsubscribe(String topic) {
        try {
            rabbitmqClient.unbinding(channel, configurationHolder.getExchangeName(),
                configurationHolder.getRoutingKey(), configurationHolder.getQueueName());
            rabbitmqConsumerHandler.stop();
        } catch (Exception ex) {
            log.error("[RabbitmqConsumer] unsubscribe happen exception.", ex);
        }
    }

    @Override
    public void registerEventListener(EventListener listener) {
        rabbitmqConsumerHandler.setEventListener(listener);
    }

    public void setRabbitmqConnectionFactory(RabbitmqConnectionFactory rabbitmqConnectionFactory) {
        this.rabbitmqConnectionFactory = rabbitmqConnectionFactory;
    }

    public ConfigurationHolder getClientConfiguration() {
        return this.configurationHolder;
    }
}
