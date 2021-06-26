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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;

public class PushMessageResponseHeader extends Header {

    //响应码
    private int code;

    //请求方语言描述
    private String language;

    //请求方采用的协议版本, 默认1.0
    private ProtocolVersion version;

    //请求方所在环境编号
    private String env;

    //请求方所在IDC
    private String idc;

    //请求方的子系统
    private String sys;

    //请求方的进程号
    private String pid;

    //请求方的IP
    private String ip;

    //请求方的USERNAME
    private String username = "username";

    //请求方的PASSWD
    private String passwd = "user@123";

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

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
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

    public static PushMessageResponseHeader buildHeader(int requestCode, String clientEnv, String clientIDC,
                                                        String clientSysId, String clientPid, String clientIP) {
        PushMessageResponseHeader pushMessageResponseHeader = new PushMessageResponseHeader();
        pushMessageResponseHeader.setCode(requestCode);
        pushMessageResponseHeader.setVersion(ProtocolVersion.V1);
        pushMessageResponseHeader.setLanguage(Constants.LANGUAGE_JAVA);
        pushMessageResponseHeader.setEnv(clientEnv);
        pushMessageResponseHeader.setIdc(clientIDC);
        pushMessageResponseHeader.setSys(clientSysId);
        pushMessageResponseHeader.setPid(clientPid);
        pushMessageResponseHeader.setIp(clientIP);
        return pushMessageResponseHeader;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("pushMessageResponseHeader={")
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
}
