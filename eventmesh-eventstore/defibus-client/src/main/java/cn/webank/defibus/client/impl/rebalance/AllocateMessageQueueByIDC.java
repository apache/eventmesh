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

package cn.webank.defibus.client.impl.rebalance;

import cn.webank.defibus.common.DeFiBusConstant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.client.consumer.AllocateMessageQueueStrategy;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.logging.InternalLogger;

public class AllocateMessageQueueByIDC implements AllocateMessageQueueStrategy {
    private static final InternalLogger log = ClientLogger.getLog();
    private MQClientInstance mqClientInstance = null;
    private static final String UNKNOWN_IDC = "UNKNOWN_IDC";

    @Override
    public String getName() {
        return "IDC_NEARBY";
    }

    @Override
    public List<MessageQueue> allocate(String consumerGroup, String currentCID, List<MessageQueue> mqAll,
        List<String> cidAll) {
        List<MessageQueue> result = new ArrayList<MessageQueue>();
        if (currentCID == null || currentCID.length() < 1) {
            throw new IllegalArgumentException("currentCID is empty");
        }
        if (mqAll == null || mqAll.isEmpty()) {
            log.info("[IGNORE] doRebalance.allocate, mqAll is empty");
            return result;
        }
        if (cidAll == null || cidAll.isEmpty()) {
            log.info("[IGNORE] doRebalance.allocate, cidAll is empty");
            return result;
        }

        if (!cidAll.contains(currentCID)) {
            log.info("[BUG] ConsumerGroup: {} The consumerId: {} not in cidAll: {}",
                consumerGroup,
                currentCID,
                cidAll);
            return result;
        }

        {
            log.debug("doRebalance: consumerGroup: {} currentCID: {}", consumerGroup, currentCID);
            log.debug("mqAll:" + mqAll);
            if (mqAll != null) {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < mqAll.size(); i++) {
                    sb.append("MQ#" + i + ":" + mqAll.get(i) + " ");
                }
                log.debug(sb.toString());
            }
            log.debug("cidAll:" + cidAll);
            if (cidAll != null) {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < cidAll.size(); i++) {
                    sb.append("CID#" + i + ":" + cidAll.get(i) + " ");
                }
                log.debug(sb.toString());
            }
        }

        /**
         * step1: seperate mqs and cids by idc
         */
        Map<String /*idc*/, List<MessageQueue>> sepMqs = this.seperateMqsByIDC(mqAll);
        Map<String /*idc*/, List<String/*cid*/>> sepClients = this.seperateCidsByIDC(cidAll);

        /**
         * step2: allocate local mqs first
         */
        String clusterPrefix = extractIdcFromClientId(currentCID);
        if (clusterPrefix != null) {
            List<MessageQueue> nearbyMqs = sepMqs.get(clusterPrefix);
            List<String> nearbyCids = sepClients.get(clusterPrefix);

            if (nearbyMqs != null && nearbyCids != null && !nearbyMqs.isEmpty() && !nearbyCids.isEmpty()) {
                Collections.sort(nearbyCids);
                Collections.sort(nearbyMqs);
                int index = nearbyCids.indexOf(currentCID);
                for (int i = index; i < nearbyMqs.size(); i++) {
                    if (i % nearbyCids.size() == index) {
                        result.add(nearbyMqs.get(i));
                    }
                }
            }
        }

        /**
         * step3: allocate mqs which no subscriber in the same idc
         */
        List<MessageQueue> mqsNoClientsInSameIdc = new ArrayList<>();
        for (String idc : sepMqs.keySet()) {
            if (!idc.equals(UNKNOWN_IDC) && (sepClients.get(idc) == null || sepClients.get(idc).isEmpty())) {
                mqsNoClientsInSameIdc.addAll(sepMqs.get(idc));
            }
        }
        if (!mqsNoClientsInSameIdc.isEmpty()) {
            Collections.sort(mqsNoClientsInSameIdc);
            Collections.sort(cidAll);
            int index = cidAll.indexOf(currentCID);
            for (int i = index; i < mqsNoClientsInSameIdc.size(); i++) {
                if (i % cidAll.size() == index) {
                    result.add(mqsNoClientsInSameIdc.get(i));
                }
            }
        }

        /**
         * step4: allocate mqs which no matched any cluster and cannot determined idc.
         */
        if (sepMqs.get(UNKNOWN_IDC) != null && !sepMqs.get(UNKNOWN_IDC).isEmpty()) {
            log.warn("doRebalance: cannot determine idc of mqs. allocate all to myself. {}", sepMqs.get(UNKNOWN_IDC));
            result.addAll(sepMqs.get(UNKNOWN_IDC));
        }
        return result;
    }

    private Map<String, List<MessageQueue>> seperateMqsByIDC(List<MessageQueue> mqAll) {
        String topic = mqAll.get(0).getTopic();
        TopicRouteData topicRouteData = mqClientInstance.getTopicRouteTable().get(topic);
        if (topicRouteData == null) {
            mqClientInstance.updateTopicRouteInfoFromNameServer(topic);
            topicRouteData = mqClientInstance.getTopicRouteTable().get(topic);
        }

        HashMap<String/*brokerName*/, String/*idc*/> brokerIdcMap = new HashMap<>();
        ArrayList<BrokerData> brokerDatas = new ArrayList<>(topicRouteData.getBrokerDatas());
        for (BrokerData broker : brokerDatas) {
            String clusterName = broker.getCluster();
            String idc = clusterName.split(DeFiBusConstant.IDC_SEPERATER)[0];
            brokerIdcMap.put(broker.getBrokerName(), idc.toUpperCase());
        }

        Map<String/*IDC*/, List<MessageQueue>> result = new HashMap<>();
        for (MessageQueue mq : mqAll) {
            String idc = brokerIdcMap.get(mq.getBrokerName());
            if (idc == null) {
                idc = UNKNOWN_IDC;
            }
            if (result.get(idc) == null) {
                List<MessageQueue> mqList = new ArrayList<>();
                mqList.add(mq);
                result.put(idc, mqList);
            } else {
                result.get(idc).add(mq);
            }
        }
        return result;
    }

    private Map<String, List<String>> seperateCidsByIDC(List<String> cidAll) {
        Map<String/* idc */, List<String>> result = new HashMap<>();
        for (String cid : cidAll) {
            String cidIdc = extractIdcFromClientId(cid);
            if (cidIdc != null) {
                cidIdc = cidIdc.toUpperCase();
                if (result.get(cidIdc) != null) {
                    result.get(cidIdc).add(cid);
                } else {
                    List<String> cidList = new ArrayList<>();
                    cidList.add(cid);
                    result.put(cidIdc, cidList);
                }
            }
        }
        return result;
    }

    private String extractIdcFromClientId(final String cid) {
        if (cid != null) {
            String[] cidArr = cid.split(DeFiBusConstant.INSTANCE_NAME_SEPERATER);
            if (cidArr.length > 2) {
                return cidArr[cidArr.length - 1];
            }
        }
        return null;
    }

    public void setMqClientInstance(MQClientInstance mqClientInstance) {
        this.mqClientInstance = mqClientInstance;
    }
}
