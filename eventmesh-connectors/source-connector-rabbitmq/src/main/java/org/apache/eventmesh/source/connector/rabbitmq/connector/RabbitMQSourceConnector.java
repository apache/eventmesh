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

package org.apache.eventmesh.source.connector.rabbitmq.connector;

import static org.apache.eventmesh.openconnect.api.config.Constants.QUEUE_OFFSET;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.BASIC_PROPS;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.DELIVERY_TAG;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.EXCHANGE;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.PULL;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.PUSH;
import static org.apache.eventmesh.source.connector.rabbitmq.utils.RabbitSourceConstants.ROUTING_KEY;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.source.connector.rabbitmq.RabbitPushConsumer;
import org.apache.eventmesh.source.connector.rabbitmq.config.RabbitMQSourceConfig;
import org.apache.eventmesh.source.connector.rabbitmq.config.RabbitSourceConnectorConfig;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.util.Maps;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitMQSourceConnector implements Source {

    private RabbitSourceConnectorConfig connectorConfig;

    private Channel channel;

    private String[] queues;

    /**
     * When the configuration of consumeMode is "push", this queue will be used for storing messages.
     * These messages come from {@link RabbitPushConsumer} and are taken by SourceWorker.
     */
    private BlockingQueue<ConnectRecord> messageQueue = new LinkedBlockingQueue<>();

    private AtomicBoolean init = new AtomicBoolean(false);

    private AtomicBoolean start = new AtomicBoolean(false);

    @Override
    public Class<? extends Config> configClass() {
        return RabbitMQSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        RabbitMQSourceConfig sourceConfig = (RabbitMQSourceConfig) config;
        connectorConfig = sourceConfig.getConnectorConfig();
        try {
            validateAddressConfig(connectorConfig);
        } catch (Exception e) {
            log.error("The configuration of address is invalid.", e);
            return;
        }
        if (init.compareAndSet(false, true)) {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setVirtualHost(connectorConfig.getVirtualHost());
            connectionFactory.setUsername(connectorConfig.getUserName());
            connectionFactory.setPassword(connectorConfig.getPassWord());
            connectionFactory.setConnectionTimeout(connectorConfig.getConnectionTimeout());

            String[] addrStrs = connectorConfig.getAddress().split(",");
            Connection connection;
            if (addrStrs.length == 1) {
                connection = connectionFactory.newConnection(new Address[] {
                    new Address(addrStrs[0].split(":")[0], Integer.parseInt(addrStrs[0].split(":")[1]))
                });
            } else {
                Address[] addresses = Arrays.stream(connectorConfig.getAddress().split(","))
                    .map((address) -> {
                        String[] hostPorts = address.split(":");
                        return new Address(hostPorts[0], Integer.parseInt(hostPorts[1]));
                    })
                    .toArray(Address[]::new);
                connection = connectionFactory.newConnection(addresses);
            }
            this.channel = connection.createChannel();
            queues = connectorConfig.getQueues().toArray(new String[0]);
        }
    }

    @Override
    public void start() throws Exception {
        try {
            validateQueuesConfig(connectorConfig, this.channel);
            validateConsumeModeConfig(connectorConfig);
        } catch (Exception e) {
            log.error("The configuration of source connector is invalid.", e);
            return;
        }
        if (init.get() && start.compareAndSet(false, true)) {
            String consumeMode = connectorConfig.getConsumeMode();
            if (StringUtils.equals(consumeMode, PUSH)) {
                if (connectorConfig.isQosEnable()) {
                    channel.basicQos(connectorConfig.getPrefetchCount());
                }
                for (String queue : queues) {
                    channel.basicConsume(queue, connectorConfig.isAutoAck(), new RabbitPushConsumer(channel, messageQueue, this));
                }
            }
        }
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return connectorConfig.getConnectorName();
    }

    @Override
    public void stop() throws Exception {
        if (start.compareAndSet(true, false)) {
            init.set(false);
            channel.close();
            channel.getConnection().close();
            log.info("RabbitMQSourceConnector is already stopped!");
        }
    }

    @SneakyThrows
    @Override
    public List<ConnectRecord> poll() {
        if (!start.get()) {
            log.warn("Source connector is not started!");
            return Collections.emptyList();
        }
        List<ConnectRecord> connectRecords = new ArrayList<>();
        String consumeMode = connectorConfig.getConsumeMode();
        if (StringUtils.equals(consumeMode, PUSH)) {
            ConnectRecord connectRecord = messageQueue.take();
            connectRecords.add(connectRecord);
        } else {
            if (!channel.isOpen()) {
                log.error("Channel is already closed!");
                return connectRecords;
            }
            for (String queue : queues) {
                GetResponse response = channel.basicGet(queue, connectorConfig.isAutoAck());
                if (response != null) {
                    connectRecords.add(buildConnectRecord(response));
                    if (!connectorConfig.isAutoAck()) {
                        channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                    }
                }
            }
        }
        return connectRecords;
    }

    public RabbitSourceConnectorConfig getConnectorConfig() {
        return connectorConfig;
    }

    private ConnectRecord buildConnectRecord(GetResponse response) {
        Envelope envelope = response.getEnvelope();
        Map<String, Object> map = new HashMap<>(4);
        map.put(EXCHANGE, envelope.getExchange());
        map.put(ROUTING_KEY, envelope.getRoutingKey());
        map.put(DELIVERY_TAG, envelope.getDeliveryTag());
        map.put(BASIC_PROPS, response.getProps());
        RecordPartition recordPartition = new RecordPartition(map);
        RecordOffset recordOffset = new RecordOffset(Maps.newHashMap(QUEUE_OFFSET, envelope.getDeliveryTag()));
        ConnectRecord connectRecord = new ConnectRecord(recordPartition,
            recordOffset,
            System.currentTimeMillis(),
            new String(response.getBody(), Constants.DEFAULT_CHARSET));
        return connectRecord;
    }

    private static void validateConsumeModeConfig(RabbitSourceConnectorConfig connectorConfig) {
        String consumeMode = connectorConfig.getConsumeMode();
        if (!StringUtils.equalsAny(consumeMode, PUSH, PULL)) {
            throw new IllegalArgumentException(String.format("Consume mode: %s is invalid.", consumeMode));
        }
    }

    private static void validateQueuesConfig(RabbitSourceConnectorConfig config, Channel channel) {
        for (String queue : config.getQueues()) {
            try {
                channel.queueDeclarePassive(queue);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                    String.format("Queue: %s doesn't exists or it is exclusively owned by another connection.", queue), e);
            }
        }
    }

    private static void validateAddressConfig(RabbitSourceConnectorConfig config) {
        String address = config.getAddress();
        // validate address in format of "host1:port1,host2:port2,host3:port3"
        Pattern pattern = Pattern.compile("[^,:]+:\\d+(,[^,:]+:\\d+)*");
        Matcher matcher = pattern.matcher(address);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("The configuration of address is invalid.");
        }
    }
}
