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

package org.apache.eventmesh.runtime.core.protocol.http.consumer;

import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsumerGroupManager {

    protected AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    protected AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);

    private EventMeshHTTPServer eventMeshHTTPServer;

    private EventMeshConsumer eventMeshConsumer;

    private ConsumerGroupConf consumerGroupConfig;

    public ConsumerGroupManager(EventMeshHTTPServer eventMeshHTTPServer, ConsumerGroupConf consumerGroupConfig) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.consumerGroupConfig = consumerGroupConfig;
        eventMeshConsumer = new EventMeshConsumer(this.eventMeshHTTPServer, this.consumerGroupConfig);
    }

    public synchronized void init() throws Exception {
        eventMeshConsumer.init();
        inited.compareAndSet(false, true);
    }

    public synchronized void start() throws Exception {
        setupEventMeshConsumer(consumerGroupConfig);
        eventMeshConsumer.start();
        started.compareAndSet(false, true);
    }

    private synchronized void setupEventMeshConsumer(ConsumerGroupConf consumerGroupConfig) throws Exception {
        for (Map.Entry<String, ConsumerGroupTopicConf> conf : consumerGroupConfig.getConsumerGroupTopicConf().entrySet()) {
            eventMeshConsumer.subscribe(conf.getKey(), conf.getValue().getSubscriptionItem());
        }
    }

    public synchronized void shutdown() throws Exception {
        eventMeshConsumer.shutdown();
        started.compareAndSet(true, false);
    }

    public synchronized void refresh(ConsumerGroupConf consumerGroupConfig) throws Exception {

        if (consumerGroupConfig == null || this.consumerGroupConfig.equals(consumerGroupConfig)) {
            return;
        }

        if (started.get()) {
            shutdown();
        }

        this.consumerGroupConfig = consumerGroupConfig;
        init();
        start();
    }

    public ConsumerGroupConf getConsumerGroupConfig() {
        return consumerGroupConfig;
    }

    public void unsubscribe(String consumerGroup) throws Exception {
        if (StringUtils.equals(consumerGroupConfig.getConsumerGroup(), consumerGroup)) {
            Set<String> topics = consumerGroupConfig.getConsumerGroupTopicConf().keySet();
            for (String topic : topics) {
                ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupConfig.getConsumerGroupTopicConf().get(topic);
                eventMeshConsumer.unsubscribe(topic, consumerGroupTopicConf.getSubscriptionItem().getMode());
            }
        }
    }
}
