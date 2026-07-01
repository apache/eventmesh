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

package org.apache.eventmesh.common.protocol.http.header.message;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PushMessageRequestHeaderTest {

    private PushMessageRequestHeader header;

    @BeforeEach
    public void before() {
        Map<String, Object> headerParam = new HashMap<>();
        headerParam.put(ProtocolKey.REQUEST_CODE, 200);
        headerParam.put(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        headerParam.put(ProtocolKey.VERSION, "1.0");
        headerParam.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER, "default cluster");
        headerParam.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, "127.0.0.1");
        headerParam.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV, "DEV");
        headerParam.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC, "IDC");
        header = PushMessageRequestHeader.buildHeader(headerParam);
    }

    @Test
    public void testToMap() {
        Assertions.assertEquals(200, header.toMap().get(ProtocolKey.REQUEST_CODE));
        Assertions.assertEquals(Constants.LANGUAGE_JAVA, header.toMap().get(ProtocolKey.LANGUAGE));
        Assertions.assertEquals(ProtocolVersion.V1, header.toMap().get(ProtocolKey.VERSION));
        Assertions.assertEquals("default cluster", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER));
        Assertions.assertEquals("127.0.0.1", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP));
        Assertions.assertEquals("DEV", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV));
        Assertions.assertEquals("IDC", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC));
    }
}
