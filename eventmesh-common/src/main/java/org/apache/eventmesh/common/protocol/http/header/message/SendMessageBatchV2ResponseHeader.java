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
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.utils.HttpConvertsUtils;
import org.apache.eventmesh.common.utils.IPUtils;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class SendMessageBatchV2ResponseHeader extends Header {

    // response code, as same as the request code
    private int code;

    // The cluster name of the EventMesh that processes the request
    private String eventMeshCluster;

    // IP of the EventMesh that processes the request
    private String eventMeshIp;

    // Environment number of the EventMesh that processes the request
    private String eventMeshEnv;

    // IDC of the EventMesh that processes the request
    private String eventMeshIdc;

    public static SendMessageBatchV2ResponseHeader buildHeader(Integer requestCode, String eventMeshCluster,
                                                               String eventMeshEnv, String eventMeshIDC) {
        SendMessageBatchV2ResponseHeader header = new SendMessageBatchV2ResponseHeader();
        header.setCode(requestCode);
        header.setEventMeshCluster(eventMeshCluster);
        header.setEventMeshEnv(eventMeshEnv);
        header.setEventMeshIdc(eventMeshIDC);
        header.setEventMeshIp(IPUtils.getLocalAddress());
        return header;
    }

    @Override
    public Map<String, Object> toMap() {
        HttpConvertsUtils httpConvertsUtils = new HttpConvertsUtils();
        ProtocolKey protocolKey = new ProtocolKey();
        ProtocolKey.EventMeshInstanceKey eventMeshInstanceKey = new ProtocolKey.EventMeshInstanceKey();
        return httpConvertsUtils.httpMapConverts(this, protocolKey, eventMeshInstanceKey);
    }
}
