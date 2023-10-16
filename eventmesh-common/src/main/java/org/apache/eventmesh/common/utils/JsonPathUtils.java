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

package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.exception.JsonException;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.internal.path.CompiledPath;
import com.jayway.jsonpath.internal.path.PathCompiler;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;

public class JsonPathUtils {
    public static final String JSONPATH_SPLIT = "\\.";
    public static final String JSONPATH_PREFIX = "$";
    public static final String JSONPATH_PREFIX_WITH_POINT = "$.";
    public static final String JSONPATH_DATA = "$.data";
    private static final ObjectMapper STRICT_OBJECT_MAPPER = new ObjectMapper();

    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider(STRICT_OBJECT_MAPPER))
            .build();

    public static boolean isEmptyJsonObject(String jsonString) {
        try {
            JsonNode jsonNode = STRICT_OBJECT_MAPPER.readTree(jsonString);
            return jsonNode.isObject() && jsonNode.isEmpty();
        } catch (Exception e) {
            throw new JsonException("INVALID_JSON_STRING", e);
        }
    }

    public static JsonNode parseStrict(String json) throws JsonException {
        try {
            JsonParser parser = STRICT_OBJECT_MAPPER.getFactory().createParser(json);
            JsonNode result = STRICT_OBJECT_MAPPER.readTree(parser);
            if (parser.nextToken() != null) {
                // Check if there are more tokens after reading the root object
                throw new JsonException("Additional tokens found after parsing: " + json);
            }
            return result;
        } catch (Exception e) {
            throw new JsonException("Json is not valid in strict way: " + json, e);
        }
    }

    public static String buildJsonString(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }


    public static boolean isValidAndDefinite(String jsonPath) {
        if (Strings.isNullOrEmpty(jsonPath) || !jsonPath.startsWith(JSONPATH_PREFIX)) {
            return Boolean.FALSE;
        }
        CompiledPath compiledPath = null;
        try {
            compiledPath = (CompiledPath) PathCompiler.compile(jsonPath);
        } catch (InvalidPathException e) {
            return Boolean.FALSE;
        }
        return compiledPath.isDefinite();
    }

    public static String getJsonPathValue(String content, String jsonPath) {
        if (Strings.isNullOrEmpty(content) || Strings.isNullOrEmpty(jsonPath)) {
            throw new EventMeshException("invalid config" + jsonPath);
        }
        Object obj = null;
        try {
            obj = JsonPath.using(JSON_PATH_CONFIG).parse(content).read(jsonPath, JsonNode.class);
        } catch (InvalidPathException invalidPathException) {
            return null;
        }
        return obj.toString();
    }

    public static JsonNode convertToJsonNode(String  object) throws JsonProcessingException {
        return STRICT_OBJECT_MAPPER.readValue(object, JsonNode.class);
    }

    public static String matchJsonPathValueWithString(String jsonString, String jsonPath) {
        Object obj = jsonPathParse(jsonString, jsonPath);

        if (obj == null) {
            return "null";
        }

        return obj.toString();
    }

    public static Object jsonPathParse(String jsonString, String jsonPath) {
        if (Strings.isNullOrEmpty(jsonPath) || Strings.isNullOrEmpty(jsonString)) {
            throw new EventMeshException("invalid config" + jsonPath);
        }
        Object obj = null;
        try {
            final ReadContext readContext = JsonPath.using(JSON_PATH_CONFIG).parse(jsonString);
            obj = readContext.read(jsonPath);
        } catch (InvalidPathException invalidPathException) {
            return null;
        }
        return obj;
    }

    public static String matchJsonPathValue(String jsonString, String jsonPath) throws JsonProcessingException {
        Object obj = jsonPathParse(jsonString, jsonPath);
        return STRICT_OBJECT_MAPPER.writer().writeValueAsString(obj);
    }
}




