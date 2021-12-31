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

package org.apache.eventmesh.admin.rocketmq.util;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonUtils {

    private static ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public static <T> byte[] serialize(String topic, Class<T> data) throws JsonProcessingException {
        if (data == null) {
            return null;
        }
        return objectMapper.writeValueAsBytes(data);
    }

    public static String toJson(Object obj) throws JsonProcessingException {
        if (obj == null) {
            return null;
        }
        return objectMapper.writeValueAsString(obj);
    }

    public static <T> T toObject(String json, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(json, clazz);
    }

    public static <T> T deserialize(Class<T> clazz, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        return objectMapper.readValue(bytes, clazz);
    }

    public static <T> T deserialize(Class<T> clazz, String json) throws IOException {
        if (json == null || json.length() == 0) {
            return null;
        }

        return objectMapper.readValue(json, clazz);
    }
}
