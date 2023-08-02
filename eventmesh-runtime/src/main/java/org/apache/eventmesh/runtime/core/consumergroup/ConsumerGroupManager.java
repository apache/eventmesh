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

package org.apache.eventmesh.runtime.core.consumergroup;

import org.apache.eventmesh.runtime.core.consumer.EventMeshConsumer;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * wrapping enhances the EventMeshConsumer
 *
 */
public class ConsumerGroupManager {

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final AtomicBoolean inited = new AtomicBoolean(false);

    private final EventMeshConsumer eventMeshConsumer;

    private ConsumerGroupConf consumerGroupConfig;

    public ConsumerGroupManager(EventMeshConsumer eventMeshConsumer) {
        this.eventMeshConsumer = eventMeshConsumer;
        this.consumerGroupConfig = eventMeshConsumer.getConsumerGroupConf();
    }

    public void init() throws Exception {
        if (!inited.compareAndSet(false, true)) {
            return;
        }

        // todo: Avoid relying on different servers?
        if (Objects.nonNull(eventMeshConsumer.getEventMeshHTTPServer())) {
            eventMeshConsumer.init();
        } else {
            eventMeshConsumer.initTcp();
        }
    }

    public void start() throws Exception {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        setupEventMeshConsumer(consumerGroupConfig);
        eventMeshConsumer.start();
    }

    /**
     * Make the EventMeshConsumer register the corresponding item
     */
    private synchronized void setupEventMeshConsumer(ConsumerGroupConf consumerGroupConfig) throws Exception {
        for (Map.Entry<String, ConsumerGroupTopicConf> conf : consumerGroupConfig.getConsumerGroupTopicConfMapping().entrySet()) {
            eventMeshConsumer.subscribe(conf.getValue().getSubscriptionItem());
        }
    }

    public void shutdown() throws Exception {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        eventMeshConsumer.shutdown();
    }

    public synchronized void refresh(final ConsumerGroupConf consumerGroupConfig) throws Exception {

        if (consumerGroupConfig == null || this.consumerGroupConfig.equals(consumerGroupConfig)) {
            return;
        }

        if (started.get()) {
            shutdown();
        }

        this.consumerGroupConfig = consumerGroupConfig;
        this.eventMeshConsumer.setConsumerGroupConf(consumerGroupConfig);
        init();
        start();
    }

    public void unsubscribe(String consumerGroup) throws Exception {
        if (!StringUtils.equals(consumerGroupConfig.getGroupName(), consumerGroup)) {
            return;
        }

        Set<String> topics = consumerGroupConfig.getConsumerGroupTopicConfMapping().keySet();
        for (String topic : topics) {
            ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupConfig.getConsumerGroupTopicConfMapping().get(topic);
            eventMeshConsumer.unsubscribe(consumerGroupTopicConf.getSubscriptionItem());
        }

    }

    public ConsumerGroupConf getConsumerGroupConfig() {
        return consumerGroupConfig;
    }
}
