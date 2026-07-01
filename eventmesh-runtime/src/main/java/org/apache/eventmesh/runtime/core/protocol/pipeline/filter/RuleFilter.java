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

package org.apache.eventmesh.runtime.core.protocol.pipeline.filter;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineFilter;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * User-defined rule matching filter.
 * Supports topic allowlist/denylist and custom content-based rules.
 *
 * <p>Bypassable — when no rules are configured, this filter is a no-op.
 */
@Slf4j
public class RuleFilter implements PipelineFilter {

    public static final String NAME = "RuleFilter";

    private final Set<String> allowedTopics;
    private final Set<String> deniedTopics;
    private final Map<String, String> contentRules;

    public RuleFilter() {
        this.allowedTopics = ConcurrentHashMap.newKeySet();
        this.deniedTopics = ConcurrentHashMap.newKeySet();
        this.contentRules = new ConcurrentHashMap<>();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return 4;
    }

    @Override
    public boolean isBypassable() {
        return true;
    }

    @Override
    public PipelineResult filter(CloudEvent event, PipelineContext ctx) {
        String topic = event.getSubject();

        // No rules → pass through
        if (allowedTopics.isEmpty() && deniedTopics.isEmpty() && contentRules.isEmpty()) {
            return PipelineResult.cont(event);
        }

        // Denylist takes precedence
        if (topic != null && deniedTopics.contains(topic)) {
            log.debug("[RuleFilter] Event {} denied for topic {}", event.getId(), topic);
            return PipelineResult.drop(event);
        }

        // Allowlist check (if configured)
        if (!allowedTopics.isEmpty()) {
            if (topic == null || !allowedTopics.contains(topic)) {
                log.debug("[RuleFilter] Event {} topic {} not in allowlist", event.getId(), topic);
                return PipelineResult.drop(event);
            }
        }

        // Content rule check
        if (event.getData() != null) {
            String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> rule : contentRules.entrySet()) {
                if (content.contains(rule.getKey())) {
                    if (!"allow".equals(rule.getValue())) {
                        log.debug("[RuleFilter] Event {} blocked by content rule '{}'", event.getId(), rule.getKey());
                        return PipelineResult.drop(event);
                    }
                }
            }
        }

        return PipelineResult.cont(event);
    }

    // ---- Programmatic rule management ----

    public void addAllowedTopic(String topic) {
        allowedTopics.add(topic);
    }

    public void removeAllowedTopic(String topic) {
        allowedTopics.remove(topic);
    }

    public void addDeniedTopic(String topic) {
        deniedTopics.add(topic);
    }

    public void removeDeniedTopic(String topic) {
        deniedTopics.remove(topic);
    }

    public void addContentRule(String keyword, String action) {
        contentRules.put(keyword, action);
    }

    public void removeContentRule(String keyword) {
        contentRules.remove(keyword);
    }

    public Set<String> getAllowedTopics() {
        return Collections.unmodifiableSet(allowedTopics);
    }

    public Set<String> getDeniedTopics() {
        return Collections.unmodifiableSet(deniedTopics);
    }
}
