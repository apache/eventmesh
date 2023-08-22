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

package org.apache.eventmesh.connector.rabbitmq.sink.connector;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.connector.rabbitmq.client.RabbitmqClient;
import org.apache.eventmesh.connector.rabbitmq.client.RabbitmqConnectionFactory;
import org.apache.eventmesh.connector.rabbitmq.sink.config.RabbitMQSinkConfig;
import org.apache.eventmesh.connector.rabbitmq.utils.ByteArrayUtils;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.util.List;
import java.util.Optional;

@Slf4j
public class RabbitMQSinkConnector implements Sink {

    private RabbitMQSinkConfig sinkConfig;

    private final RabbitmqConnectionFactory rabbitmqConnectionFactory = new RabbitmqConnectionFactory();

    private RabbitmqClient rabbitmqClient;

    private Connection connection;

    private Channel channel;

    private volatile boolean started = false;

    @Override
    public Class<? extends Config> configClass() {
        return RabbitMQSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.sinkConfig = (RabbitMQSinkConfig) config;
        this.rabbitmqClient = new RabbitmqClient(rabbitmqConnectionFactory);
        this.connection = rabbitmqClient.getConnection(sinkConfig.getConnectorConfig().getHost(), sinkConfig.getConnectorConfig().getUsername(),
                sinkConfig.getConnectorConfig().getPasswd(), sinkConfig.getConnectorConfig().getPort(), sinkConfig.getConnectorConfig().getVirtualHost());
        this.channel = rabbitmqConnectionFactory.createChannel(connection);
    }

    @Override
    public void start() throws Exception {
        if (!started) {
            started = true;
        }
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        if (started) {
            try {
                rabbitmqClient.closeConnection(connection);
                rabbitmqClient.closeChannel(channel);
            } finally {
                started = false;
            }
        }
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
            CloudEvent event = CloudEventUtil.convertRecordToEvent(connectRecord);
            try {
                Optional<byte[]> optionalBytes = ByteArrayUtils.objectToBytes(event);
                if (optionalBytes.isPresent()) {
                    byte[] data = optionalBytes.get();
                    rabbitmqClient.publish(channel, sinkConfig.getConnectorConfig().getExchangeName(),
                            sinkConfig.getConnectorConfig().getRoutingKey(), data);
                }
            } catch (InterruptedException e) {
                Thread currentThread = Thread.currentThread();
                log.warn("[RabbitMQSinkConnector] Interrupting thread {} due to exception {}",
                    currentThread.getName(), e.getMessage());
                currentThread.interrupt();
            } catch (Exception e) {
                log.error("[RabbitMQSinkConnector] sendResult has error : ", e);
            }
        }
    }
}
