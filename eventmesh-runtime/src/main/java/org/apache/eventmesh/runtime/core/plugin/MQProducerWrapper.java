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

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.api.producer.Producer;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class MQProducerWrapper extends MQWrapper {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    protected Producer meshMQProducer;

    public MQProducerWrapper(String connectorPluginType) {
        this.meshMQProducer = ConnectorPluginFactory.getMeshMQProducer(connectorPluginType);
        if (meshMQProducer == null) {
            logger.error("can't load the meshMQProducer plugin, please check.");
            throw new RuntimeException("doesn't load the meshMQProducer plugin, please check.");
        }
    }

    public synchronized void init(Properties keyValue) throws Exception {
        if (inited.get()) {
            return;
        }

        meshMQProducer.init(keyValue);
        inited.compareAndSet(false, true);
    }

    public synchronized void start() throws Exception {
        if (started.get()) {
            return;
        }

        meshMQProducer.start();

        started.compareAndSet(false, true);
    }

    public synchronized void shutdown() throws Exception {
        if (!inited.get()) {
            return;
        }

        if (!started.get()) {
            return;
        }

        meshMQProducer.shutdown();

        inited.compareAndSet(true, false);
        started.compareAndSet(true, false);
    }

    public void send(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        meshMQProducer.publish(cloudEvent, sendCallback);
    }

    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout)
            throws Exception {
        meshMQProducer.request(cloudEvent, rrCallback, timeout);
    }

    public boolean reply(final CloudEvent cloudEvent, final SendCallback sendCallback) throws Exception {
        return meshMQProducer.reply(cloudEvent, sendCallback);
    }

    public Producer getMeshMQProducer() {
        return meshMQProducer;
    }

}
