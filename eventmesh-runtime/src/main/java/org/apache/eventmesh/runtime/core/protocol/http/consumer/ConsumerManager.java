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
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.consumergroup.event.ConsumerGroupStateEvent;
import org.apache.eventmesh.runtime.core.consumergroup.event.ConsumerGroupTopicConfChangeEvent;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;

public class ConsumerManager {

    private EventMeshHTTPServer eventMeshHTTPServer;

    /**
     * consumerGroup to ConsumerGroupManager.
     */
    private ConcurrentHashMap<String, ConsumerGroupManager> consumerTable =
            new ConcurrentHashMap<>();

    private static final int DEFAULT_UPDATE_TIME = 3 * 30 * 1000;

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private ScheduledExecutorService scheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    public ConsumerManager(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    public void init() throws Exception {
        eventMeshHTTPServer.getEventBus().register(this);
        logger.info("consumerManager inited......");
    }

    public void start() throws Exception {
        logger.info("consumerManager started......");

        //        scheduledExecutorService.scheduleAtFixedRate(() -> {
        //            logger.info("clientInfo check start.....");
        //            synchronized (eventMeshHTTPServer.localClientInfoMapping) {
        //                Map<String, List<Client>> clientInfoMap =
        //                    eventMeshHTTPServer.localClientInfoMapping;
        //                if (clientInfoMap.size() > 0) {
        //                    for (String key : clientInfoMap.keySet()) {
        //                        String consumerGroup = key.split("@")[0];
        //                        String topic = key.split("@")[1];
        //                        List<Client> clientList = clientInfoMap.get(key);
        //                        Iterator<Client> clientIterator = clientList.iterator();
        //                        boolean isChange = false;
        //                        while (clientIterator.hasNext()) {
        //                            Client client = clientIterator.next();
        //                            //The time difference is greater than 3 heartbeat cycles
        //                            if (System.currentTimeMillis() - client.lastUpTime.getTime()
        //                                > DEFAULT_UPDATE_TIME) {
        //                                logger.warn(
        //                                    "client {} lastUpdate time {} over three heartbeat cycles",
        //                                    JsonUtils.serialize(client), client.lastUpTime);
        //                                clientIterator.remove();
        //                                isChange = true;
        //                            }
        //                        }
        //                        if (isChange) {
        //                            if (clientList.size() > 0) {
        //                                //change url
        //                                logger.info("consumerGroup {} client info changing",
        //                                    consumerGroup);
        //                                Map<String, List<String>> idcUrls = new HashMap<>();
        //                                Set<String> clientUrls = new HashSet<>();
        //                                for (Client client : clientList) {
        //                                    clientUrls.add(client.url);
        //                                    if (idcUrls.containsKey(client.idc)) {
        //                                        idcUrls.get(client.idc)
        //                                            .add(StringUtils.deleteWhitespace(client.url));
        //                                    } else {
        //                                        List<String> urls = new ArrayList<>();
        //                                        urls.add(client.url);
        //                                        idcUrls.put(client.idc, urls);
        //                                    }
        //                                }
        //                                synchronized (eventMeshHTTPServer.localConsumerGroupMapping) {
        //                                    ConsumerGroupConf consumerGroupConf =
        //                                        eventMeshHTTPServer.localConsumerGroupMapping
        //                                            .get(consumerGroup);
        //                                    Map<String, ConsumerGroupTopicConf> map =
        //                                        consumerGroupConf.getConsumerGroupTopicConf();
        //                                    for (String topicKey : map.keySet()) {
        //                                        if (StringUtils.equals(topic, topicKey)) {
        //                                            ConsumerGroupTopicConf latestTopicConf =
        //                                                new ConsumerGroupTopicConf();
        //                                            latestTopicConf.setConsumerGroup(consumerGroup);
        //                                            latestTopicConf.setTopic(topic);
        //                                            latestTopicConf.setSubscriptionItem(
        //                                                map.get(topicKey).getSubscriptionItem());
        //                                            latestTopicConf.setUrls(clientUrls);
        //
        //                                            latestTopicConf.setIdcUrls(idcUrls);
        //
        //                                            map.put(topic, latestTopicConf);
        //                                        }
        //                                    }
        //                                    eventMeshHTTPServer.localConsumerGroupMapping
        //                                        .put(consumerGroup, consumerGroupConf);
        //                                    logger.info(
        //                                        "consumerGroup {} client info changed, "
        //                                            + "consumerGroupConf {}", consumerGroup,
        //                                        JsonUtils.serialize(consumerGroupConf));
        //
        //                                    try {
        //                                        notifyConsumerManager(consumerGroup, consumerGroupConf);
        //                                    } catch (Exception e) {
        //                                        logger.error("notifyConsumerManager error", e);
        //                                    }
        //                                }
        //
        //                            } else {
        //                                logger.info("consumerGroup {} client info removed",
        //                                    consumerGroup);
        //                                //remove
        //                                try {
        //                                    notifyConsumerManager(consumerGroup, null);
        //                                } catch (Exception e) {
        //                                    logger.error("notifyConsumerManager error", e);
        //                                }
        //
        //                                eventMeshHTTPServer.localConsumerGroupMapping.keySet()
        //                                    .removeIf(s -> StringUtils.equals(consumerGroup, s));
        //                            }
        //                        }
        //
        //                    }
        //                }
        //            }
        //        }, 10000, 10000, TimeUnit.MILLISECONDS);
        //TODO: update the subscription periodically from registry
    }

    /**
     * notify ConsumerManager groupLevel
     */
    public void notifyConsumerManager(String consumerGroup,
                                      ConsumerGroupConf latestConsumerGroupConfig)
            throws Exception {
        ConsumerGroupManager cgm =
                eventMeshHTTPServer.getConsumerManager().getConsumer(consumerGroup);
        if (latestConsumerGroupConfig == null) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.action = ConsumerGroupStateEvent.ConsumerGroupStateAction.DELETE;
            notification.consumerGroup = consumerGroup;
            eventMeshHTTPServer.getEventBus().post(notification);
            return;
        }

        if (cgm == null) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.action = ConsumerGroupStateEvent.ConsumerGroupStateAction.NEW;
            notification.consumerGroup = consumerGroup;
            notification.consumerGroupConfig = EventMeshUtil.cloneObject(latestConsumerGroupConfig);
            eventMeshHTTPServer.getEventBus().post(notification);
            return;
        }

