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

package org.apache.eventmesh.runtime.core.protocol.http.producer;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.consumergroup.ProducerGroupConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerManager {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private EventMeshHTTPServer eventMeshHTTPServer;

    private ConcurrentHashMap<String /** groupName*/, EventMeshProducer> producerTable = new ConcurrentHashMap<String, EventMeshProducer>();

    public ProducerManager(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    public void init() throws Exception {
        logger.info("producerManager inited......");
    }

    public void start() throws Exception {
        logger.info("producerManager started......");
    }

    public EventMeshProducer getEventMeshProducer(String producerGroup) throws Exception {
        EventMeshProducer eventMeshProducer = null;
        if (!producerTable.containsKey(producerGroup)) {
            synchronized (producerTable) {
                if (!producerTable.containsKey(producerGroup)) {
                    ProducerGroupConf producerGroupConfig = new ProducerGroupConf(producerGroup);
                    eventMeshProducer = createEventMeshProducer(producerGroupConfig);
                    eventMeshProducer.start();
                }
            }
        }

        eventMeshProducer = producerTable.get(producerGroup);

        if (!eventMeshProducer.getStarted().get()) {
            eventMeshProducer.start();
        }

        return eventMeshProducer;
    }

    public synchronized EventMeshProducer createEventMeshProducer(ProducerGroupConf producerGroupConfig) throws Exception {
        if (producerTable.containsKey(producerGroupConfig.getGroupName())) {
            return producerTable.get(producerGroupConfig.getGroupName());
        }
        EventMeshProducer eventMeshProducer = new EventMeshProducer();
        eventMeshProducer.init(producerGroupConfig);
        producerTable.put(producerGroupConfig.getGroupName(), eventMeshProducer);
        return eventMeshProducer;
    }

    public void shutdown() {
        for (EventMeshProducer eventMeshProducer : producerTable.values()) {
            try {
                eventMeshProducer.shutdown();
            } catch (Exception ex) {
                logger.error("shutdown eventMeshProducer[{}] err", eventMeshProducer, ex);
            }
        }
        logger.info("producerManager shutdown......");
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public ConcurrentHashMap<String, EventMeshProducer> getProducerTable() {
        return producerTable;
    }
}
