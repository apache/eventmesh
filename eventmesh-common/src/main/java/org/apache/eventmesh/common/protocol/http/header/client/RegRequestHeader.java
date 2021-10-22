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

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;

import java.util.HashMap;
import java.util.Map;

public class RegRequestHeader extends Header {

    private String code;

    private String language;

    private ProtocolVersion version;

    private String env;

    private String idc;

    private String sys;

    private String pid;

    private String ip;

    private String username;

    private String passwd;

    public static RegRequestHeader buildHeader(Map<String, Object> headerParam) {
        RegRequestHeader header = new RegRequestHeader();
        header.setCode(MapUtils.getString(headerParam, ProtocolKey.REQUEST_CODE));
        header.setVersion(ProtocolVersion.get(MapUtils.getString(headerParam, ProtocolKey.VERSION)));
        String lan = StringUtils.isBlank(MapUtils.getString(headerParam, ProtocolKey.LANGUAGE))
                ? Constants.LANGUAGE_JAVA : MapUtils.getString(headerParam, ProtocolKey.LANGUAGE);
        header.setLanguage(lan);
        header.setEnv(MapUtils.getString(headerParam, ProtocolKey.ClientInstanceKey.ENV));
        header.setIdc(MapUtils.getString(headerParam, ProtocolKey.ClientInstanceKey.IDC));
        header.setSys(MapUtils.getString(headerParam, ProtocolKey.ClientInstanceKey.SYS));
        header.setPid(MapUtils.getString(headerParam, ProtocolKey.ClientInstanceKey.PID));
        header.setIp(MapUtils.getString(headerParam, ProtocolKey.ClientInstanceKey.IP));
        header.setUsername(MapUtils.getString(headerParam, ProtocolKey.ClientInstanceKey.USERNAME));
        header.setPasswd(MapUtils.getString(headerParam, ProtocolKey.ClientInstanceKey.PASSWD));
        return header;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getIdc() {
        return idc;
    }

    public void setIdc(String idc) {
        this.idc = idc;
    }

    public String getSys() {
        return sys;
    }

    public void setSys(String sys) {
        this.sys = sys;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswd() {
        return passwd;
    }

    public void setPasswd(String passwd) {
        this.passwd = passwd;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(ProtocolKey.REQUEST_CODE, code);
        map.put(ProtocolKey.LANGUAGE, language);
        map.put(ProtocolKey.VERSION, version);
        map.put(ProtocolKey.ClientInstanceKey.ENV, env);
        map.put(ProtocolKey.ClientInstanceKey.IDC, idc);
        map.put(ProtocolKey.ClientInstanceKey.SYS, sys);
        map.put(ProtocolKey.ClientInstanceKey.PID, pid);
        map.put(ProtocolKey.ClientInstanceKey.IP, ip);
        map.put(ProtocolKey.ClientInstanceKey.USERNAME, username);
        map.put(ProtocolKey.ClientInstanceKey.PASSWD, passwd);
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("regRequestHeader={")
                .append("code=").append(code).append(",")
                .append("language=").append(language).append(",")
                .append("version=").append(version).append(",")
                .append("env=").append(env).append(",")
                .append("idc=").append(idc).append(",")
                .append("sys=").append(sys).append(",")
                .append("pid=").append(pid).append(",")
                .append("ip=").append(ip).append(",")
                .append("username=").append(username).append(",")
                .append("passwd=").append(passwd).append("}");
        return sb.toString();
    }
}
