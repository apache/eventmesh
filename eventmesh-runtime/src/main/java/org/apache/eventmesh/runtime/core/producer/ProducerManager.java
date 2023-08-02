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

package org.apache.eventmesh.runtime.core.producer;

import org.apache.eventmesh.common.config.CommonConfiguration;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * As a unique production manager for each server
 *
 */
@Slf4j
public class ProducerManager {

    private final CommonConfiguration commonConfiguration;

    private final ConcurrentHashMap<String /** groupName*/, EventMeshProducer> producerTable = new ConcurrentHashMap<>();

    public ProducerManager(CommonConfiguration commonConfiguration) {
        this.commonConfiguration = commonConfiguration;
    }

    public void init() throws Exception {
        log.info("producerManager inited......");
    }

    public void start() throws Exception {
        log.info("producerManager started......");
    }

    public EventMeshProducer getEventMeshProducer(ProducerGroupConf producerGroupConf) throws Exception {
        String groupName = producerGroupConf.getGroupName();

        EventMeshProducer eventMeshProducer;

        if (!producerTable.containsKey(groupName)) {
            synchronized (producerTable) {
                if (!producerTable.containsKey(groupName)) {
                    ProducerGroupConf producerGroupConfig =
                            new ProducerGroupConf(groupName, producerGroupConf.getSysId(), producerGroupConf.getToken());
                    eventMeshProducer = createEventMeshProducer(producerGroupConfig);
                    eventMeshProducer.start();
                }
            }
        }

        eventMeshProducer = producerTable.get(groupName);

        if (!eventMeshProducer.isStarted()) {
            eventMeshProducer.start();
        }

        return eventMeshProducer;
    }


    private synchronized EventMeshProducer createEventMeshProducer(ProducerGroupConf producerGroupConfig) throws Exception {
        if (producerTable.containsKey(producerGroupConfig.getGroupName())) {
            return producerTable.get(producerGroupConfig.getGroupName());
        }
        EventMeshProducer eventMeshProducer = new EventMeshProducer(producerGroupConfig, commonConfiguration);

        if (Objects.nonNull(producerGroupConfig.getSysId())) {
            eventMeshProducer.initTcp(producerGroupConfig.getSysId(), producerGroupConfig.getGroupName());
        } else {
            eventMeshProducer.init();
        }

        producerTable.put(producerGroupConfig.getGroupName(), eventMeshProducer);
        return eventMeshProducer;
    }

    public void shutdown() {
        for (EventMeshProducer eventMeshProducer : producerTable.values()) {
            try {
                eventMeshProducer.shutdown();
            } catch (Exception ex) {
                log.error("shutdown eventMeshProducer[{}] err", eventMeshProducer, ex);
            }
        }
        log.info("producerManager shutdown......");
    }

}
