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

package org.apache.eventmesh.storage.rabbitmq.admin;

import org.apache.eventmesh.api.admin.AbstractAdmin;
import org.apache.eventmesh.api.admin.TopicProperties;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitMQAdmin extends AbstractAdmin {

    private Connection connection;

    private Channel channel;

    public RabbitMQAdmin() {
        super(new AtomicBoolean(false));
        // config
    }

    // more
    @Override
    public void init(Properties properties) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(properties.getProperty("rabbitmq.host", "localhost"));
        factory.setPort(Integer.parseInt(properties.getProperty("rabbitmq.port", "5672")));
        factory.setUsername(properties.getProperty("rabbitmq.username", "guest"));
        factory.setPassword(properties.getProperty("rabbitmq.password", "guest"));

        connection = factory.newConnection();
        channel = connection.createChannel();
        start();
    }

    @Override
    public List<TopicProperties> getTopic() throws Exception {
        // Implement logic to retrieve topic information from RabbitMQ.
        // Use channel to interact with RabbitMQ, e.g., channel.queueDeclare, channel.exchangeDeclare, etc.
        return null;
    }

    @Override
    public void createTopic(String topicName) throws Exception {
        // Implement logic to create a topic in RabbitMQ.
    }

    @Override
    public void deleteTopic(String topicName) throws Exception {
        // Implement logic to delete a topic from RabbitMQ.
    }

    // more
    @Override
    public List<CloudEvent> getEvent(String topicName, int offset, int length) throws Exception {
        // Implement logic to retrieve events from RabbitMQ for the given topic.
        return null;
    }

    // more
    @Override
    public void publish(CloudEvent cloudEvent) throws Exception {
        // Implement logic to publish an event to a specific topic in RabbitMQ.
    }

    // more
    @Override
    public void shutdown() {
        super.shutdown();
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {
                // Handle the exception.
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                // Handle the exception.
            }
        }
    }
}
