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

import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.registry.Registry;

import java.util.HashMap;
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
        try {
            Map<String, String> metadata = new HashMap<>(1 << 4);
            for (Map.Entry<String, ConsumerGroupConf> consumerGroupMap : eventMeshHTTPServer.localConsumerGroupMapping.entrySet()) {
                ConsumerGroupConf consumerGroupConf = consumerGroupMap.getValue();
                Map<String, ConsumerGroupTopicConf> consumerGroupTopicConf = consumerGroupConf.getConsumerGroupTopicConf();
                for (Map.Entry<String, ConsumerGroupTopicConf> consumerGroupTopicConfEntry : consumerGroupTopicConf.entrySet()) {
                    ConsumerGroupTopicConf groupTopicConf = consumerGroupTopicConfEntry.getValue();
                    metadata.put(groupTopicConf.getTopic(), String.join(",", groupTopicConf.getUrls()));
                }
            }
            Registry registry = eventMeshHTTPServer.getRegistry();
            registry.registerMetadata(metadata);
        } catch (Exception e) {
            LOGGER.error("[LocalSubscribeEventProcessor] update eventmesh metadata error", e);
        }
    }
}
