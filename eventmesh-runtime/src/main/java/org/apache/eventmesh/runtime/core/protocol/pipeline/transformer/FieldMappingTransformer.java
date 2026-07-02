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

package org.apache.eventmesh.runtime.core.protocol.pipeline.transformer;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineTransformer;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Field mapping transformer — renames, copies, or drops fields in event data.
 *
 * <p>Mapping rules are specified as key-value pairs in the pipeline context:
 * <pre>{@code
 *   ctx.setAttribute("FieldMappingTransformer.mapping", map);
 * }</pre>
 *
 * <p>Special keys:
 * <ul>
 *   <li>{@code __rename__oldKey:newKey} — rename a field</li>
 *   <li>{@code __drop__fieldName} — drop a field (value ignored)</li>
 *   <li>{@code __copy__oldKey:newKey} — copy field to new name (keep original)</li>
 * </ul>
 */
@Slf4j
public class FieldMappingTransformer implements PipelineTransformer {

    private static final String MAPPING_ATTR = "FieldMappingTransformer.mapping";
    private static final String PREFIX_RENAME = "__rename__";
    private static final String PREFIX_DROP = "__drop__";
    private static final String PREFIX_COPY = "__copy__";

    @Override
    public String name() {
        return "field-mapping";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CloudEvent transform(CloudEvent event, PipelineContext ctx) {
        Map<String, String> mapping = null;
        try {
            Object attr = ctx.getAttribute(MAPPING_ATTR);
            if (attr instanceof Map) {
                mapping = (Map<String, String>) attr;
            }
        } catch (Exception e) {
            log.debug("FieldMappingTransformer: no mapping rules in context");
        }

        if (mapping == null || mapping.isEmpty()) {
            log.trace("FieldMappingTransformer: no mapping rules, pass-through");
            return event;
        }

        if (event.getData() == null) {
            log.trace("FieldMappingTransformer: no data field, pass-through");
            return event;
        }

        try {
            String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            Map<String, Object> dataMap = parseJson(content);

            if (dataMap == null) {
                return event;
            }

            // Apply mappings
            Map<String, Object> result = new LinkedHashMap<>(dataMap);
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (key.startsWith(PREFIX_DROP)) {
                    String targetField = key.substring(PREFIX_DROP.length());
                    result.remove(targetField);
                    log.debug("FieldMappingTransformer: dropped field {}", targetField);
                } else if (key.startsWith(PREFIX_RENAME)) {
                    String targetField = key.substring(PREFIX_RENAME.length());
                    Object fieldValue = result.remove(targetField);
                    if (fieldValue != null) {
                        result.put(value, fieldValue);
                        log.debug("FieldMappingTransformer: renamed {} -> {}", targetField, value);
                    }
                } else if (key.startsWith(PREFIX_COPY)) {
                    String targetField = key.substring(PREFIX_COPY.length());
                    Object fieldValue = result.get(targetField);
                    if (fieldValue != null) {
                        result.put(value, fieldValue);
                        log.debug("FieldMappingTransformer: copied {} -> {}", targetField, value);
                    }
                } else {
                    // Direct key mapping: key=sourcePath, value=targetPath
                    result.put(value, resolvePath(dataMap, key));
                    log.debug("FieldMappingTransformer: mapped {} -> {}", key, value);
                }
            }

            String newContent = toJson(result);
            return CloudEventBuilder.from(event)
                    .withData(newContent.getBytes(StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            log.warn("FieldMappingTransformer: failed to apply field mapping", e);
            return event;
        }
    }

    /** Simple JSON parser — zero-dependency. Supports nested paths like a.b.c. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        // Wrap as single-key map if not object
        String trimmed = json.trim();
        if (!trimmed.startsWith("{")) {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("_value", trimmed);
            return wrapper;
        }
        // Simple flat parser (production would use Jackson/Gson)
        Map<String, Object> map = new LinkedHashMap<>();
        String inner = trimmed.substring(1, trimmed.length() - 1);
        String[] pairs = splitJsonPairs(inner);
        for (String pair : pairs) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = unquote(pair.substring(0, colon).trim());
            String val = pair.substring(colon + 1).trim();
            map.put(key, parseSimpleValue(val));
        }
        return map;
    }

    static String[] splitJsonPairs(String inner) {
        java.util.List<String> result = new java.util.ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (char c : inner.toCharArray()) {
            if (c == '"') inString = !inString;
            if (!inString) {
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
            }
            if (c == ',' && depth == 0 && !inString) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result.toArray(new String[0]);
    }

    static Object parseSimpleValue(String val) {
        if (val.startsWith("\"") && val.endsWith("\"")) return unquote(val);
        if ("true".equals(val)) return true;
        if ("false".equals(val)) return false;
        if ("null".equals(val)) return null;
        try {
            if (val.contains(".")) return Double.parseDouble(val);
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return val;
        }
    }

    static String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    public static Object resolvePath(Map<String, Object> map, String path) {
        if (!path.contains(".")) return map.get(path);
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    public static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append('"').append(e.getKey()).append('"').append(':');
            sb.append(valueToJson(e.getValue()));
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static String valueToJson(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return '"' + escapeJson((String) v) + '"';
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Map) return toJson((Map<String, Object>) v);
        return '"' + escapeJson(v.toString()) + '"';
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
