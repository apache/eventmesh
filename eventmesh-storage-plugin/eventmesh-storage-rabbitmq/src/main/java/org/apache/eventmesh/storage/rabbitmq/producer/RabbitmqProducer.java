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

package org.apache.eventmesh.storage.rabbitmq.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.exception.StorageRuntimeException;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.storage.rabbitmq.client.RabbitmqClient;
import org.apache.eventmesh.storage.rabbitmq.client.RabbitmqConnectionFactory;
import org.apache.eventmesh.storage.rabbitmq.cloudevent.RabbitmqCloudEvent;
import org.apache.eventmesh.storage.rabbitmq.cloudevent.RabbitmqCloudEventWriter;
import org.apache.eventmesh.storage.rabbitmq.config.ConfigurationHolder;
import org.apache.eventmesh.storage.rabbitmq.utils.ByteArrayUtils;

import java.util.Optional;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import lombok.extern.slf4j.Slf4j;

@Config(field = "configurationHolder")
@Slf4j
public class RabbitmqProducer implements Producer {

    private RabbitmqConnectionFactory rabbitmqConnectionFactory = new RabbitmqConnectionFactory();

    private RabbitmqClient rabbitmqClient;

    private Connection connection;

    private Channel channel;

    private volatile boolean started = false;

    /**
     * Unified configuration class corresponding to rabbitmq-client.properties
     */
    private ConfigurationHolder configurationHolder;

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
            } finally {
                started = false;
            }
        }
    }

    @Override
    public void init(Properties properties) throws Exception {
        this.rabbitmqClient = new RabbitmqClient(rabbitmqConnectionFactory);
        this.connection = rabbitmqClient.getConnection(configurationHolder.getHost(), configurationHolder.getUsername(),
                configurationHolder.getPasswd(), configurationHolder.getPort(), configurationHolder.getVirtualHost());
        this.channel = rabbitmqConnectionFactory.createChannel(connection);
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        try {
            RabbitmqCloudEventWriter writer = new RabbitmqCloudEventWriter();
            RabbitmqCloudEvent rabbitmqCloudEvent = writer.writeBinary(cloudEvent);
            byte[] data = RabbitmqCloudEvent.toByteArray(rabbitmqCloudEvent);
            if (data != null) {
                rabbitmqClient.publish(channel, configurationHolder.getExchangeName(), configurationHolder.getRoutingKey(), data);

                SendResult sendResult = new SendResult();
                sendResult.setTopic(cloudEvent.getSubject());
                sendResult.setMessageId(cloudEvent.getId());
                sendCallback.onSuccess(sendResult);
            }
        } catch (Exception ex) {
            log.error("[RabbitmqProducer] publish happen exception.", ex);
            sendCallback.onException(
                    OnExceptionContext.builder()
                            .topic(cloudEvent.getSubject())
                            .messageId(cloudEvent.getId())
                            .exception(new StorageRuntimeException(ex))
                            .build());
        }
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        try {
            Optional<byte[]> optionalBytes = ByteArrayUtils.objectToBytes(cloudEvent);
            if (optionalBytes.isPresent()) {
                byte[] data = optionalBytes.get();
                rabbitmqClient.publish(channel, configurationHolder.getExchangeName(),
                        configurationHolder.getRoutingKey(), data);
            }
        } catch (Exception ex) {
            log.error("[RabbitmqProducer] sendOneway happen exception.", ex);
        }
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {

    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        return false;
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {

    }

    @Override
    public void setExtFields() {

    }

    public void setRabbitmqConnectionFactory(RabbitmqConnectionFactory rabbitmqConnectionFactory) {
        this.rabbitmqConnectionFactory = rabbitmqConnectionFactory;
    }

    public ConfigurationHolder getClientConfiguration() {
        return this.configurationHolder;
    }
}
