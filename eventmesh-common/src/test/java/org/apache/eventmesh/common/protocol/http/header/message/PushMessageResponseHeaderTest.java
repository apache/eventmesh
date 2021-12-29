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

import static org.hamcrest.CoreMatchers.is;

import org.junit.Assert;
import org.junit.Test;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;

public class PushMessageResponseHeaderTest {

    @Test
    public void testToMap() {
        PushMessageResponseHeader header = PushMessageResponseHeader.buildHeader(100, "DEV",
                "IDC", "SYSID", "PID", "127.0.0.1");
        Assert.assertThat(header.toMap().get(ProtocolKey.REQUEST_CODE), is(100));
        Assert.assertThat(header.toMap().get(ProtocolKey.LANGUAGE), is(Constants.LANGUAGE_JAVA));
        Assert.assertThat(header.toMap().get(ProtocolKey.VERSION), is(ProtocolVersion.V1));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.ENV), is("DEV"));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.IDC), is("IDC"));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.SYS), is("SYSID"));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.PID), is("PID"));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.IP), is("127.0.0.1"));
    }
}
