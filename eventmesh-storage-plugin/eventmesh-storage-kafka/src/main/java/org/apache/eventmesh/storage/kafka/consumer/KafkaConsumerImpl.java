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

package org.apache.eventmesh.storage.kafka.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.storage.kafka.config.ClientConfiguration;

import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

@Config(field = "clientConfiguration")
public class KafkaConsumerImpl implements Consumer {

    private ConsumerImpl consumer;

    /**
     * Unified configuration class corresponding to kafka-client.properties
     */
    private ClientConfiguration clientConfiguration;

    @Override
    public synchronized void init(Properties props) {
        String namesrvAddr = clientConfiguration.getNamesrvAddr();
        String consumerGroup = props.getProperty("consumerGroup");

        // Other config props
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, namesrvAddr);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        consumer = new ConsumerImpl(props);
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        consumer.updateOffset(cloudEvents, context);
    }

    @Override
    public void subscribe(String topic) {
        consumer.subscribe(topic);
    }

    @Override
    public boolean isStarted() {
        return consumer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return consumer.isClosed();
    }

    @Override
    public synchronized void start() {
        consumer.start();
    }

    @Override
    public void unsubscribe(String topic) {
        consumer.unsubscribe(topic);
    }

    @Override
    public void registerEventListener(EventListener listener) {
        consumer.registerEventListener(listener);
    }

    @Override
    public synchronized void shutdown() {
        consumer.shutdown();
    }

    public ClientConfiguration getClientConfiguration() {
        return this.clientConfiguration;
    }
}
