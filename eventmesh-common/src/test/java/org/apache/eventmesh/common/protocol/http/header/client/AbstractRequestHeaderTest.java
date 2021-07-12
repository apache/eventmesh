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
import org.junit.Assert;

public class AbstractRequestHeaderTest {

    public void assertMapContent(Header header) {
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.REQUEST_CODE));
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.LANGUAGE));
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.VERSION));
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.ClientInstanceKey.ENV));
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.ClientInstanceKey.IDC));
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.ClientInstanceKey.SYS));
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.ClientInstanceKey.PID));
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.ClientInstanceKey.IP));
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.ClientInstanceKey.USERNAME));
        Assert.assertTrue(header.toMap().containsKey(ProtocolKey.ClientInstanceKey.PASSWD));
    }
}
