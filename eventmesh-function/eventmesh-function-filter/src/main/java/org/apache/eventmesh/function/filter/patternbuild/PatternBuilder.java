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

package org.apache.eventmesh.function.filter.patternbuild;

import org.apache.eventmesh.common.exception.JsonException;
import org.apache.eventmesh.function.filter.PatternEntry;
import org.apache.eventmesh.function.filter.condition.Condition;
import org.apache.eventmesh.function.filter.condition.ConditionsBuilder;
import org.apache.eventmesh.function.filter.pattern.Pattern;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import io.cloudevents.SpecVersion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PatternBuilder {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Pattern build(String jsonStr) {

        Pattern pattern = new Pattern();
        JsonNode jsonNode = null;
        try {
            jsonNode = mapper.readTree(jsonStr);
        } catch (Exception e) {
            throw new JsonException("INVALID_JSON_STRING", e);
        }

        if (jsonNode.isEmpty() || !jsonNode.isObject()) {
            return null;
        }

        // iter all json data
        Iterator<Entry<String, JsonNode>> iterator = jsonNode.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if (key.equals("data")) {

                parseDataField(value, pattern);
                continue;
            }

            if (!value.isArray()) {
                throw new JsonException("INVALID_JSON_STRING");
            }

            if (!SpecVersion.V1.getAllAttributes().contains(key)) {
                throw new JsonException("INVALID_JSON_KEY");
            }

            // iter all requiredField
            parseRequiredField(key, "$." + key, value, pattern);
        }

        return pattern;

    }

    private static void parseDataField(JsonNode jsonNode, Pattern pattern) {
        if (!jsonNode.isObject()) {
            throw new JsonException("INVALID_JSON_KEY");
        }
        Queue<Node> queueNode = new ArrayDeque<>();
        Node node = new Node("$.data", "data", jsonNode);
        queueNode.add(node);
        while (!queueNode.isEmpty()) {
            Node ele = queueNode.poll();
            String elepath = ele.getPath();
            Iterator<Map.Entry<String, JsonNode>> iterator = ele.getValue().fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                // state
                String key = entry.getKey();
                // [{"anything-but":"initializing"}] [{"anything-but":123}]}
                JsonNode value = entry.getValue();
                PatternEntry patternEntry = new PatternEntry(key, elepath + "." + key);
                if (!value.isObject()) {
                    if (value.isArray()) {
                        for (JsonNode node11 : value) {
                            // {"anything-but":"initializing"}
                            patternEntry.addCondition(parseCondition(node11));
                        }
                    }
                    pattern.addDataList(patternEntry);
                } else {
                    queueNode.add(new Node(elepath + "." + key, key, value));
                }
            }
        }

    }

    private static Condition parseCondition(JsonNode jsonNode) {
        if (jsonNode.isValueNode()) {
            return new ConditionsBuilder().withKey("specified").withParams(jsonNode).build();
        }

        Iterator<Map.Entry<String, JsonNode>> iterator = jsonNode.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            // "anything-but"
            String key = entry.getKey();
            // "initializing"
            JsonNode value = entry.getValue();
            return new ConditionsBuilder().withKey(key).withParams(value).build();
        }
        return null;
    }

    private static void parseRequiredField(String patternName, String patternPath, JsonNode jsonNode, Pattern pattern) {
        if (jsonNode.isEmpty()) {
            // Empty array
            throw new JsonException("INVALID_PATTERN_VALUE ");
        }
        PatternEntry patternEntry = new PatternEntry(patternName, patternPath);
        for (final JsonNode objNode : jsonNode) {
            Condition condition = parseCondition(objNode);
            patternEntry.addCondition(condition);
        }

        pattern.addRequiredFieldList(patternEntry);

    }

    static class Node {

        private String path;
        private String key;
        private JsonNode value;

        Node(String path, String key, JsonNode value) {
            this.path = path;
            this.key = key;
            this.value = value;
        }

        String getPath() {
            return this.path;
        }

        String getKey() {
            return this.key;
        }

        JsonNode getValue() {
            return this.value;
        }
    }

}
