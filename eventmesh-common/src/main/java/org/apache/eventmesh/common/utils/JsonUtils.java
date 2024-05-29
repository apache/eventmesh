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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshDateFormat;
import org.apache.eventmesh.common.exception.JsonException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

/**
 * Json serialize or deserialize utils.
 */
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        OBJECT_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OBJECT_MAPPER.setDateFormat(new EventMeshDateFormat(Constants.DATE_FORMAT_DEFAULT));
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    public static <T> T mapToObject(Map<String, Object> map, Class<T> beanClass) {
        if (map == null) {
            return null;
        }
        Object obj = OBJECT_MAPPER.convertValue(map, beanClass);
        return (T) obj;
    }
    
    /**
     * Serialize object to json string.
     *
     * @param obj obj
     * @return json string
     */
    public static String toJSONString(Object obj) {
        if (Objects.isNull(obj)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JsonException("serialize to json error", e);
        }
    }

    public static byte[] toJSONBytes(Object obj) {
        if (Objects.isNull(obj)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            throw new JsonException("serialize to json error", e);
        }
    }

    /**
     * parse json string to object.
     *
     * @param text  json string
     * @param clazz object class
     * @param <T>   object type
     * @return object
     */
    public static <T> T parseObject(String text, Class<T> clazz) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, clazz);
        } catch (JsonProcessingException e) {
            throw new JsonException("deserialize json string to object error", e);
        }
    }

    public static <T> T parseObject(InputStream inputStream, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(inputStream, clazz);
        } catch (IOException e) {
            throw new JsonException("deserialize input stream to object error",e);
        }
    }

    public static <T> T parseObject(String text, Type type) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        try {
            TypeReference<T> typeReference = new TypeReference<T>() {

                @Override
                public Type getType() {
                    return type;
                }
            };
            return OBJECT_MAPPER.readValue(text, typeReference);
        } catch (JsonProcessingException e) {
            throw new JsonException("deserialize json string to object error", e);
        }
    }

    public static <T> T parseObject(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new JsonException(String.format("parse bytes to %s error", clazz), e);
        }
    }

    /**
     * parse json string to object.
     *
     * @param text          json string
     * @param typeReference object type reference
     * @param <T>           object type
     * @return object
     */
    public static <T> T parseTypeReferenceObject(String text, TypeReference<T> typeReference) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, typeReference);
        } catch (JsonProcessingException e) {
            throw new JsonException("deserialize json string to typeReference error", e);
        }
    }

    public static <T> T parseTypeReferenceObject(byte[] text, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(text, typeReference);
        } catch (IOException e) {
            throw new JsonException("deserialize json string to typeReference error", e);
        }
    }

    public static JsonNode getJsonNode(String text) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new JsonException("deserialize json string to JsonNode error", e);
        }
    }
}
