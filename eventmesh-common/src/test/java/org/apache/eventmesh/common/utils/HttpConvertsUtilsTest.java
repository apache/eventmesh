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

import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey.EventMeshInstanceKey;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.stubs.HeaderStub;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HttpConvertsUtilsTest {
    private final HeaderStub headerStub = new HeaderStub();
    private final ProtocolKey mockedProtocolKey = new ProtocolKey();
    private final EventMeshInstanceKey mockedEventMeshProtocolKey = new EventMeshInstanceKey();

    @Test
    void httpMapConverts() {
        Map<String, Object> httpMapConverts = new HttpConvertsUtils().httpMapConverts(headerStub, mockedProtocolKey);
        Assertions.assertEquals(httpMapConverts.get(headerStub.code), headerStub.code);
    }

    @Test
    void testHttpMapConverts() {
        Map<String, Object> httpMapConverts = new HttpConvertsUtils().httpMapConverts(headerStub, mockedProtocolKey, mockedEventMeshProtocolKey);
        Assertions.assertEquals(httpMapConverts.get(headerStub.code), headerStub.code);
        Assertions.assertEquals(httpMapConverts.get(headerStub.eventmeshenv), headerStub.eventmeshenv);
    }

    @Test
    void httpHeaderConverts() {
        HashMap<String, Object> headerParams = new HashMap<>();
        String code = "test";
        headerParams.put("code", code);

        Header header = new HttpConvertsUtils().httpHeaderConverts(headerStub, headerParams);

        Assertions.assertEquals(code, header.toMap().get("code"));
    }

    @Test
    void testHttpHeaderConverts() {
        HashMap<String, Object> headerParams = new HashMap<>();
        String env = "test";
        headerParams.put("eventmeshenv", env);

        Header header = new HttpConvertsUtils().httpHeaderConverts(headerStub, headerParams, mockedEventMeshProtocolKey);

        Assertions.assertEquals(env, header.toMap().get("eventmeshenv"));
    }
}

