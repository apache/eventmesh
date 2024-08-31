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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JsonPathUtilsTest {

    @Test
    public void tesTisEmptyJsonObject() {
        String emptyJsonObject = "{}";
        String jsonObject = "{\"key\": \"value\"}";
        String emptyJsonArray = "[]";
        String jsonArray = "[{\"key\": \"value\"}]";
        String empty = "";

        assertTrue(JsonPathUtils.isEmptyJsonObject(emptyJsonObject));
        assertFalse(JsonPathUtils.isEmptyJsonObject(jsonObject));
        assertFalse(JsonPathUtils.isEmptyJsonObject(emptyJsonArray));
        assertFalse(JsonPathUtils.isEmptyJsonObject(jsonArray));
        assertFalse(JsonPathUtils.isEmptyJsonObject(empty));
    }

    @Test
    public void testParseStrict() {
       String json = "{\"key\": \"value\"}";
        JsonNode result = JsonPathUtils.parseStrict(json);
        assertNotNull(result);
        assertEquals("value", result.get("key").asText());

        String emptyJsonObject = "{}";
        JsonNode result2 = JsonPathUtils.parseStrict(emptyJsonObject);
        assertNotNull(result2);
        assertTrue(result2.isEmpty());

    }

    @Test
    public void testBuildJsonString() {
        Map<String,String> person = new HashMap<>();
        person.put("name", "John");
        person.put("age", "30");
        String actual = JsonPathUtils.buildJsonString("person", person);
        String excepted = "{\"person\":{\"name\":\"John\",\"age\":\"30\"}}";
        assertNotNull(actual);
        assertEquals(excepted, actual);
    }

    @Test
    public void testIsValidAndDefinite() {
        String jsonPath = "$.person[0].name";
        String jsonPath2 = "$.person[*].address.city";
        String jsonPath3 = "person.job[0].title";
        String jsonPath4 = null;
        String jsonPath5 = "";

        assertTrue(JsonPathUtils.isValidAndDefinite(jsonPath));
        assertFalse(JsonPathUtils.isValidAndDefinite(jsonPath2));
        assertFalse(JsonPathUtils.isValidAndDefinite(jsonPath3));
        assertFalse(JsonPathUtils.isValidAndDefinite(jsonPath4));
        assertFalse(JsonPathUtils.isValidAndDefinite(jsonPath5));

    }

    @Test
    public void testGetJsonPathValue() {
        String jsonContent = "{ \"person\": { \"name\": \"John Doe\", \"age\": 30, \"address\": { \"city\": \"New York\" } } }";

        String jsonPath1 = "$.person.name";
        String jsonPath2 = "$.person.address.city";
        String jsonPath3 = "$.person.age";

        assertEquals("John Doe", JsonPathUtils.getJsonPathValue(jsonContent, jsonPath1));
        assertEquals("New York", JsonPathUtils.getJsonPathValue(jsonContent, jsonPath2));
        assertEquals("30", JsonPathUtils.getJsonPathValue(jsonContent, jsonPath3));

    }

    @Test
    public void testConvertToJsonNode() {
        String jsonString1 = "{\"name\": \"John Doe\", \"age\": 30, \"address\": { \"city\": \"New York\" }}";
        try {
            JsonNode node1 = JsonPathUtils.convertToJsonNode(jsonString1);
            assertEquals("John Doe", node1.get("name").asText());
            assertEquals("New York", node1.get("address").get("city").asText());
            assertEquals("30", node1.get("age").asText());
        } catch (JsonProcessingException ignored) {

        }


    }

    @Test
    public void testMatchJsonPathValueWithString() {
        String jsonString = "{\"name\": \"John Doe\", \"age\": 30, \"address\": { \"city\": \"New York\" }}";

        String jsonPath1 = "$.name";
        String result1 = JsonPathUtils.matchJsonPathValueWithString(jsonString, jsonPath1);
        assertEquals("John Doe", result1);

        String jsonPath2 = "$.age";
        String result2 = JsonPathUtils.matchJsonPathValueWithString(jsonString, jsonPath2);
        assertEquals("30", result2); // Age should be returned as a string

        String jsonPath3 = "$.address.city";
        String result3 = JsonPathUtils.matchJsonPathValueWithString(jsonString, jsonPath3);
        assertEquals("New York", result3);

        String jsonPath4 = "$.job";
        String result4 = JsonPathUtils.matchJsonPathValueWithString(jsonString, jsonPath4);
        assertEquals("null", result4);
    }

    @Test
    public void testJsonPathParse() {
        String jsonString = "{\"name\": \"John Doe\", \"age\": 30, \"address\": { \"city\": \"New York\" }}";

        String jsonPath1 = "$.name";
        Object result1 = JsonPathUtils.jsonPathParse(jsonString, jsonPath1);
        assertNotNull(result1);
        assertEquals("John Doe", result1);

        String jsonPath2 = "$.address.city";
        Object result2 = JsonPathUtils.jsonPathParse(jsonString, jsonPath2);
        assertNotNull(result2);
        assertEquals("New York", result2);
    }

    @Test
    public void testMatchJsonPathValue() {
        String jsonString = "{\"name\": \"John Doe\", \"age\": 30, \"address\": { \"city\": \"New York\" }}";

        try {
            String jsonPath1 = "$.name";
            String result1 = JsonPathUtils.matchJsonPathValue(jsonString, jsonPath1);
            assertEquals("\"John Doe\"", result1);

            String jsonPath2 = "$.address.city";
            String result2 = JsonPathUtils.matchJsonPathValue(jsonString, jsonPath2);
            assertEquals("\"New York\"", result2);

            String jsonPath3 = "$.job";
            String result3 = JsonPathUtils.matchJsonPathValue(jsonString, jsonPath3);
            assertEquals("null",result3);
        } catch (JsonProcessingException ignored) {

        }
    }
}
