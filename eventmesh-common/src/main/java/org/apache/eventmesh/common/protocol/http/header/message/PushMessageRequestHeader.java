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


import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;

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

    public String getEventMeshIdc() {
        return eventMeshIdc;
    }

    public void setEventMeshIdc(String eventMeshIdc) {
        this.eventMeshIdc = eventMeshIdc;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public ProtocolVersion getVersion() {
        return version;
    }

    public void setVersion(ProtocolVersion version) {
        this.version = version;
    }

    public static PushMessageRequestHeader buildHeader(final Map<String, Object> headerParam) {
        PushMessageRequestHeader pushMessageRequestHeader = new PushMessageRequestHeader();
        pushMessageRequestHeader.setCode(MapUtils.getIntValue(headerParam, ProtocolKey.REQUEST_CODE));
        pushMessageRequestHeader.setLanguage(MapUtils.getString(headerParam, ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA));
        pushMessageRequestHeader.setVersion(ProtocolVersion.get(MapUtils.getString(headerParam, ProtocolKey.VERSION)));
        pushMessageRequestHeader.setEventMeshCluster(MapUtils.getString(headerParam, ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER));
        pushMessageRequestHeader.setEventMeshIp(MapUtils.getString(headerParam, ProtocolKey.EventMeshInstanceKey.EVENTMESHIP));
        pushMessageRequestHeader.setEventMeshEnv(MapUtils.getString(headerParam, ProtocolKey.EventMeshInstanceKey.EVENTMESHENV));
        pushMessageRequestHeader.setEventMeshIdc(MapUtils.getString(headerParam, ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC));
        return pushMessageRequestHeader;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(ProtocolKey.REQUEST_CODE, code);
        map.put(ProtocolKey.LANGUAGE, language);
        map.put(ProtocolKey.VERSION, version);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER, eventMeshCluster);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, eventMeshIp);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV, eventMeshEnv);
        map.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC, eventMeshIdc);
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("pushMessageRequestHeader={")
                .append("code=").append(code).append(",")
                .append("language=").append(language).append(",")
                .append("version=").append(version.getVersion()).append(",")
                .append("eventMeshEnv=").append(eventMeshEnv).append(",")
                .append("eventMeshIdc=").append(eventMeshIdc).append(",")
                .append("eventMeshCluster=").append(eventMeshCluster).append(",")
                .append("eventMeshIp=").append(eventMeshIp).append("}");
        return sb.toString();
    }

}
