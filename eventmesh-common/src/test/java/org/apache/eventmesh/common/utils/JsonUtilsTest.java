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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

public class JsonUtilsTest {

    @Test
    public void toJSONString() {
        Map<String, String> map = new HashMap<>();
        String jsonString = JsonUtils.toJSONString(map);
        Assertions.assertEquals("{}", jsonString);
        map.put("mxsm", "2");
        jsonString = JsonUtils.toJSONString(map);
        Assertions.assertEquals("{\"mxsm\":\"2\"}", jsonString);

        Map<String, Object> maps = new HashMap<>();
        maps.put("mxsm", LocalDate.of(2013, 6, 28));
        jsonString = JsonUtils.toJSONString(maps);
        Assertions.assertEquals("{\"mxsm\":\"2013-06-28\"}", jsonString);
    }

    @Test
    public void testToBytes() {
        Map<String, String> map = new HashMap<>();
        map.put("mxsm", "2");
        Assertions.assertArrayEquals("{\"mxsm\":\"2\"}".getBytes(StandardCharsets.UTF_8), JsonUtils.toJSONBytes(map));
    }

    @Test
    public void testParseObject() {

        String json = "{\"mxsm\":\"2\",\"date\":\"2022-02-12 21:36:01\"}";
        Map<String, String> map = JsonUtils.parseTypeReferenceObject(json, new TypeReference<Map<String, String>>() {

        });
        Assertions.assertNotNull(map);
        Assertions.assertEquals("2", map.get("mxsm"));
        EventMesh mxsm = JsonUtils.parseObject(json, EventMesh.class);
        Assertions.assertNotNull(mxsm);
        Assertions.assertEquals("2", mxsm.mxsm);
        Assertions.assertEquals(new GregorianCalendar(2022, 1, 12, 21, 36, 01).getTime().getTime(), mxsm.date.getTime());
        EventMesh mxsm1 = JsonUtils.parseObject(json.getBytes(StandardCharsets.UTF_8), EventMesh.class);
        Assertions.assertNotNull(mxsm1);
        Assertions.assertEquals("2", mxsm1.mxsm);
    }

    @Test
    public void getJsonNode() {
        String json = "{\"mxsm\":\"2\",\"date\":\"2022-02-12 21:36:01\"}";
        JsonNode jsonNode = JsonUtils.getJsonNode(json);
        Assertions.assertNotNull(jsonNode);
        Assertions.assertEquals("2", jsonNode.findValue("mxsm").asText());
    }

    @Data
    public static class EventMesh {

        private String mxsm;

        private Date date;
    }
}
