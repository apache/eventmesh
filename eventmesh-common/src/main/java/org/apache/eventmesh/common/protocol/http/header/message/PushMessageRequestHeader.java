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
public class PushMessageRequestHeader extends Header {

    //request code
    private int code;

    //requester language description
    private String language;

    //protocol version adopted by requester, default:1.0
    private ProtocolVersion version;

    private String eventMeshCluster;

    private String eventMeshIp;

    private String eventMeshEnv;

    private String eventMeshIdc;

    public static PushMessageRequestHeader buildHeader(final Map<String, Object> headerParam) {
        HttpConvertsUtils httpConvertsUtils = new HttpConvertsUtils();
        PushMessageRequestHeader header = new PushMessageRequestHeader();
        ProtocolKey.EventMeshInstanceKey eventMeshInstanceKey = new ProtocolKey.EventMeshInstanceKey();
        return (PushMessageRequestHeader) httpConvertsUtils.httpHeaderConverts(header, headerParam, eventMeshInstanceKey);
    }

    @Override
    public Map<String, Object> toMap() {
        HttpConvertsUtils httpConvertsUtils = new HttpConvertsUtils();
        ProtocolKey protocolKey = new ProtocolKey();
        ProtocolKey.EventMeshInstanceKey eventMeshInstanceKey = new ProtocolKey.EventMeshInstanceKey();
        return httpConvertsUtils.httpMapConverts(this, protocolKey, eventMeshInstanceKey);
    }

}
