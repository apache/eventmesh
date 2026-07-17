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

package org.apache.eventmesh.runtime.cluster;

import org.apache.eventmesh.runtime.ingress.UniIngressService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Dynamic configuration hot-reload (§13.6.3). Watches Meta for rule changes and pushes them into
 * the runtime without a restart:
 * <ul>
 *   <li>{@code /em/ratelimit/rules} → {@link UniIngressService#setTopicRateLimit} (per-topic
 *       rate-limit rules, §13.6.1).</li>
 * </ul>
 *
 * <p>On start it reads the current value once (catch-up), then registers a watch for subsequent
 * changes. Rule format: {@code [{"topic":"orders","capacity":100,"rate":10.0}, ...]}. When Meta is
 * unavailable the watch simply stops firing; the last-applied rules stay in force (§13.2.9
 * degraded mode).</p>
 *
 * <p>ACL rule hot-reload ({@code /em/acl/rules} → {@code AclFilter.setRules}) is wired once the
 * security filter chain is boot-strapped (G14 Filter boot wiring) — the {@link MetaStore} watch
 * itself is generic.</p>
 */
@Slf4j
public class DynamicConfigWatcher {

    private static final String RATELIMIT_KEY = "/em/ratelimit/rules";

    private final MetaStore meta;
    private final UniIngressService ingress;
    private final ObjectMapper mapper = new ObjectMapper();

    public DynamicConfigWatcher(MetaStore meta, UniIngressService ingress) {
        this.meta = meta;
        this.ingress = ingress;
    }

    /** Read the current rules once, then watch for changes. */
    public void start() {
        String initial = meta.get(RATELIMIT_KEY);
        if (initial != null) {
            applyRateLimitRules(initial);
        }
        meta.watch(RATELIMIT_KEY, (key, value, deleted) -> {
            if (deleted || value == null) {
                return;
            }
            applyRateLimitRules(value);
        });
        log.info("dynamic config watcher started (rate-limit rules from Meta {})", RATELIMIT_KEY);
    }

    private void applyRateLimitRules(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            if (!node.isArray()) {
                log.warn("rate-limit rules payload is not an array: {}", json);
                return;
            }
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode rule : arr) {
                String topic = rule.has("topic") ? rule.get("topic").asText() : null;
                if (topic == null) {
                    continue;
                }
                long capacity = rule.has("capacity") ? rule.get("capacity").asLong() : 0L;
                double rate = rule.has("rate") ? rule.get("rate").asDouble() : 0.0;
                ingress.setTopicRateLimit(topic, capacity, rate);
            }
            log.info("applied {} rate-limit rule(s) from Meta", arr.size());
        } catch (Exception e) {
            log.warn("failed to apply rate-limit rules from Meta: {}", e.toString());
        }
    }
}