        if (!latestConsumerGroupConfig.equals(cgm.getConsumerGroupConfig())) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.action = ConsumerGroupStateEvent.ConsumerGroupStateAction.CHANGE;
            notification.consumerGroup = consumerGroup;
            notification.consumerGroupConfig = EventMeshUtil.cloneObject(latestConsumerGroupConfig);
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
                logger.error("shutdown consumerGroupManager[{}] err", consumerGroupManager, ex);
            }
        }
        logger.info("consumerManager shutdown......");
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
    public synchronized void addConsumer(String consumerGroup,
                                         ConsumerGroupConf consumerGroupConfig) throws Exception {
        ConsumerGroupManager cgm =
                new ConsumerGroupManager(eventMeshHTTPServer, consumerGroupConfig);
        cgm.init();
        cgm.start();
        consumerTable.put(consumerGroup, cgm);
    }

    /**
     * restart consumer
     */
    public synchronized void restartConsumer(String consumerGroup,
                                             ConsumerGroupConf consumerGroupConfig)
            throws Exception {
        if (consumerTable.containsKey(consumerGroup)) {
            ConsumerGroupManager cgm = consumerTable.get(consumerGroup);
            cgm.refresh(consumerGroupConfig);
        }
    }

    /**
     * get consumer
     */
    public ConsumerGroupManager getConsumer(String consumerGroup) throws Exception {
        ConsumerGroupManager cgm = consumerTable.get(consumerGroup);
        return cgm;
    }

    /**
     * delete consumer
     *
     * @param consumerGroup
     */
    public synchronized void delConsumer(String consumerGroup) throws Exception {
        logger.info("start delConsumer with consumerGroup {}", consumerGroup);
        if (consumerTable.containsKey(consumerGroup)) {
            ConsumerGroupManager cgm = consumerTable.remove(consumerGroup);
            logger.info("start unsubscribe topic with consumer group manager {}",
                    JsonUtils.serialize(cgm));
            cgm.unsubscribe(consumerGroup);
            cgm.shutdown();
        }
        logger.info("end delConsumer with consumerGroup {}", consumerGroup);
    }

    @Subscribe
    public void onChange(ConsumerGroupTopicConfChangeEvent event) {
        try {
            logger.info("onChange event:{}", event);
            if (event.action
                    == ConsumerGroupTopicConfChangeEvent.ConsumerGroupTopicConfChangeAction.NEW) {
                ConsumerGroupManager manager = getConsumer(event.consumerGroup);
                if (Objects.isNull(manager)) {
                    return;
                }
                manager.getConsumerGroupConfig().getConsumerGroupTopicConf()
                        .put(event.topic, event.newTopicConf);
                return;
            }

            if (event.action
                    == ConsumerGroupTopicConfChangeEvent.ConsumerGroupTopicConfChangeAction.CHANGE) {
                ConsumerGroupManager manager = getConsumer(event.consumerGroup);
                if (Objects.isNull(manager)) {
                    return;
                }
                manager.getConsumerGroupConfig().getConsumerGroupTopicConf()
                        .replace(event.topic, event.newTopicConf);
                return;
            }

            if (event.action
                    == ConsumerGroupTopicConfChangeEvent.ConsumerGroupTopicConfChangeAction.DELETE) {
                ConsumerGroupManager manager = getConsumer(event.consumerGroup);
                if (Objects.isNull(manager)) {
                    return;
                }
                manager.getConsumerGroupConfig().getConsumerGroupTopicConf().remove(event.topic);
                return;
            }
        } catch (Exception ex) {
            logger.error("onChange event:{} err", event, ex);
        }
    }

    @Subscribe
    public void onChange(ConsumerGroupStateEvent event) {
        try {
            logger.info("onChange event:{}", event);
            if (event.action == ConsumerGroupStateEvent.ConsumerGroupStateAction.NEW) {
                addConsumer(event.consumerGroup, event.consumerGroupConfig);
                return;
            }

            if (event.action == ConsumerGroupStateEvent.ConsumerGroupStateAction.CHANGE) {
                restartConsumer(event.consumerGroup, event.consumerGroupConfig);
                return;
            }

            if (event.action == ConsumerGroupStateEvent.ConsumerGroupStateAction.DELETE) {
                delConsumer(event.consumerGroup);
                return;
            }
        } catch (Exception ex) {
            logger.error("onChange event:{} err", event, ex);
        }
    }
}
