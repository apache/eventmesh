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

package cn.webank.defibus.broker.consumequeue;

import cn.webank.defibus.broker.DeFiBrokerController;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.rocketmq.common.constant.LoggerName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientRebalanceResultManager {
    private final DeFiBrokerController deFiBrokerController;
    private static final Logger log = LoggerFactory.getLogger(LoggerName.REBALANCE_LOCK_LOGGER_NAME);

    private final ConcurrentHashMap<String/*Group*/, ConcurrentHashMap<String/*Topic*/, ConcurrentHashMap<Integer/*queueId*/, String>>> clientListenMap
        = new ConcurrentHashMap<>();

    public ClientRebalanceResultManager(DeFiBrokerController deFiBrokerController) {
        this.deFiBrokerController = deFiBrokerController;
    }

    public void updateListenMap(String group, String topic, int queueId, String clientId) {
        ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> groupMap = clientListenMap.get(group);
        if (groupMap == null) {
            groupMap = new ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>>();
            clientListenMap.put(group, groupMap);
        }
        ConcurrentHashMap<Integer, String> topicMap = groupMap.get(topic);
        if (topicMap == null) {
            topicMap = new ConcurrentHashMap<>();
            groupMap.put(topic, topicMap);
        }
        String old = topicMap.put(queueId, clientId);
        if (!clientId.equals(old)) {
            log.info("update client listen map. new: {} old: {} {} {} {}",
                clientId, old, group, topic, queueId);
        }
    }

    public HashMap<Integer, String> getTopicListenMap(String group, String topic) {
        HashMap<Integer, String> listenMap = new HashMap<>();
        if (clientListenMap.get(group) != null && clientListenMap.get(group).get(topic) != null) {
            ConcurrentHashMap<Integer, String> queueMap = clientListenMap.get(group).get(topic);
            for (Integer queueId : queueMap.keySet()) {
                listenMap.put(queueId, queueMap.get(queueId));
            }
        }
        return listenMap;
    }
}
