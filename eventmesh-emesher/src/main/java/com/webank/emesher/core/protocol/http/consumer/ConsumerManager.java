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

package com.webank.emesher.core.protocol.http.consumer;

import com.webank.emesher.boot.ProxyHTTPServer;
import com.webank.emesher.core.consumergroup.ConsumerGroupConf;
import com.webank.emesher.core.consumergroup.event.ConsumerGroupStateEvent;
import com.webank.emesher.core.consumergroup.event.ConsumerGroupTopicConfChangeEvent;
import com.google.common.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ConsumerManager {

    private ProxyHTTPServer proxyHTTPServer;

    private ConcurrentHashMap<String /** consumerGroup */, ConsumerGroupManager> consumerTable = new ConcurrentHashMap<String, ConsumerGroupManager>();

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public ConsumerManager(ProxyHTTPServer proxyHTTPServer) {
        this.proxyHTTPServer = proxyHTTPServer;
    }

    public void init() throws Exception {
        proxyHTTPServer.getEventBus().register(this);
        logger.info("consumerManager inited......");
    }

    public void start() throws Exception {
        logger.info("consumerManager started......");
    }

    public void shutdown() {
        proxyHTTPServer.getEventBus().unregister(this);
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
    public synchronized void addConsumer(String consumerGroup, ConsumerGroupConf consumerGroupConfig) throws Exception {
        ConsumerGroupManager cgm = new ConsumerGroupManager(proxyHTTPServer, consumerGroupConfig);
        cgm.init();
        cgm.start();
        consumerTable.put(consumerGroup, cgm);
    }

    /**
     * restart consumer
     */
    public synchronized void restartConsumer(String consumerGroup, ConsumerGroupConf consumerGroupConfig) throws Exception {
        ConsumerGroupManager cgm = consumerTable.get(consumerGroup);
        cgm.refresh(consumerGroupConfig);
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
        ConsumerGroupManager cgm = consumerTable.remove(consumerGroup);
        cgm.shutdown();
    }

    @Subscribe
    public void onChange(ConsumerGroupTopicConfChangeEvent event) {
        try {
            logger.info("onChange event:{}", event);
            if (event.action == ConsumerGroupTopicConfChangeEvent.ConsumerGroupTopicConfChangeAction.NEW) {
                ConsumerGroupManager manager = getConsumer(event.consumerGroup);
                if (Objects.isNull(manager)) {
                    return;
                }
                manager.getConsumerGroupConfig().getConsumerGroupTopicConf().put(event.topic, event.newTopicConf);
                return;
            }

            if (event.action == ConsumerGroupTopicConfChangeEvent.ConsumerGroupTopicConfChangeAction.CHANGE) {
                ConsumerGroupManager manager = getConsumer(event.consumerGroup);
                if (Objects.isNull(manager)) {
                    return;
                }
                manager.getConsumerGroupConfig().getConsumerGroupTopicConf().replace(event.topic, event.newTopicConf);
                return;
            }

            if (event.action == ConsumerGroupTopicConfChangeEvent.ConsumerGroupTopicConfChangeAction.DELETE) {
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
