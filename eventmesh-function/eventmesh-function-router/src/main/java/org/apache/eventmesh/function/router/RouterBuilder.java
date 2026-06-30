/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.function.router;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.function.api.Router;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RouterBuilder {

    /**
     * Build a Router from the given configuration string.
     *
     * Supported config formats:
     * <ul>
     *   <li>Plain string (e.g. "target-topic") - static routing to a fixed topic</li>
     *   <li>JSON object with "type": "static" and "targetTopic": "..." - explicit static routing</li>
     *   <li>JSON object with "type": "dynamic" and "rules": [...] - content-based dynamic routing</li>
     * </ul>
     *
     * Dynamic routing rules format:
     * <pre>
     * {
     *   "type": "dynamic",
     *   "defaultTopic": "fallback-topic",
     *   "rules": [
     *     {"field": "$.header.type", "pattern": "order", "topic": "order-topic"},
     *     {"field": "$.body.priority", "pattern": "high", "topic": "priority-topic"}
     *   ]
     * }
     * </pre>
     * Each rule is evaluated in order; the first matching rule's topic is used.
     * If no rule matches, defaultTopic is returned.
     *
     * @param routerConfig the configuration string
     * @return a Router instance
     */
    public static Router build(String routerConfig) {
        if (routerConfig == null || routerConfig.trim().isEmpty()) {
            throw new IllegalArgumentException("routerConfig cannot be null or empty");
        }

        String trimmed = routerConfig.trim();

        // Try to parse as JSON; if it fails, treat as a plain topic name (backward compatibility)
        JsonNode configNode = null;
        try {
            configNode = JsonUtils.getJsonNode(trimmed);
        } catch (Exception e) {
            // Not valid JSON - treat as static topic name
            return new StaticRouter(trimmed);
        }

        if (configNode == null) {
            return new StaticRouter(trimmed);
        }

        // If it's a simple string node (e.g. "target-topic" with quotes), treat as static
        if (configNode.isTextual()) {
            return new StaticRouter(configNode.asText());
        }

        // If it's a plain string value that happens to be valid JSON (e.g. just a topic name)
        if (!configNode.isObject()) {
            return new StaticRouter(configNode.asText());
        }

        String type = configNode.has("type") ? configNode.get("type").asText() : "static";

        if ("dynamic".equalsIgnoreCase(type)) {
            return buildDynamicRouter(configNode);
        }

        // Default: static routing
        String targetTopic = configNode.has("targetTopic")
            ? configNode.get("targetTopic").asText()
            : trimmed;
        return new StaticRouter(targetTopic);
    }

    private static Router buildDynamicRouter(JsonNode configNode) {
        String defaultTopic = configNode.has("defaultTopic")
            ? configNode.get("defaultTopic").asText()
            : null;

        List<DynamicRouter.Rule> rules = new ArrayList<>();
        JsonNode rulesNode = configNode.get("rules");
        if (rulesNode != null && rulesNode.isArray()) {
            for (JsonNode ruleNode : rulesNode) {
                String field = ruleNode.has("field") ? ruleNode.get("field").asText() : null;
                String patternStr = ruleNode.has("pattern") ? ruleNode.get("pattern").asText() : null;
                String topic = ruleNode.has("topic") ? ruleNode.get("topic").asText() : null;
                if (topic != null) {
                    Pattern regexPattern = patternStr != null ? Pattern.compile(patternStr) : null;
                    rules.add(new DynamicRouter.Rule(field, patternStr, regexPattern, topic));
                }
            }
        }

        return new DynamicRouter(rules, defaultTopic);
    }

    /**
     * Static router that always routes to a fixed target topic.
     */
    private static class StaticRouter implements Router {
        private final String targetTopic;

        public StaticRouter(String targetTopic) {
            this.targetTopic = targetTopic;
        }

        @Override
        public String route(String json) {
            return targetTopic;
        }
    }

    /**
     * Dynamic router that evaluates content-based rules to determine the target topic.
     * Rules are evaluated in order; the first matching rule's topic is returned.
     * If no rule matches, the default topic is returned.
     */
    private static class DynamicRouter implements Router {
        private final List<Rule> rules;
        private final String defaultTopic;

        public DynamicRouter(List<Rule> rules, String defaultTopic) {
            this.rules = rules;
            this.defaultTopic = defaultTopic;
        }

        @Override
        public String route(String json) {
            if (rules.isEmpty()) {
                return defaultTopic;
            }

            try {
                JsonNode contentNode = JsonUtils.getJsonNode(json);
                if (contentNode == null) {
                    return defaultTopic;
                }

                for (Rule rule : rules) {
                    if (rule.matches(contentNode)) {
                        return rule.topic;
                    }
                }
            } catch (Exception e) {
                log.warn("Dynamic routing evaluation failed, falling back to default topic", e);
            }

            return defaultTopic;
        }
    }

    /**
     * A single routing rule that matches a JSON field against a pattern.
     */
    private static class Rule {
        final String field;
        final String patternStr;
        final Pattern regexPattern;
        final String topic;

        Rule(String field, String patternStr, Pattern regexPattern, String topic) {
            this.field = field;
            this.patternStr = patternStr;
            this.regexPattern = regexPattern;
            this.topic = topic;
        }

        boolean matches(JsonNode contentNode) {
            if (field == null || regexPattern == null) {
                // If no field/pattern specified, always match (catch-all rule)
                return true;
            }

            // Navigate the JSON path (simplified: supports dot notation like $.body.type)
            String path = field;
            if (path.startsWith("$.")) {
                path = path.substring(2);
            } else if (path.startsWith("$")) {
                path = path.substring(1);
            }

            JsonNode currentNode = contentNode;
            for (String segment : path.split("\\.")) {
                if (segment.isEmpty()) {
                    continue;
                }
                if (currentNode == null) {
                    return false;
                }
                currentNode = currentNode.get(segment);
            }

            if (currentNode == null) {
                return false;
            }

            String value = currentNode.isTextual() ? currentNode.asText() : currentNode.toString();
            return regexPattern.matcher(value).find();
        }
    }
}
