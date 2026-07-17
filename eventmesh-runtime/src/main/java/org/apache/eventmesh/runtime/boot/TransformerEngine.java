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
import org.apache.eventmesh.function.transformer.Transformer;
import org.apache.eventmesh.function.transformer.TransformerBuilder;
import org.apache.eventmesh.function.transformer.TransformerParam;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerGroupManager;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransformerEngine {

    /**
     * key:group-topic
     **/
    private final Map<String, Transformer> transformerMap = new HashMap<>();

    private final String transformerPrefix = "transformer-";

    private final MetaStorage metaStorage;

    private MetaServiceListener metaServiceListener;

    private final ProducerManager producerManager;

    private final ConsumerManager consumerManager;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public TransformerEngine(MetaStorage metaStorage, ProducerManager producerManager, ConsumerManager consumerManager) {
        this.metaStorage = metaStorage;
        this.producerManager = producerManager;
        this.consumerManager = consumerManager;
    }

    public TransformerEngine(MetaStorage metaStorage) {
        this(metaStorage, null, null);
    }

    public void start() {
        Map<String, String> transformerMetaData = metaStorage.getMetaData(transformerPrefix, true);
        for (Entry<String, String> transformerDataEntry : transformerMetaData.entrySet()) {
            // transformer-group
            String key = transformerDataEntry.getKey();
            // topic-transformerParam list
            String value = transformerDataEntry.getValue();
            updateTransformerMap(key, value);
        }
        metaServiceListener = this::updateTransformerMap;

        // addListeners for producerManager & consumerManager
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            if (producerManager != null) {
                ConcurrentHashMap<String, EventMeshProducer> producerMap = producerManager.getProducerTable();
                for (String producerGroup : producerMap.keySet()) {
                    for (String transformerKey : transformerMap.keySet()) {
                        if (!StringUtils.contains(transformerKey, producerGroup)) {
                            addTransformerListener(producerGroup);
                            log.info("addTransformerListener for producer group: " + producerGroup);
                        }
                    }
                }
            }
            if (consumerManager != null) {
                ConcurrentHashMap<String, ConsumerGroupManager> consumerMap = consumerManager.getClientTable();
                for (String consumerGroup : consumerMap.keySet()) {
                    for (String transformerKey : transformerMap.keySet()) {
                        if (!StringUtils.contains(transformerKey, consumerGroup)) {
                            addTransformerListener(consumerGroup);
                            log.info("addTransformerListener for consumer group: " + consumerGroup);
                        }
                    }
                }
            }
        }, 10_000, 5_000, TimeUnit.MILLISECONDS);
    }

    private void updateTransformerMap(String key, String value) {
        String group = StringUtils.substringAfter(key, transformerPrefix);

        JsonNode transformerJsonNodeArray = JsonUtils.getJsonNode(value);

        if (transformerJsonNodeArray != null) {
            for (JsonNode transformerJsonNode : transformerJsonNodeArray) {
                String topic = transformerJsonNode.get("topic").asText();
                String transformerParam = transformerJsonNode.get("transformerParam").toString();
                TransformerParam tfp = JsonUtils.parseObject(transformerParam, TransformerParam.class);
                Transformer transformer = TransformerBuilder.buildTransformer(tfp);
                transformerMap.put(group + "-" + topic, transformer);
            }
        }
        addTransformerListener(group);
    }

    public void addTransformerListener(String group) {
        String transformerKey = transformerPrefix + group;
        try {
            metaStorage.getMetaDataWithListener(metaServiceListener, transformerKey);
        } catch (Exception e) {
            throw new RuntimeException("addTransformerListener exception", e);
        }
    }

    public void shutdown() {
        scheduledExecutorService.shutdown();
    }

    public Transformer getTransformer(String key) {
        return transformerMap.get(key);
    }

}
