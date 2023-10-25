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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PushMessageResponseHeaderTest {

    @Test
    public void testToMap() {
        PushMessageResponseHeader header = PushMessageResponseHeader.buildHeader(100, "DEV",
            "IDC", "SYSID", "PID", "127.0.0.1");
        Assertions.assertEquals(100, header.toMap().get(ProtocolKey.REQUEST_CODE));
        Assertions.assertEquals(Constants.LANGUAGE_JAVA, header.toMap().get(ProtocolKey.LANGUAGE));
        Assertions.assertEquals(ProtocolVersion.V1, header.toMap().get(ProtocolKey.VERSION));
        Assertions.assertEquals("DEV", header.toMap().get(ProtocolKey.ClientInstanceKey.ENV.getKey()));
        Assertions.assertEquals("IDC", header.toMap().get(ProtocolKey.ClientInstanceKey.IDC.getKey()));
        Assertions.assertEquals("SYSID", header.toMap().get(ProtocolKey.ClientInstanceKey.SYS.getKey()));
        Assertions.assertEquals("PID", header.toMap().get(ProtocolKey.ClientInstanceKey.PID.getKey()));
        Assertions.assertEquals("127.0.0.1", header.toMap().get(ProtocolKey.ClientInstanceKey.IP.getKey()));
    }
}
