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

package org.apache.eventmesh.connector.pravega;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.connector.pravega.client.PravegaClient;
import org.apache.eventmesh.connector.pravega.config.PravegaConnectorConfig;
import org.apache.eventmesh.connector.pravega.exception.PravegaConnectorException;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Config(field = "pravegaConnectorConfig")
public class PravegaConsumerImpl implements Consumer {

    private static final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Unified configuration class corresponding to pravega-connector.properties
     */
    private PravegaConnectorConfig pravegaConnectorConfig;

    private boolean isBroadcast;
    private String instanceName;
    private String consumerGroup;
    private PravegaClient client;
    private EventListener eventListener;

    @Override
    public void init(Properties keyValue) throws Exception {
        isBroadcast = Boolean.parseBoolean(keyValue.getProperty("isBroadcast", "false"));
        instanceName = keyValue.getProperty("instanceName", "");
        consumerGroup = keyValue.getProperty("consumerGroup", "");

        client = PravegaClient.getInstance(pravegaConnectorConfig);
    }

    @Override
    public void start() {
        started.compareAndSet(false, true);
    }

    @Override
    public void shutdown() {
        started.compareAndSet(true, false);
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isClosed() {
        return !started.get();
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void subscribe(String topic) throws Exception {
        if (!client.subscribe(topic, isBroadcast, consumerGroup, instanceName, eventListener)) {
            throw new PravegaConnectorException(String.format("subscribe topic[%s] fail.", topic));
        }
    }

    @Override
    public void unsubscribe(String topic) {
        if (!client.unsubscribe(topic, isBroadcast, consumerGroup)) {
            throw new PravegaConnectorException(String.format("unsubscribe topic[%s] fail.", topic));
        }
    }

    @Override
    public void registerEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    public PravegaConnectorConfig getClientConfiguration() {
        return this.pravegaConnectorConfig;
    }
}
