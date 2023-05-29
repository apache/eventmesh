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

package org.apache.eventmesh.sink.connector.rabbitmq.connector.connector;

import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.api.data.KeyValue;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.sink.connector.rabbitmq.connector.callback.RabbitConfirmListener;
import org.apache.eventmesh.sink.connector.rabbitmq.connector.callback.RabbitReturnListener;
import org.apache.eventmesh.sink.connector.rabbitmq.connector.config.RabbitMQSinkConfig;
import org.apache.eventmesh.sink.connector.rabbitmq.connector.config.RabbitSinkConnectorConfig;
import org.apache.eventmesh.sink.connector.rabbitmq.connector.domain.MessageInfo;
import org.apache.eventmesh.sink.connector.rabbitmq.connector.domain.RabbitDestination;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.BasicProperties.Builder;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitMQSinkConnector implements Sink {

    private RabbitSinkConnectorConfig connectorConfig;

    private Channel channel;

    private AtomicBoolean init = new AtomicBoolean(false);

    private AtomicBoolean start = new AtomicBoolean(false);

    private static final AtomicLong CORRELATION_ID = new AtomicLong(0);

    // Store messages for retrying when confirmListener is enabled.
    private Map<Long, MessageInfo> cacheForConfirm = new ConcurrentHashMap<>();

    @Override
    public Class<? extends Config> configClass() {
        return RabbitMQSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        RabbitMQSinkConfig sinkConfig = (RabbitMQSinkConfig) config;
        this.connectorConfig = sinkConfig.getConnectorConfig();
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
        }

    }

    @Override
    public void start() throws Exception {
        try {
            validateDestinationsConfig(connectorConfig, channel);
        } catch (Exception e) {
            log.error("The configuration of destination is invalid.", e);
            return;
        }
        if (init.get() && start.compareAndSet(false, true)) {
            // If the configuration value of "passive" is false, the destination will be created when not exists.
            if (!connectorConfig.isPassive()) {
                for (RabbitDestination destination : connectorConfig.getDestinations()) {
                    channel.exchangeDeclare(destination.getExchange(), destination.getExchangeType(), true);
                }
            }
            if (connectorConfig.isConfirmListener()) {
                channel.confirmSelect();
                channel.addConfirmListener(new RabbitConfirmListener(this, channel, cacheForConfirm));
            }
            if (connectorConfig.isReturnListener()) {
                channel.addReturnListener(new RabbitReturnListener(this, channel));
            }
        }
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.connectorConfig.getConnectorName();
    }

    @Override
    public void stop() throws Exception {
        if (start.compareAndSet(true, false)) {
            init.set(false);
            channel.close();
            channel.getConnection().close();
            log.info("RabbitMQSinkConnector is already stopped!");
        }
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        if (!start.get()) {
            log.warn("Sink connector is not started!");
            return;
        }
        List<RabbitDestination> destinations = connectorConfig.getDestinations();
        if (destinations.size() == 1) {
            RabbitDestination destination = destinations.get(0);
            sinkRecords.forEach(connectRecord -> publishMsg(connectRecord, destination));
        } else if (destinations.size() > 1) {
            sinkRecords.forEach(connectRecord -> {
                destinations.forEach(destination -> publishMsg(connectRecord, destination));
            });
        }
    }

    private void publishMsg(ConnectRecord connectRecord, RabbitDestination destination) {
        if (!channel.isOpen()) {
            log.error("Channel is already closed!");
            return;
        }
        BasicProperties props = convertFrom(connectRecord);
        String exchange = destination.getExchange();
        String routingKey = destination.getRoutingKey();
        byte[] data = (byte[]) connectRecord.getData();
        try {
            if (connectorConfig.isConfirmListener()) {
                // Store messages for retrying in ConfirmListener if needed then.
                long deliveryTag = channel.getNextPublishSeqNo();
                MessageInfo messageInfo = new MessageInfo(exchange, routingKey, props, data);
                cacheForConfirm.put(deliveryTag, messageInfo);
            }
            channel.basicPublish(exchange, routingKey, connectorConfig.isReturnListener(), props, data);
        } catch (IOException e) {
            log.error("Failed to publish message.", e);
        }
    }

    private static BasicProperties convertFrom(ConnectRecord connectRecord) {
        KeyValue extensions = connectRecord.getExtensions();
        Set<String> keySet = extensions.keySet();
        Map<String, Object> map = new HashMap<>(keySet == null ? 1 : (int) (keySet.size() / 0.75f + 1));
        for (String key : keySet) {
            map.put(key, extensions.getString(key));
        }
        return new Builder()
            .correlationId(CORRELATION_ID.getAndIncrement() + "")
            .timestamp(new Date())
            .headers(map)
            .build();
    }

    private static void validateDestinationsConfig(RabbitSinkConnectorConfig connectorConfig, Channel channel) {
        List<RabbitDestination> destinations = connectorConfig.getDestinations();
        destinations.forEach((destination) -> {
            String exchange = destination.getExchange();
            if (connectorConfig.isPassive()) {
                // If the configuration value of "passive" is true, throw exception when exchange not exists.
                try {
                    channel.exchangeDeclarePassive(exchange);
                } catch (IOException e) {
                    throw new IllegalArgumentException(String.format("Exchange %s does not exist.", exchange), e);
                }
            }
            String exchangeType = destination.getExchangeType();
            if (!connectorConfig.isPassive() && !StringUtils.equalsAny(exchangeType, "direct", "fanout", "topic")) {
                throw new IllegalArgumentException(String.format("ExchangeType %s is not supported.", exchangeType));
            }
        });
    }

    private static void validateAddressConfig(RabbitSinkConnectorConfig config) {
        String address = config.getAddress();
        // validate address in the format of "host1:port1,host2:port2,host3:port3"
        Pattern pattern = Pattern.compile("[^,:]+:\\d+(,[^,:]+:\\d+)*");
        Matcher matcher = pattern.matcher(address);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("The configuration of connectorConfig.address is invalid.");
        }
    }
}
