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

import org.apache.eventmesh.api.meta.bo.EventMeshServicePubTopicInfo;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.runtime.boot.EventMeshServer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProducerTopicManager {

    private final EventMeshServer eventMeshServer;

    private transient ScheduledFuture<?> scheduledTask;

    protected static ScheduledExecutorService scheduler;

    private final ConcurrentHashMap<String, EventMeshServicePubTopicInfo> eventMeshServicePubTopicInfoMap = new ConcurrentHashMap<>(64);

    public ProducerTopicManager(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;
    }

    public void init() {
        scheduler = ThreadPoolFactory.createScheduledExecutor(Runtime.getRuntime().availableProcessors(),
            new EventMeshThreadFactory("Producer-Topic-Manager", true));
        log.info("ProducerTopicManager inited......");
    }

    public void start() {

        if (scheduledTask == null) {
            synchronized (ProducerTopicManager.class) {
                scheduledTask = scheduler.scheduleAtFixedRate(() -> {
                    try {
                        if (!eventMeshServer.getConfiguration().isEventMeshServerRegistryEnable()) {
                            return;
                        }
                        List<EventMeshServicePubTopicInfo> pubTopicInfoList = eventMeshServer.getMetaStorage().findEventMeshServicePubTopicInfos();
                        Optional.ofNullable(pubTopicInfoList)
                            .ifPresent(lt -> lt.forEach(item -> eventMeshServicePubTopicInfoMap.put(item.getService(), item)));
                    } catch (Exception e) {
                        log.error("ProducerTopicManager update eventMesh pub topic info error. ", e);
                    }

                }, 5, 20, TimeUnit.SECONDS);
            }
        }
        log.info("ProducerTopicManager started......");
    }

    public void shutdown() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        log.info("ProducerTopicManager shutdown......");
    }

    public ConcurrentHashMap<String, EventMeshServicePubTopicInfo> getEventMeshServicePubTopicInfoMap() {
        return eventMeshServicePubTopicInfoMap;
    }

    public EventMeshServicePubTopicInfo getEventMeshServicePubTopicInfo(String producerGroup) {
        return eventMeshServicePubTopicInfoMap.get(producerGroup);
    }


}
