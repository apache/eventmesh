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

package org.apache.eventmesh.common.protocol.http;

import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.netty.handler.codec.http.DefaultFullHttpResponse;

@ExtendWith(MockitoExtension.class)
public class HttpEventWrapperTest {

    private HttpEventWrapper httpEventWrapper;

    @BeforeEach
    public void setUp() {
        httpEventWrapper = new HttpEventWrapper("POST", "1.1", "hello");
    }

    @Test
    public void testCreateHttpResponse() {
        HashMap<String, Object> headMap = new HashMap<>();
        headMap.put("String", "responseHeaderMap");
        HashMap<String, Object> responseBodyMap = new HashMap<>();
        responseBodyMap.put("String", "responseBodyMap");
        HttpEventWrapper result = httpEventWrapper.createHttpResponse(headMap, responseBodyMap);
        Assertions.assertEquals("1.1", result.getHttpVersion());
        Assertions.assertEquals("POST", result.getHttpMethod());
        Assertions.assertEquals("hello", result.getRequestURI());
        Assertions.assertEquals("responseHeaderMap", result.getHeaderMap().get("String"));
        Map responseMap = JsonUtils.parseObject(new String(result.getBody()), Map.class);
        Assertions.assertEquals("responseBodyMap", responseMap.get("String"));
    }

    @Test
    public void testCreateHttpResponse2() {
        HttpEventWrapper result = httpEventWrapper.createHttpResponse(EventMeshRetCode.SUCCESS);
        Map responseMap = JsonUtils.parseObject(new String(result.getBody()), Map.class);
        Assertions.assertEquals(EventMeshRetCode.SUCCESS.getRetCode(), responseMap.get("retCode"));
        Assertions.assertEquals(EventMeshRetCode.SUCCESS.getErrMsg(), responseMap.get("retMessage"));
    }

    @Test
    public void testGetBody() {
        byte[] bodyArray = new byte[]{'0'};
        httpEventWrapper.setBody(bodyArray);
        byte[] result = httpEventWrapper.getBody();
        Assertions.assertNotNull(result);
        Assertions.assertEquals(result[0], '0');
    }

    @Test
    public void testSetBody() {
        httpEventWrapper.setBody(new byte[]{(byte) 0});
    }

    @Test
    public void testHttpResponse() throws Exception {
        httpEventWrapper.setBody(new byte[]{(byte) 0});
        DefaultFullHttpResponse result = httpEventWrapper.httpResponse();
        Assertions.assertNotNull(result);
    }

    @Test
    public void testBuildSysHeaderForClient() {
        httpEventWrapper.buildSysHeaderForClient();
    }

    @Test
    public void testBuildSysHeaderForCE() {
        httpEventWrapper.buildSysHeaderForCE();
    }
}
