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

import java.util.HashMap;
import java.util.Map;

public class HeartbeatResponseHeader extends Header {

    private int code;

    private String eventMeshCluster;

    private String eventMeshIp;

    private String eventMeshEnv;

    private String eventMeshRegion;

    private String eventMeshIdc;

    private String eventMeshDcn;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getEventMeshCluster() {
        return eventMeshCluster;
    }

    public void setEventMeshCluster(String eventMeshCluster) {
        this.eventMeshCluster = eventMeshCluster;
    }

    public String getEventMeshIp() {
        return eventMeshIp;
    }

    public void setEventMeshIp(String eventMeshIp) {
        this.eventMeshIp = eventMeshIp;
    }

    public String getEventMeshEnv() {
        return eventMeshEnv;
    }

    public void setEventMeshEnv(String eventMeshEnv) {
        this.eventMeshEnv = eventMeshEnv;
    }

    public String getEventMeshRegion() {
        return eventMeshRegion;
    }

    public void setEventMeshRegion(String eventMeshRegion) {
        this.eventMeshRegion = eventMeshRegion;
    }

    public String getEventMeshIdc() {
        return eventMeshIdc;
    }

    public void setEventMeshIdc(String eventMeshIdc) {
        this.eventMeshIdc = eventMeshIdc;
    }

    public String getEventMeshDcn() {
        return eventMeshDcn;
    }

    public void setEventMeshDcn(String eventMeshDcn) {
        this.eventMeshDcn = eventMeshDcn;
    }

    public static HeartbeatResponseHeader buildHeader(Integer requestCode, String eventMeshCluster,
                                                String eventMeshIp, String eventMeshEnv, String eventMeshRegion,
                                                      String eventMeshDcn, String eventMeshIDC) {
        HeartbeatResponseHeader heartbeatResponseHeader = new HeartbeatResponseHeader();
        heartbeatResponseHeader.setCode(requestCode);
        heartbeatResponseHeader.setEventMeshCluster(eventMeshCluster);
        heartbeatResponseHeader.setEventMeshDcn(eventMeshDcn);
        heartbeatResponseHeader.setEventMeshIp(eventMeshIp);
        heartbeatResponseHeader.setEventMeshEnv(eventMeshEnv);
        heartbeatResponseHeader.setEventMeshRegion(eventMeshRegion);
        heartbeatResponseHeader.setEventMeshIdc(eventMeshIDC);
        return heartbeatResponseHeader;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("heartbeatResponseHeader={")
                .append("code=").append(code).append(",")
                .append("eventMeshEnv=").append(eventMeshEnv).append(",")
                .append("eventMeshRegion=").append(eventMeshRegion).append(",")
                .append("eventMeshIdc=").append(eventMeshIdc).append(",")
                .append("eventMeshDcn=").append(eventMeshDcn).append(",")
                .append("eventMeshCluster=").append(eventMeshCluster).append(",")
                .append("eventMeshIp=").append(eventMeshIp).append("}");
        return sb.toString();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(ProtocolKey.REQUEST_CODE, code);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER, eventMeshCluster);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, eventMeshIp);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV, eventMeshEnv);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHREGION, eventMeshRegion);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC, eventMeshIdc);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHDCN, eventMeshDcn);
        return map;
    }
}
