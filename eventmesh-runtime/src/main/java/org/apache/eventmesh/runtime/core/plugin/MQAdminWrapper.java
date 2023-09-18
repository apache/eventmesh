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

package org.apache.eventmesh.runtime.core.plugin;

import org.apache.eventmesh.api.admin.Admin;
import org.apache.eventmesh.api.admin.TopicProperties;
import org.apache.eventmesh.api.factory.StoragePluginFactory;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MQAdminWrapper extends MQWrapper {

    protected Admin meshMQAdmin;

    public MQAdminWrapper(String storagePluginType) {
        this.meshMQAdmin = StoragePluginFactory.getMeshMQAdmin(storagePluginType);
        if (meshMQAdmin == null) {
            log.error("can't load the meshMQAdmin plugin, please check.");
            throw new RuntimeException("doesn't load the meshMQAdmin plugin, please check.");
        }
    }

    public synchronized void init(Properties keyValue) throws Exception {
        if (inited.get()) {
            return;
        }

        meshMQAdmin.init(keyValue);
        inited.compareAndSet(false, true);
    }

    public synchronized void start() throws Exception {
        if (started.get()) {
            return;
        }

        meshMQAdmin.start();

        started.compareAndSet(false, true);
    }

    public synchronized void shutdown() throws Exception {
        if (!inited.get()) {
            return;
        }

        if (!started.get()) {
            return;
        }

        meshMQAdmin.shutdown();

        inited.compareAndSet(true, false);
        started.compareAndSet(true, false);
    }

    public Admin getMeshMQAdmin() {
        return meshMQAdmin;
    }

    public List<TopicProperties> getTopic() throws Exception {
        return meshMQAdmin.getTopic();
    }

    public void createTopic(String topicName) throws Exception {
        meshMQAdmin.createTopic(topicName);
    }

    public void deleteTopic(String topicName) throws Exception {
        meshMQAdmin.deleteTopic(topicName);
    }

    public List<CloudEvent> getEvent(String topicName, int offset, int length) throws Exception {
        return meshMQAdmin.getEvent(topicName, offset, length);
    }

    public void publish(CloudEvent cloudEvent) throws Exception {
        meshMQAdmin.publish(cloudEvent);
    }
}
