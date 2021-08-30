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

package org.apache.eventmesh.server.tcp.producer;

import io.openmessaging.api.Message;
import io.openmessaging.api.SendCallback;
import org.apache.eventmesh.api.RRCallback;
import org.apache.eventmesh.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.api.producer.MeshMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class MQProducerWrapper {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    public AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);

    protected MeshMQProducer meshMQProducer;

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

    public void send(Message message, SendCallback sendCallback) throws Exception {
        meshMQProducer.send(message, sendCallback);
    }

    public void request(Message message, RRCallback rrCallback, long timeout)
            throws Exception {
        meshMQProducer.request(message, rrCallback, timeout);
    }

    public boolean reply(final Message message, final SendCallback sendCallback) throws Exception {
        return meshMQProducer.reply(message, sendCallback);
    }

    public MeshMQProducer getMeshMQProducer() {
        return meshMQProducer;
    }
}
