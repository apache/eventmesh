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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ReplyMessageRequestHeaderTest {

    private ReplyMessageRequestHeader header;

    @Before
    public void before() {
        Map<String, Object> headerParam = new HashMap<>();
        headerParam.put(ProtocolKey.REQUEST_CODE, "200");
        headerParam.put(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        headerParam.put(ProtocolKey.VERSION, "1.0");
        headerParam.put(ProtocolKey.ClientInstanceKey.ENV.getKey(), "DEV");
        headerParam.put(ProtocolKey.ClientInstanceKey.IDC.getKey(), "IDC");
        headerParam.put(ProtocolKey.ClientInstanceKey.SYS.getKey(), "SYS");
        headerParam.put(ProtocolKey.ClientInstanceKey.PID.getKey(), "PID");
        headerParam.put(ProtocolKey.ClientInstanceKey.IP.getKey(), "127.0.0.1");
        header = ReplyMessageRequestHeader.buildHeader(headerParam);
    }

    @Test
    public void testToMap() {
        Assert.assertThat(header.toMap().get(ProtocolKey.REQUEST_CODE), is("200"));
        Assert.assertThat(header.toMap().get(ProtocolKey.LANGUAGE), is(Constants.LANGUAGE_JAVA));
        Assert.assertThat(header.toMap().get(ProtocolKey.VERSION), is(ProtocolVersion.V1));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.ENV.getKey()), is("DEV"));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.IDC.getKey()), is("IDC"));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.SYS.getKey()), is("SYS"));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.PID.getKey()), is("PID"));
        Assert.assertThat(header.toMap().get(ProtocolKey.ClientInstanceKey.IP.getKey()), is("127.0.0.1"));
    }
}
