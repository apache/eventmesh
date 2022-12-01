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

import org.apache.eventmesh.common.exception.JsonException;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Json serialize or deserialize utils.
 */
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    }

    /**
     * Serialize object to json string.
     *
     * @param obj obj
     * @return json string
     */
    public static String serialize(Object obj) {
        if (Objects.isNull(obj)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JsonException("serialize to json error", e);
        }
    }

    public static <T> byte[] serialize(String topic, Class<T> data) throws JsonProcessingException {
        if (Objects.isNull(data)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new JsonException("serialize to json error", e);
        }
    }

    /**
     * Deserialize json string to object.
     *
     * @param json json string
     * @param clz  object class
     * @param <T>  object type
     * @return object
     */
    public static <T> T deserialize(String json, Class<T> clz) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clz);
        } catch (JsonProcessingException e) {
            throw new JsonException("deserialize json string to object error", e);
        }
    }

    public static <T> T deserialize(Class<T> clazz, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new JsonException(String.format("deserialize bytes to %s error", clazz), e);
        }
    }

    /**
     * Deserialize json string to object.
     *
     * @param str           json string
     * @param typeReference object type reference
     * @param <T>           object type
     * @return object
     */
    public static <T> T deserialize(String str, TypeReference<T> typeReference) {
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(str, typeReference);
        } catch (JsonProcessingException e) {
            throw new JsonException("deserialize json string to typeReference error", e);
        }
    }

    public static JsonNode getJsonNode(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JsonException("deserialize json string to JsonNode error", e);
        }
    }
}
