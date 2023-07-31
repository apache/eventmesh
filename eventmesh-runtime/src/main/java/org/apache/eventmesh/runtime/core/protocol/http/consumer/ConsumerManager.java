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

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.event.ConsumerGroupStateEvent;
import org.apache.eventmesh.runtime.core.consumergroup.event.ConsumerGroupTopicConfChangeEvent;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


import com.google.common.eventbus.Subscribe;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsumerManager {

    private final EventMeshHTTPServer eventMeshHTTPServer;

    /**
     * consumerGroup to ConsumerGroupManager.
     */
    private ConcurrentHashMap<String, ConsumerGroupManager> consumerTable = new ConcurrentHashMap<>(64);


    public ConsumerManager(final EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    public void init() throws Exception {
        eventMeshHTTPServer.getEventBus().register(this);
        log.info("consumerManager inited......");
    }

    public void start() throws Exception {
        log.info("consumerManager started......");
    }

    /**
     * notify ConsumerManager groupLevel
     */
    public void notifyConsumerManager(String consumerGroup, ConsumerGroupConf latestConsumerGroupConfig) throws Exception {

        ConsumerGroupManager cgm = eventMeshHTTPServer.getConsumerManager().getConsumer(consumerGroup);

        if (latestConsumerGroupConfig == null) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.setAction(ConsumerGroupStateEvent.ConsumerGroupStateAction.DELETE);
            notification.setConsumerGroup(consumerGroup);
            eventMeshHTTPServer.getEventBus().post(notification);
            return;
        }

        if (cgm == null) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.setAction(ConsumerGroupStateEvent.ConsumerGroupStateAction.NEW);
            notification.setConsumerGroup(consumerGroup);
            notification.setConsumerGroupConfig(EventMeshUtil.cloneObject(latestConsumerGroupConfig));
            eventMeshHTTPServer.getEventBus().post(notification);
            return;
        }

        if (!latestConsumerGroupConfig.equals(cgm.getConsumerGroupConfig())) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.setAction(ConsumerGroupStateEvent.ConsumerGroupStateAction.CHANGE);
            notification.setConsumerGroup(consumerGroup);
            notification.setConsumerGroupConfig(EventMeshUtil.cloneObject(latestConsumerGroupConfig));
            eventMeshHTTPServer.getEventBus().post(notification);
            return;
        }
    }

    public void shutdown() {
        eventMeshHTTPServer.getEventBus().unregister(this);
        for (ConsumerGroupManager consumerGroupManager : consumerTable.values()) {
            try {
                consumerGroupManager.shutdown();
            } catch (Exception ex) {
                log.error("shutdown consumerGroupManager[{}] err", consumerGroupManager, ex);
            }
        }
        log.info("consumerManager shutdown......");
    }

    public boolean contains(String consumerGroup) {
        return consumerTable.containsKey(consumerGroup);
    }

    /**
     * add consumer
     *
     * @param consumerGroup
     * @param consumerGroupConfig
     * @throws Exception
     */
    public synchronized void addConsumer(String consumerGroup, ConsumerGroupConf consumerGroupConfig) throws Exception {
        ConsumerGroupManager cgm = new ConsumerGroupManager(eventMeshHTTPServer, consumerGroupConfig);
        cgm.init();
        cgm.start();
        consumerTable.put(consumerGroup, cgm);
    }

    /**
     * restart consumer
     */
    public synchronized void restartConsumer(String consumerGroup, ConsumerGroupConf consumerGroupConfig) throws Exception {
        if (consumerTable.containsKey(consumerGroup)) {
            ConsumerGroupManager cgm = consumerTable.get(consumerGroup);
            cgm.refresh(consumerGroupConfig);
        }
    }

    /**
     * get consumer
     */
    public ConsumerGroupManager getConsumer(String consumerGroup) {
        return consumerTable.get(consumerGroup);
    }

    /**
     * delete consumer
     *
     * @param consumerGroup
     */
    public synchronized void delConsumer(String consumerGroup) throws Exception {
        log.info("start delConsumer with consumerGroup {}", consumerGroup);
        if (consumerTable.containsKey(consumerGroup)) {
            ConsumerGroupManager cgm = consumerTable.remove(consumerGroup);
            log.info("start unsubscribe topic with consumer group manager {}", JsonUtils.toJSONString(cgm));
            cgm.unsubscribe(consumerGroup);
            cgm.shutdown();
        }
        log.info("end delConsumer with consumerGroup {}", consumerGroup);
    }

    @Subscribe
    public void handleConsumerGroupTopicConfChangeEvent(ConsumerGroupTopicConfChangeEvent event) {
        try {
            log.info("onChange event:{}", event);
            switch (event.getAction()) {
                case NEW: {
                    ConsumerGroupManager manager = getConsumer(event.getConsumerGroup());
                    if (Objects.isNull(manager)) {
                        return;
                    }
                    manager.getConsumerGroupConfig().getConsumerGroupTopicConfMapping().put(event.getTopic(), event.getNewTopicConf());
                    break;
                }
                case CHANGE: {
                    ConsumerGroupManager manager = getConsumer(event.getConsumerGroup());
                    if (Objects.isNull(manager)) {
                        return;
                    }
                    manager.getConsumerGroupConfig().getConsumerGroupTopicConfMapping().replace(event.getTopic(), event.getNewTopicConf());
                    break;
                }
                case DELETE: {
                    ConsumerGroupManager manager = getConsumer(event.getConsumerGroup());
                    if (Objects.isNull(manager)) {
                        return;
                    }
                    manager.getConsumerGroupConfig().getConsumerGroupTopicConfMapping().remove(event.getTopic());
                    break;
                }
                default:
                    //do nothing
            }
        } catch (Exception ex) {
            log.error("onChange event:{} err", event, ex);
        }
    }

    @Subscribe
    public void handleConsumerGroupStateEvent(ConsumerGroupStateEvent event) {
        try {
            log.info("onChange event:{}", event);

            switch (event.getAction()) {
                case NEW:
                    addConsumer(event.getConsumerGroup(), event.getConsumerGroupConfig());
                    break;
                case CHANGE:
                    restartConsumer(event.getConsumerGroup(), event.getConsumerGroupConfig());
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

    public ConcurrentHashMap<String, ConsumerGroupManager> getClientTable() {
        return consumerTable;
    }
}
