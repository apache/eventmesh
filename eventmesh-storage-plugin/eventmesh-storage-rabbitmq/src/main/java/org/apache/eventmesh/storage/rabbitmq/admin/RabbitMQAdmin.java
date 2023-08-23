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
import org.apache.eventmesh.storage.rabbitmq.config.ConfigurationHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

public class RabbitMQAdmin extends AbstractAdmin {

    private ConfigurationHolder configurationHolder;

    private String mgmtHost;

    private int mgmtPort;

    public RabbitMQAdmin() {
        super(new AtomicBoolean(false));
    }

    public void init() throws Exception {
        this.mgmtHost = configurationHolder.getHost();
        this.mgmtPort = configurationHolder.getMgmtPort();
    }

    @Override
    public List<TopicProperties> getTopic() throws Exception {

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
    }
}
