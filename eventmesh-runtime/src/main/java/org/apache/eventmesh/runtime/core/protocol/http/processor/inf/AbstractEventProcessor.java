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

package org.apache.eventmesh.runtime.core.protocol.http.processor.inf;

import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.registry.nacos.constant.NacosConstant;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupMetadata;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicMetadata;
import org.apache.eventmesh.runtime.registry.Registry;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EventProcessor
 */
public abstract class AbstractEventProcessor implements EventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger("AbstractEventProcessor");

    protected EventMeshHTTPServer eventMeshHTTPServer;

    public AbstractEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    /**
     * Add a topic with subscribers to the service's metadata.
     */
    protected void updateMetadata() {
        if (!eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerRegistryEnable) {
            return;
        }
        try {
            Map<String, String> metadata = new HashMap<>(1 << 4);
            for (Map.Entry<String, ConsumerGroupConf> consumerGroupMap : eventMeshHTTPServer.localConsumerGroupMapping.entrySet()) {
                String consumerGroupKey = consumerGroupMap.getKey();
                ConsumerGroupConf consumerGroupConf = consumerGroupMap.getValue();

                ConsumerGroupMetadata consumerGroupMetadata = new ConsumerGroupMetadata();
                consumerGroupMetadata.setConsumerGroup(consumerGroupKey);

                Map<String, ConsumerGroupTopicMetadata> consumerGroupTopicMetadataMap = new HashMap<>(1 << 4);
                for (Map.Entry<String, ConsumerGroupTopicConf> consumerGroupTopicConfEntry : consumerGroupConf.getConsumerGroupTopicConf()
                    .entrySet()) {
                    final String topic = consumerGroupTopicConfEntry.getKey();
                    ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupTopicConfEntry.getValue();
                    ConsumerGroupTopicMetadata consumerGroupTopicMetadata = new ConsumerGroupTopicMetadata();
                    consumerGroupTopicMetadata.setConsumerGroup(consumerGroupTopicConf.getConsumerGroup());
                    consumerGroupTopicMetadata.setTopic(consumerGroupTopicConf.getTopic());
                    consumerGroupTopicMetadata.setUrls(consumerGroupTopicConf.getUrls());

                    consumerGroupTopicMetadataMap.put(topic, consumerGroupTopicMetadata);
                }
                consumerGroupMetadata.setConsumerGroupTopicMetadataMap(consumerGroupTopicMetadataMap);
                metadata.put(consumerGroupKey, JsonUtils.serialize(consumerGroupMetadata));
            }
            Registry registry = eventMeshHTTPServer.getRegistry();
            registry.registerMetadata(metadata);
        } catch (Exception e) {
            LOGGER.error("[LocalSubscribeEventProcessor] update eventmesh metadata error", e);
        }
    }


    protected String getTargetMesh(String consumerGroup, List<SubscriptionItem> subscriptionList)
        throws Exception {
        // Currently only supports http
        CommonConfiguration httpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        if (!httpConfiguration.eventMeshServerRegistryEnable) {
            return "";
        }

        String targetMesh = "";
        Registry registry = eventMeshHTTPServer.getRegistry();
        List<EventMeshDataInfo> allEventMeshInfo = registry.findAllEventMeshInfo();
        String httpServiceName =
            ConfigurationContextUtil.HTTP + "-" + NacosConstant.GROUP + "@@" + httpConfiguration.eventMeshName + "-" + ConfigurationContextUtil.HTTP;
        for (EventMeshDataInfo eventMeshDataInfo : allEventMeshInfo) {
            if (!eventMeshDataInfo.getEventMeshName().equals(httpServiceName)) {
                continue;
            }
            if (httpConfiguration.eventMeshCluster.equals(eventMeshDataInfo.getEventMeshClusterName())) {
                continue;
            }
            Map<String, String> metadata = eventMeshDataInfo.getMetadata();
            String topicMetadataJson = metadata.get(consumerGroup);
            if (StringUtils.isBlank(topicMetadataJson)) {
                continue;
            }

            ConsumerGroupMetadata consumerGroupMetadata = JsonUtils.deserialize(topicMetadataJson, ConsumerGroupMetadata.class);
            Map<String, ConsumerGroupTopicMetadata> consumerGroupTopicMetadataMap = consumerGroupMetadata.getConsumerGroupTopicMetadataMap();

            for (SubscriptionItem subscriptionItem : subscriptionList) {
                if (consumerGroupTopicMetadataMap.containsKey(subscriptionItem.getTopic())) {
                    targetMesh = "http://" + eventMeshDataInfo.getEndpoint() + "/eventmesh/subscribe/local";
                    break;
                }
            }
            break;
        }
        return targetMesh;
    }
}
