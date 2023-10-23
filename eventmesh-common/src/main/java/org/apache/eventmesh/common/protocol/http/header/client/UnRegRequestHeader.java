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
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.utils.HttpConvertsUtils;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class UnRegRequestHeader extends Header {

    // request code
    private String code;

    // requester language description
    private String language;

    // protocol version adopted by requester, default:1.0
    private ProtocolVersion version;

    // the environment number of the requester
    private String env;

    // the IDC of the requester
    private String idc;

    // subsystem of the requester
    private String sys;

    // PID of the requester
    private String pid;

    // IP of the requester
    private String ip;

    // USERNAME of the requester
    private String username = "username";

    // PASSWD of the requester
    private String passwd = "user@123";

    public static UnRegRequestHeader buildHeader(Map<String, Object> headerParam) {
        HttpConvertsUtils httpConvertsUtils = new HttpConvertsUtils();
        UnRegRequestHeader header = new UnRegRequestHeader();
        return (UnRegRequestHeader) httpConvertsUtils.httpHeaderConverts(header, headerParam);
    }

    @Override
    public Map<String, Object> toMap() {
        HttpConvertsUtils httpConvertsUtils = new HttpConvertsUtils();
        ProtocolKey protocolKey = new ProtocolKey();
        return httpConvertsUtils.httpMapConverts(this, protocolKey);
    }

}
