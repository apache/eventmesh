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

package org.apache.eventmesh.storage.kafka.admin;

import org.apache.eventmesh.api.admin.AbstractAdmin;
import org.apache.eventmesh.api.admin.TopicProperties;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.storage.kafka.config.ClientConfiguration;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;


public class KafkaAdmin extends AbstractAdmin {

    protected String nameServerAddr;

    protected String clusterName;

    public KafkaAdmin() {
        super(new AtomicBoolean(false));

        ConfigService configService = ConfigService.getInstance();
        ClientConfiguration clientConfiguration = configService.buildConfigInstance(ClientConfiguration.class);

        nameServerAddr = clientConfiguration.getNamesrvAddr();
        clusterName = clientConfiguration.getClusterName();
    }

    @Override
    public List<TopicProperties> getTopic() throws Exception {
        return null;
    }

    @Override
    public void createTopic(String topicName) {
    }

    @Override
    public void deleteTopic(String topicName) {
    }

    @Override
    public List<CloudEvent> getEvent(String topicName, int offset, int length) throws Exception {
        return null;
    }

    @Override
    public void publish(CloudEvent cloudEvent) throws Exception {
    }

}
