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

package org.apache.eventmesh.runtime.core.consumer;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupManager;
import org.apache.eventmesh.runtime.core.consumergroup.event.ConsumerGroupTopicConfUpdateEvent;
import org.apache.eventmesh.runtime.core.consumergroup.event.ConsumerGroupUpdateEvent;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import lombok.extern.slf4j.Slf4j;

/**
 * As a unique consumption manager for each server
 */
@Slf4j
public class ConsumerManager {

    private EventMeshHTTPServer eventMeshHTTPServer;
    private EventMeshTCPServer eventMeshTCPServer;
    private final EventBus eventBus;
    /**
     * consumerGroup to ConsumerGroupManager.
     */
    private final ConcurrentHashMap<String, ConsumerGroupManager> consumerTable = new ConcurrentHashMap<>(64);

    public ConsumerManager(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.eventBus = eventMeshHTTPServer.getEventBus();
    }

    public ConsumerManager(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.eventBus = eventMeshTCPServer.getEventBus();
    }

    public void init() throws Exception {
        eventBus.register(this);
        log.info("consumerManager inited......");
    }

    public void start() throws Exception {
        log.info("consumerManager started......");
    }

    public void shutdown() {
        eventBus.unregister(this);
        for (ConsumerGroupManager consumerGroupManager : consumerTable.values()) {
            try {
                consumerGroupManager.shutdown();
            } catch (Exception ex) {
                log.error("shutdown consumerGroupManager[{}] err", consumerGroupManager, ex);
            }
        }
        log.info("consumerManager shutdown......");
    }

    /**
     * notify ConsumerManager groupLevel
     *
     * @see #handleConsumerGroupUpdateEvent
     */
    public void notifyConsumerManager(String consumerGroup, ConsumerGroupConf latestConsumerGroupConfig) throws Exception {

        ConsumerGroupManager cgm = getConsumer(consumerGroup);

        if (latestConsumerGroupConfig == null) {
            ConsumerGroupUpdateEvent notification = new ConsumerGroupUpdateEvent();
            notification.setAction(ConsumerGroupUpdateEvent.ConsumerGroupUpdateAction.DELETE);
            notification.setConsumerGroup(consumerGroup);
            eventBus.post(notification);
            return;
        }

        if (cgm == null) {
            ConsumerGroupUpdateEvent notification = new ConsumerGroupUpdateEvent();
            notification.setAction(ConsumerGroupUpdateEvent.ConsumerGroupUpdateAction.NEW);
            notification.setConsumerGroup(consumerGroup);
            notification.setNewConsumerGroupConfig(EventMeshUtil.cloneObject(latestConsumerGroupConfig));
            eventBus.post(notification);
            return;
        }

        if (!latestConsumerGroupConfig.equals(cgm.getConsumerGroupConfig())) {
            ConsumerGroupUpdateEvent notification = new ConsumerGroupUpdateEvent();
            notification.setAction(ConsumerGroupUpdateEvent.ConsumerGroupUpdateAction.CHANGE);
            notification.setConsumerGroup(consumerGroup);
            notification.setNewConsumerGroupConfig(EventMeshUtil.cloneObject(latestConsumerGroupConfig));
            eventBus.post(notification);
        }
    }

    @Subscribe
    public void handleConsumerGroupTopicConfUpdateEvent(ConsumerGroupTopicConfUpdateEvent event) {
        try {
            log.info("onChange event:{}", event);

            ConsumerGroupManager manager = getConsumer(event.getConsumerGroup());
            if (Objects.isNull(manager)) {
                return;
            }

            switch (event.getAction()) {
                case NEW:
                    manager.getConsumerGroupConfig()
                            .getConsumerGroupTopicConfMapping().put(event.getTopic(), event.getNewTopicConf());
                    break;
                case CHANGE:
                    manager.getConsumerGroupConfig()
                            .getConsumerGroupTopicConfMapping().replace(event.getTopic(), event.getNewTopicConf());
                    break;
                case DELETE:
                    manager.getConsumerGroupConfig()
                            .getConsumerGroupTopicConfMapping().remove(event.getTopic());
                    break;
                default:
                    //do nothing
            }
        } catch (Exception ex) {
            log.error("onChange event:{} err", event, ex);
        }
    }

    @Subscribe
    public void handleConsumerGroupUpdateEvent(ConsumerGroupUpdateEvent event) {
        try {
            log.info("onChange event:{}", event);

            switch (event.getAction()) {
                case NEW:
                    addConsumer(event.getConsumerGroup(), event.getNewConsumerGroupConfig());
                    break;
                case CHANGE:
                    restartConsumer(event.getConsumerGroup(), event.getNewConsumerGroupConfig());
                    break;
                case DELETE:
                    delConsumer(event.getConsumerGroup());
                    break;
                default:
                    //do nothing
            }
        } catch (Exception ex) {
            log.error("onChange event:{} err", event, ex);
        }
    }

    /**
     * add consumer
     */
    private synchronized void addConsumer(String consumerGroup, ConsumerGroupConf consumerGroupConfig) throws Exception {
        ConsumerGroupManager cgm;

        // todo: Avoid relying on different servers?
        if (Objects.nonNull(eventMeshHTTPServer)) {
            cgm = new ConsumerGroupManager(new EventMeshConsumer(eventMeshHTTPServer, consumerGroupConfig));
        } else {
            cgm = new ConsumerGroupManager(new EventMeshConsumer(eventMeshTCPServer, consumerGroupConfig));
        }

        cgm.init();
        cgm.start();
        consumerTable.put(consumerGroup, cgm);
    }

    /**
     * restart consumer
     */
    private synchronized void restartConsumer(String consumerGroup, ConsumerGroupConf consumerGroupConfig) throws Exception {
        if (consumerTable.containsKey(consumerGroup)) {
            ConsumerGroupManager cgm = consumerTable.get(consumerGroup);
            cgm.refresh(consumerGroupConfig);
        }
    }

    /**
     * delete consumer
     */
    private synchronized void delConsumer(String consumerGroup) throws Exception {
        log.info("start delConsumer with consumerGroup {}", consumerGroup);
        if (consumerTable.containsKey(consumerGroup)) {
            ConsumerGroupManager cgm = consumerTable.remove(consumerGroup);
            log.info("start unsubscribe topic with consumer group manager {}", JsonUtils.toJSONString(cgm));
            cgm.unsubscribe(consumerGroup);
            cgm.shutdown();
        }
        log.info("end delConsumer with consumerGroup {}", consumerGroup);
    }


    public boolean contains(String consumerGroup) {
        return consumerTable.containsKey(consumerGroup);
    }

    /**
     * get consumer
     */
    private ConsumerGroupManager getConsumer(String consumerGroup) {
        return consumerTable.get(consumerGroup);
    }

}
