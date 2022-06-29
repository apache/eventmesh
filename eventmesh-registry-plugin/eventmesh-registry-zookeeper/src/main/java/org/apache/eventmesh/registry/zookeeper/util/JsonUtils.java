package org.apache.eventmesh.registry.zookeeper.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {

    public static String toJSON(Object o) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static <T> T parseJson(String json, Class<T> c) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            return objectMapper.readValue(json, c);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
