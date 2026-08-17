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

package org.apache.eventmesh.common.wire;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON codec for the streaming {@code meta} map (string/number/bool/null + nested values),
 * kept dependency-free so {@code eventmesh-common}'s wire package doesn't pull Jackson. Used by
 * {@link EventMeshFrame} for the {@code meta} attribute on stream chunks.
 */
final class MetaJson {

    private MetaJson() {
    }

    static String stringify(Map<String, Object> meta) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : meta.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            quote(sb, e.getKey());
            sb.append(':');
            value(sb, e.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    static Map<String, Object> parse(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return new Parser(json).parseObject();
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"').append(escape(s)).append('"');
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    private static void value(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else {
            quote(sb, v.toString());
        }
    }

    private static final class Parser {
        private final String src;
        private int idx;

        Parser(String src) {
            this.src = src;
        }

        private void skipWs() {
            while (idx < src.length() && Character.isWhitespace(src.charAt(idx))) {
                idx++;
            }
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (idx >= src.length() || src.charAt(idx) != '{') {
                return map;
            }
            idx++;
            skipWs();
            if (idx < src.length() && src.charAt(idx) == '}') {
                return map;
            }
            while (idx < src.length()) {
                skipWs();
                String key = parseString();
                skipWs();
                if (idx < src.length() && src.charAt(idx) == ':') {
                    idx++;
                }
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (idx < src.length() && src.charAt(idx) == ',') {
                    idx++;
                } else {
                    break;
                }
            }
            return map;
        }

        private Object parseValue() {
            skipWs();
            if (idx >= src.length()) {
                return null;
            }
            char c = src.charAt(idx);
            if (c == '"') {
                return parseString();
            }
            if (c == '{') {
                return parseObject();
            }
            if (c == 't' || c == 'f' || c == 'n' || c == '-' || (c >= '0' && c <= '9')) {
                return parseLiteral();
            }
            return null;
        }

        private String parseString() {
            StringBuilder out = new StringBuilder();
            if (idx >= src.length() || src.charAt(idx) != '"') {
                return "";
            }
            idx++;
            while (idx < src.length()) {
                char c = src.charAt(idx++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && idx < src.length()) {
                    char e = src.charAt(idx++);
                    switch (e) {
                        case '"':
                            out.append('"');
                            break;
                        case '\\':
                            out.append('\\');
                            break;
                        case 'n':
                            out.append('\n');
                            break;
                        case 'r':
                            out.append('\r');
                            break;
                        case 't':
                            out.append('\t');
                            break;
                        default:
                            out.append(e);
                    }
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }

        private Object parseLiteral() {
            int start = idx;
            while (idx < src.length()) {
                char c = src.charAt(idx);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                idx++;
            }
            String tok = src.substring(start, idx);
            if ("true".equals(tok)) {
                return Boolean.TRUE;
            }
            if ("false".equals(tok)) {
                return Boolean.FALSE;
            }
            if ("null".equals(tok)) {
                return null;
            }
            try {
                if (tok.contains(".") || tok.contains("e") || tok.contains("E")) {
                    return Double.parseDouble(tok);
                }
                return Long.parseLong(tok);
            } catch (NumberFormatException ex) {
                return tok;
            }
        }
    }
}
