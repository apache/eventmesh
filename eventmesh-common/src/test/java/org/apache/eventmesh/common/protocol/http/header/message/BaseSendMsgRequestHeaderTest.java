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

import static org.junit.Assert.assertEquals;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class BaseSendMsgRequestHeaderTest {
    
    private TestBaseSendMsgRequestHeader baseSendMsgRequestHeader;
    
    @Before
    public void init() {
        baseSendMsgRequestHeader = new TestBaseSendMsgRequestHeader();
        baseSendMsgRequestHeader.setCode("200");
        baseSendMsgRequestHeader.setEnv("DEV");
        baseSendMsgRequestHeader.setIdc("IDC");
        baseSendMsgRequestHeader.setVersion(ProtocolVersion.V1);
        baseSendMsgRequestHeader.setUsername("admin");
        baseSendMsgRequestHeader.setSys("SYSID");
        baseSendMsgRequestHeader.setPid("PID");
        baseSendMsgRequestHeader.setPasswd("123456");
        baseSendMsgRequestHeader.setLanguage(Constants.LANGUAGE_JAVA);
        baseSendMsgRequestHeader.setIp("127.0.0.1");
    }
    
    @Test
    public void testToMap() {
        Map<String, Object> map = baseSendMsgRequestHeader.toMap();
        assertEquals("200", map.get(ProtocolKey.REQUEST_CODE));
        assertEquals(Constants.LANGUAGE_JAVA, map.get(ProtocolKey.LANGUAGE));
        assertEquals(ProtocolVersion.V1, map.get(ProtocolKey.VERSION));
        assertEquals("DEV", map.get(ProtocolKey.ClientInstanceKey.ENV));
        assertEquals("IDC", map.get(ProtocolKey.ClientInstanceKey.IDC));
        assertEquals("SYSID", map.get(ProtocolKey.ClientInstanceKey.SYS));
        assertEquals("PID", map.get(ProtocolKey.ClientInstanceKey.PID));
        assertEquals("127.0.0.1", map.get(ProtocolKey.ClientInstanceKey.IP));
        assertEquals("admin", map.get(ProtocolKey.ClientInstanceKey.USERNAME));
        assertEquals("123456", map.get(ProtocolKey.ClientInstanceKey.PASSWD));
    }
    
    @Test
    public void testDoBuildHeader() {
        Map<String, Object> map = new HashMap<String, Object>() {
            {
                put(ProtocolKey.REQUEST_CODE, "200");
                put(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
                put(ProtocolKey.PROTOCOL_TYPE, "http");
                put(ProtocolKey.PROTOCOL_VERSION, "1.0");
                put(ProtocolKey.PROTOCOL_DESC, "desc");
                put(ProtocolKey.LANGUAGE, "GO");
                put(ProtocolKey.ClientInstanceKey.ENV, "DEV");
                put(ProtocolKey.ClientInstanceKey.IDC, "IDC");
                put(ProtocolKey.ClientInstanceKey.SYS, "SYSID");
                put(ProtocolKey.ClientInstanceKey.PID, "PID");
                put(ProtocolKey.ClientInstanceKey.IP, "127.0.0.1");
                put(ProtocolKey.ClientInstanceKey.USERNAME, "admin");
                put(ProtocolKey.ClientInstanceKey.PASSWD, "123456");
            }
        };
        baseSendMsgRequestHeader = new TestBaseSendMsgRequestHeader();
        BaseSendMsgRequestHeader.doBuildHeader(baseSendMsgRequestHeader, map);
        assertEquals("200", baseSendMsgRequestHeader.getCode());
        assertEquals(ProtocolVersion.V1, baseSendMsgRequestHeader.getVersion());
        assertEquals("http", baseSendMsgRequestHeader.getProtocolType());
        assertEquals("1.0", baseSendMsgRequestHeader.getProtocolVersion());
        assertEquals("desc", baseSendMsgRequestHeader.getProtocolDesc());
        assertEquals("GO", baseSendMsgRequestHeader.getLanguage());
        assertEquals("DEV", baseSendMsgRequestHeader.getEnv());
        assertEquals("IDC", baseSendMsgRequestHeader.getIdc());
        assertEquals("SYSID", baseSendMsgRequestHeader.getSys());
        assertEquals("PID", baseSendMsgRequestHeader.getPid());
        assertEquals("127.0.0.1", baseSendMsgRequestHeader.getIp());
        assertEquals("admin", baseSendMsgRequestHeader.getUsername());
        assertEquals("123456", baseSendMsgRequestHeader.getPasswd());
    }
    
    class TestBaseSendMsgRequestHeader extends BaseSendMsgRequestHeader{
        
    }
}
