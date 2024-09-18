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

package org.apache.eventmesh.function.filter;

import org.apache.eventmesh.function.filter.pattern.Pattern;
import org.apache.eventmesh.function.filter.patternbuild.PatternBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PatternTest {

    private final String event = "{\n"
        + "\"id\": \"4b26115b-73e-cf74a******\",\n"
        + "        \"specversion\": \"1.0\",\n"
        + "\"source\": \"eventmesh.source\",\n"
        + "\"type\": \"object:put\",\n"
        + "\"datacontenttype\": \"application/json\",\n"
        + "\"subject\": \"xxx.jpg\",\n"
        + "\"time\": \"2022-01-17T12:07:48.955Z\",\n"
        + "\"data\": {\n"
        + "\"name\": \"test01\",\n"
        + "\"state\": \"enable\",\n"
        + "\"num\": 10 ,\n"
        + "\"num1\": 50.7 \n"
        + "}\n"
        + "    }";

    @Test
    public void testSpecifiedFilter() {
        String condition = "{\n"
            + "    \"source\":[\n"
            + "        {\n"
            + "            \"prefix\":\"eventmesh.\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(event);
        Assertions.assertEquals(true, res);
    }

    @Test
    public void testPrefixFilter() {
        String condition = "{\n"
            + "    \"source\":[\n"
            + "        {\n"
            + "            \"prefix\":\"eventmesh.\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(event);
        Assertions.assertEquals(true, res);
    }

    @Test
    public void testSuffixFilter() {
        String condition = "{\n"
            + "    \"subject\":[\n"
            + "        {\n"
            + "            \"suffix\":\".jpg\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(event);
        Assertions.assertEquals(true, res);
    }

    @Test
    public void testNumericFilter() {
        String condition = "{\n"
            + "    \"data\":{\n"
            + "        \"num\":[\n"
            + "            {\n"
            + "                \"numeric\":[\n"
            + "                    \">\",\n"
            + "                    0,\n"
            + "                    \"<=\",\n"
            + "                    10\n"
            + "                ]\n"
            + "            }\n"
            + "        ],\n"
            + "        \"num1\":[\n"
            + "            {\n"
            + "                \"numeric\":[\n"
            + "                    \"=\",\n"
            + "                    50.7\n"
            + "                ]\n"
            + "            }\n"
            + "        ]\n"
            + "    }\n"
            + "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(event);
        Assertions.assertEquals(true, res);
    }

    @Test
    public void testExistsFilter() {
        String condition = "{\n"
            + "    \"data\":{\n"
            + "        \"state\":[\n"
            + "            {\n"
            + "               \"exists\": false\n"
            + "            }\n"
            + "         ]\n"
            + "    }\n"
            + "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(event);
        Assertions.assertEquals(false, res);
    }

    @Test
    public void testAnythingButFilter() {
        String condition = "{\n"
            + "    \"data\":{\n"
            + "        \"state\":[\n"
            + "            {\n"
            + "               \"anything-but\": \"enable\"\n"
            + "            }\n"
            + "         ]\n"
            + "    }\n"
            + "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(event);
        Assertions.assertEquals(false, res);
    }

    @Test
    public void testPrefixFilterMap() {
        // Create the inner Map representing {prefix=eventmesh.}
        Map<String, String> innerMap = new HashMap<>();
        innerMap.put("prefix", "eventmesh.");
        // Create a List representing [{prefix=eventmesh.}]
        List<Map<String, String>> sourceList = Collections.singletonList(innerMap);
        // Create the condition representing {source=[{prefix=eventmesh.}]}
        Map<String, Object> condition = new HashMap<>();
        condition.put("source", sourceList);

        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(event);
        Assertions.assertEquals(true, res);
    }

}
