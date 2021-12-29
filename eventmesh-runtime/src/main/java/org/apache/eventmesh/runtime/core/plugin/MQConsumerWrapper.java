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

import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.api.factory.ConnectorPluginFactory;

public class MQConsumerWrapper extends MQWrapper {

    public final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected Consumer meshMQPushConsumer;

    public MQConsumerWrapper(String connectorPluginType) {
        this.meshMQPushConsumer = ConnectorPluginFactory.getMeshMQPushConsumer(connectorPluginType);
        if (meshMQPushConsumer == null) {
            logger.error("can't load the meshMQPushConsumer plugin, please check.");
            throw new RuntimeException("doesn't load the meshMQPushConsumer plugin, please check.");
        }
    }

    public void subscribe(String topic, EventListener listener) throws Exception {
        meshMQPushConsumer.subscribe(topic, listener);
    }

    public void unsubscribe(String topic) throws Exception {
        meshMQPushConsumer.unsubscribe(topic);
    }

    public synchronized void init(Properties keyValue) throws Exception {

        meshMQPushConsumer.init(keyValue);
        inited.compareAndSet(false, true);
    }

    public synchronized void start() throws Exception {
        meshMQPushConsumer.start();
        started.compareAndSet(false, true);
    }

    public synchronized void shutdown() throws Exception {
        meshMQPushConsumer.shutdown();
        inited.compareAndSet(false, true);
        started.compareAndSet(false, true);
    }

    //public void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently) {
    //    meshMQPushConsumer.registerMessageListener(messageListenerConcurrently);
    //}

    public void updateOffset(List<CloudEvent> events, AbstractContext eventMeshConsumeConcurrentlyContext) {
        meshMQPushConsumer.updateOffset(events, eventMeshConsumeConcurrentlyContext);
    }
}
