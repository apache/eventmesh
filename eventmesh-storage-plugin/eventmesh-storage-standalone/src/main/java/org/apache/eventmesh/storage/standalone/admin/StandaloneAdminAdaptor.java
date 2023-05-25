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

package org.apache.eventmesh.storage.standalone.admin;

import org.apache.eventmesh.api.admin.Admin;
import org.apache.eventmesh.api.admin.TopicProperties;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

public class StandaloneAdminAdaptor implements Admin {

    private final StandaloneAdmin admin;

    public StandaloneAdminAdaptor() {
        admin = new StandaloneAdmin();
    }

    @Override
    public boolean isStarted() {
        return admin.isStarted();
    }

    @Override
    public boolean isClosed() {
        return admin.isClosed();
    }

    @Override
    public void start() {
        admin.start();
    }

    @Override
    public void shutdown() {
        admin.shutdown();
    }

    @Override
    public void init(Properties properties) throws Exception {
        admin.init(properties);
    }

    @Override
    public List<TopicProperties> getTopic() throws Exception {
        return admin.getTopic();
    }

    @Override
    public void createTopic(String topicName) throws Exception {
        admin.createTopic(topicName);
    }

    @Override
    public void deleteTopic(String topicName) throws Exception {
        admin.deleteTopic(topicName);
    }

    @Override
    public List<CloudEvent> getEvent(String topicName, int offset, int length) throws Exception {
        return admin.getEvent(topicName, offset, length);
    }

    @Override
    public void publish(CloudEvent cloudEvent) throws Exception {
        admin.publish(cloudEvent);
    }
}
