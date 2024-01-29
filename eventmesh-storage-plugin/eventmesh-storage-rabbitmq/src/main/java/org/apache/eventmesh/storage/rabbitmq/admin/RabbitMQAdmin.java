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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

public class RabbitMQAdmin extends AbstractAdmin {

    private ConfigurationHolder configurationHolder;

    private String mgmtHost;

    private int mgmtPort;

    private String mgmtProtocol;

    public RabbitMQAdmin() {
        super(new AtomicBoolean(false));
    }

    public void init() throws Exception {
        this.mgmtHost = configurationHolder.getHost();
        this.mgmtPort = configurationHolder.getMgmtPort();
        this.mgmtProtocol = configurationHolder.getMgmtProtocol();
    }

    @Override
    public List<TopicProperties> getTopic() {
        // Utilizing the RabbitMQ Management HTTP API is a favorable approach to list queues and historical message counts.
        // To display topics, it would be necessary to retrieve the topic name from each message and use it to declare a corresponding queue.
        return new ArrayList<>();
    }

    @Override
    public void createTopic(String topicName) {
    }

    @Override
    public void deleteTopic(String topicName) {
    }

    @Override
    public void publish(CloudEvent cloudEvent) throws Exception {
    }

    @Override
    public void shutdown() {
    }
}
