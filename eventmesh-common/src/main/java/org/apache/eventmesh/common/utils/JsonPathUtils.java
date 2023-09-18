package org.apache.eventmesh.common.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.exception.JsonException;
import com.jayway.jsonpath.internal.path.CompiledPath;
import com.jayway.jsonpath.internal.path.PathCompiler;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.apache.eventmesh.common.filter.JacksonUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
            throw new JsonException("INVALID_JSON_STRING",e);
        }
    }

    public static JsonNode STRING_JSON(String json) throws JsonException {
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

    public static String getJsonPathValue(String content, String jsonPath){
        if (Strings.isNullOrEmpty(content) || Strings.isNullOrEmpty(jsonPath)) {
            throw new EventMeshException("invalid config" + jsonPath);
        }
        Object obj = null;
        try {
            obj = JsonPath.using(JSON_PATH_CONFIG).parse(content).read(jsonPath, JsonNode.class);
        } catch (InvalidPathException invalidPathException) {
            System.out.println("111");
            return null;
        }
        return obj.toString();
    }

    public static JsonNode convertToJsonNode(String  object) throws JsonProcessingException {
        return STRICT_OBJECT_MAPPER.readValue(object, JsonNode.class);
    }

    public static String matchJsonPathValueWithString(String jsonString, String jsonPath) throws JsonProcessingException {
        if (Strings.isNullOrEmpty(jsonString) || Strings.isNullOrEmpty(jsonPath)) {
            throw new EventMeshException("invalid config" + jsonPath);
        }
        Object obj = null;
        try {
            final ReadContext readContext = JsonPath.using(JSON_PATH_CONFIG).parse(jsonString);
            obj = readContext.read(jsonPath);
        } catch (InvalidPathException invalidPathException) {
            return null;
        }

        if(obj == null){
            return "null";
        }

        return obj.toString();

    }

    public static String matchJsonPathValue(String jsonString, String jsonPath) throws JsonProcessingException {
        if (Strings.isNullOrEmpty(jsonString) || Strings.isNullOrEmpty(jsonPath)) {
            throw new EventMeshException("invalid config" + jsonPath);
        }
        Object obj = null;
        try {
            final ReadContext readContext = JsonPath.using(JSON_PATH_CONFIG).parse(jsonString);
            obj = readContext.read(jsonPath);
        } catch (InvalidPathException invalidPathException) {
            return null;
        }

        return STRICT_OBJECT_MAPPER.writer().writeValueAsString(obj);
//        if(obj instanceof String){
//            return " \""+ obj.toString() + "\" ";
//        }else if(obj instanceof ){
//            return obj.toString();
//        }

    }
}




