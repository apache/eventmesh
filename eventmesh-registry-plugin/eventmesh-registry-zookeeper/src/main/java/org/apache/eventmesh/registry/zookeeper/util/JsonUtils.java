package org.apache.eventmesh.registry.zookeeper.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @Author: moxing
 * @Date: 2022/6/17 13:57
 * @Description:
 */
public class JsonUtils {

    public static String toJSON(Object o) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String string = objectMapper.writeValueAsString(o);
            return string;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

}
