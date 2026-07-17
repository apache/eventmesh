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
import org.apache.eventmesh.function.api.Router;
import org.apache.eventmesh.function.router.RouterBuilder;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RouterEngine {

    private final MetaStorage metaStorage;

    private final Map<String, Router> routerMap = new ConcurrentHashMap<>();

    private final String routerPrefix = "router-";

    private MetaServiceListener metaServiceListener;

    public RouterEngine(MetaStorage metaStorage) {
        this.metaStorage = metaStorage;
    }

    public void start() {
        Map<String, String> routerMetaData = metaStorage.getMetaData(routerPrefix, true);
        for (Entry<String, String> routerDataEntry : routerMetaData.entrySet()) {
            String key = routerDataEntry.getKey();
            String value = routerDataEntry.getValue();
            updateRouterMap(key, value);
        }
        metaServiceListener = this::updateRouterMap;
    }

    private void updateRouterMap(String key, String value) {
        String group = StringUtils.substringAfter(key, routerPrefix);

        JsonNode routerJsonNodeArray = JsonUtils.getJsonNode(value);
        if (routerJsonNodeArray != null) {
            for (JsonNode routerJsonNode : routerJsonNodeArray) {
                String topic = routerJsonNode.get("topic").asText();
                String routerConfig = routerJsonNode.get("routerConfig").toString();
                Router router = RouterBuilder.build(routerConfig);
                routerMap.put(group + "-" + topic, router);
            }
        }
        addRouterListener(group);
    }

    public void addRouterListener(String group) {
        String routerKey = routerPrefix + group;
        try {
            metaStorage.getMetaDataWithListener(metaServiceListener, routerKey);
        } catch (Exception e) {
            log.error("addRouterListener exception", e);
        }
    }

    public void shutdown() {
        routerMap.clear();
    }

    public Router getRouter(String key) {
        return routerMap.get(key);
    }
}
