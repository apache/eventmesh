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
import cn.webank.defibus.broker.DeFiBrokerPathConfigHelper;
import com.alibaba.fastjson.JSON;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.ConfigManager;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageRedirectManager {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
    private final ConcurrentHashMap<String/*topic*/, ConcurrentHashMap<String/*flag*/, RedirectConfItem>> redirectMap = new ConcurrentHashMap<>();
    private final DeFiBrokerController deFiBrokerController;
    private Random random = new Random();
    RedirectConfigManager configManager;

    public MessageRedirectManager(DeFiBrokerController deFiBrokerController) {
        this.deFiBrokerController = deFiBrokerController;
        configManager = new RedirectConfigManager();
        configManager.load();
    }

    public void shutdown() {
        if (configManager != null) {
            configManager.persist();
        }
    }

    public RedirectResult redirectMessageToWhichQueue(SendMessageRequestHeader requestHeader, String flag) {
        if (hasRedirectConfig(flag, requestHeader.getTopic())) {
            ArrayList<Integer> grayQueueId = findCandidateQueueId(flag, requestHeader.getTopic());
            if (grayQueueId.size() > 0) {
                int redirectQueueId = grayQueueId.get(Math.abs(random.nextInt()) % grayQueueId.size());
                return new RedirectResult(RedirectStates.REDIRECT_OK, redirectQueueId);
            } else {
                return new RedirectResult(RedirectStates.NO_INSTANCE_FOUND, -1);
            }
        } else {
            return new RedirectResult(RedirectStates.NO_REDIRECT_CONFIG, -1);
        }
    }

    private boolean hasRedirectConfig(String flag, String topic) {
        if (flag != null && topic != null) {
            return redirectMap.get(topic) != null
                && redirectMap.get(topic).get(flag) != null
                && redirectMap.get(topic).get(flag).getIps().size() > 0;
        }
        return false;
    }

    private ArrayList<Integer> findCandidateQueueId(String flag, String topic) {
        ArrayList<Integer> candidateQueueId = new ArrayList<>();
        if (flag != null) {
            ConcurrentHashMap<String/*flag*/, RedirectConfItem> flagMap = redirectMap.get(topic);
            if (flagMap != null) {
                RedirectConfItem confItems = flagMap.get(flag);
                if (confItems != null) {
                    String groupName = confItems.getConsumerGroup();
                    HashMap<Integer/*queueId*/, String/*clientId*/> cidMap = deFiBrokerController.getClientRebalanceResultManager().getTopicListenMap(groupName, topic);
                    for (Integer qId : cidMap.keySet()) {
                        String cid = cidMap.get(qId);
                        if (StringUtils.isNotEmpty(cid)) {
                            String ip = StringUtils.split(cid, "@")[0];
                            if (confItems.getIps().contains(ip)) {
                                candidateQueueId.add(qId);
                            }
                        }
                    }
                }
            }
        }
        return candidateQueueId;
    }

    public void updateConfigs(List<RedirectConfItem> list) {
        List<RedirectConfItem> old = getConfigList();
        for (RedirectConfItem item : list) {
            ConcurrentHashMap<String, RedirectConfItem> flagMap = redirectMap.get(item.getTopic());
            if (flagMap == null) {
                flagMap = new ConcurrentHashMap<>();
                redirectMap.put(item.getTopic(), flagMap);
            }
            RedirectConfItem oldItem = flagMap.put(item.getRedirectFlag(), item);
            if (oldItem != null && !oldItem.equals(item)) {
                log.info("update redirect message config, old: {} new: {}", oldItem, item);
            }
        }
    }

    public List<RedirectConfItem> getConfigList() {
        List<RedirectConfItem> list = new ArrayList<>();
        for (String topic : redirectMap.keySet()) {
            for (String flag : redirectMap.get(topic).keySet()) {
                list.add(redirectMap.get(topic).get(flag));
            }
        }
        return list;
    }

    public static class RedirectConfItem {
        String topic;
        String consumerGroup;
        String redirectFlag;
        Set<String> ips = new HashSet<>();

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public String getRedirectFlag() {
            return redirectFlag;
        }

        public void setRedirectFlag(String redirectFlag) {
            this.redirectFlag = redirectFlag;
        }

        public Set<String> getIps() {
            return ips;
        }

        public void setIps(Set<String> ips) {
            this.ips = ips;
        }

        public boolean equals(RedirectConfItem obj) {
            return obj != null && this.topic.equals(obj.getTopic())
                && this.redirectFlag.equals(obj.getRedirectFlag())
                && this.consumerGroup.equals(obj.getConsumerGroup())
                && this.ips.equals(obj.getIps());
        }

        @Override public String toString() {
            return "RedirectConfItem{" +
                "topic='" + topic + '\'' +
                ", consumerGroup='" + consumerGroup + '\'' +
                ", redirectFlag='" + redirectFlag + '\'' +
                ", ips=" + ips +
                '}';
        }
    }

    public class RedirectResult {
        RedirectStates states;
        int redirectQueueId;

        public RedirectResult(RedirectStates states, int redirectQueueId) {
            this.states = states;
            this.redirectQueueId = redirectQueueId;
        }

        public RedirectStates getStates() {
            return states;
        }

        public int getRedirectQueueId() {
            return redirectQueueId;
        }
    }

    public enum RedirectStates {
        REDIRECT_OK,
        NO_REDIRECT_CONFIG,
        NO_INSTANCE_FOUND
    }

    public class RedirectConfigManager extends ConfigManager {
        private List<RedirectConfItem> configList = new ArrayList<>();

        @Override public String encode() {
            return encode(false);
        }

        @Override public String configFilePath() {
            String rootDir = DeFiBrokerPathConfigHelper.getBrokerConfigPath();
            return rootDir + File.separator + "messageRedirectConfig.json";
        }

        @Override public void decode(String jsonString) {
            if (jsonString != null) {
                List<RedirectConfItem> list = JSON.parseArray(jsonString, RedirectConfItem.class);
                if (list != null) {
                    updateConfigs(list);
                }
            }
        }

        @Override public String encode(boolean prettyFormat) {
            return JSON.toJSONString(getConfigList(), prettyFormat);
        }
    }
}
