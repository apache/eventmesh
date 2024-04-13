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

import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReplyMessageResponseHeaderTest {

    @Test
    public void testToMap() {
        ReplyMessageResponseHeader header = ReplyMessageResponseHeader.buildHeader(100,
            "Cluster", "127.0.0.1", "DEV", "IDC");
        Assertions.assertEquals(100, header.toMap().get(ProtocolKey.REQUEST_CODE));
        Assertions.assertEquals("Cluster", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER));
        Assertions.assertEquals("127.0.0.1", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP));
        Assertions.assertEquals("DEV", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV));
        Assertions.assertEquals("IDC", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC));
    }
}
