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

package org.apache.eventmesh.common.protocol.http.header.client;

import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.header.Header;

import org.junit.jupiter.api.Assertions;

public class AbstractResponseHeaderTest {

    public void assertMapContent(Header header) {
        Assertions.assertTrue(header.toMap().containsKey(ProtocolKey.REQUEST_CODE));
        Assertions.assertTrue(header.toMap().containsKey(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER));
        Assertions.assertTrue(header.toMap().containsKey(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP));
        Assertions.assertTrue(header.toMap().containsKey(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV));
        Assertions.assertTrue(header.toMap().containsKey(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC));
        Assertions.assertEquals(200, header.toMap().get(ProtocolKey.REQUEST_CODE));
        Assertions.assertEquals("CLUSTER", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER));
        Assertions.assertEquals("127.0.0.1", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP));
        Assertions.assertEquals("DEV", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV));
        Assertions.assertEquals("IDC", header.toMap().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC));
    }
}
