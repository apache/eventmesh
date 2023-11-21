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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.api.meta.MetaServiceListener;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.filter.pattern.Pattern;
import org.apache.eventmesh.filter.patternbuild.PatternBuilder;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerGroupManager;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.ProducerManager;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

public class FilterEngine {

    /**
     * key:group-topic
     **/
    public Map<String, Pattern> filterPatternMap = new HashMap<>();

    private final String FILTER_PREIX = "filter-" ;

    private final MetaStorage metaStorage;

    private MetaServiceListener metaServiceListener;

    private final ProducerManager producerManager;

    private final ConsumerManager consumerManager;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public FilterEngine(MetaStorage metaStorage, ProducerManager producerManager, ConsumerManager consumerManager) {
        this.metaStorage = metaStorage;
        this.producerManager = producerManager;
        this.consumerManager = consumerManager;
    }

    public void init() {
        Map<String, String> filterMetaData = metaStorage.getMetaData(FILTER_PREIX, true);
        for (Entry<String, String> filterDataEntry : filterMetaData.entrySet()) {
            // filter-group
            String key = filterDataEntry.getKey();
            // topic-filterRule list
            String value = filterDataEntry.getValue();
            updateFilterPatternMap(key, value);
        }
        metaServiceListener = this::updateFilterPatternMap;

        // addListeners for producerManager & consumerManager
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            ConcurrentHashMap<String, EventMeshProducer> producerMap = producerManager.getProducerTable();
            for(String producerGroup : producerMap.keySet()) {
                for (String filterKey : filterPatternMap.keySet()) {
                    if (!StringUtils.contains(filterKey, producerGroup)) {
                        addFilterListener(producerGroup);
                    }
                }
            }
            ConcurrentHashMap<String, ConsumerGroupManager> consumerMap = consumerManager.getClientTable();
            for(String consumerGroup : consumerMap.keySet()) {
                for (String filterKey : filterPatternMap.keySet()) {
                    if (!StringUtils.contains(filterKey, consumerGroup)) {
                        addFilterListener(consumerGroup);
                    }
                }
            }
        },10_000, 5_000, TimeUnit.MILLISECONDS);
    }

    private void updateFilterPatternMap(String key, String value) {
        String group = StringUtils.substringAfter(key, FILTER_PREIX);

        JsonNode filterJsonNodeArray = JsonUtils.getJsonNode(value);
        if (filterJsonNodeArray != null) {
            for (JsonNode filterJsonNode : filterJsonNodeArray) {
                String topic = filterJsonNode.get("topic").asText();
                String filterCondition = filterJsonNode.get("condition").asText();
                Pattern filterPattern = PatternBuilder.build(filterCondition);
                filterPatternMap.put(group + "-" + topic, filterPattern);
            }
        }
        addFilterListener(group);
    }

    public void addFilterListener(String group) {
        String filterKey = FILTER_PREIX + group;
        try {
            metaStorage.getMetaDataWithListener(metaServiceListener, filterKey);
        } catch (Exception e) {
            throw new RuntimeException("addFilterListener exception", e);
        }
    }


    public void shutdown() {
        scheduledExecutorService.shutdown();
    }
}
