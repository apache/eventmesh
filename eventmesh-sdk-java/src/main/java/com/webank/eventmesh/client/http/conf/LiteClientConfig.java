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

package com.webank.eventmesh.client.http.conf;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

public class LiteClientConfig {

    private String liteProxyAddr = "127.0.0.1:10105";

    private String forwardAgents = "127.0.0.1:3128";

    private boolean registryEnabled = Boolean.FALSE;

    private String registryAddr = "http://127.0.0.1:8090";

    private int registryFetchIntervel = 30000;

    private int consumeThreadCore = 2;

    private int consumeThreadMax = 5;

    private String env;

    private String region;

    private String idc;

    private String dcn;

    private String ip = "127.0.0.1";

    private String pid;

    private String sys;

    private String userName = "userName";

    private String password = "password";

    public int getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public LiteClientConfig setConsumeThreadMax(int consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
        return this;
    }

    public String getForwardAgents() {
        return forwardAgents;
    }

    public LiteClientConfig setForwardAgents(String forwardAgents) {
        this.forwardAgents = forwardAgents;
        return this;
    }

    public int getConsumeThreadCore() {
        return consumeThreadCore;
    }

    public LiteClientConfig setConsumeThreadCore(int consumeThreadCore) {
        this.consumeThreadCore = consumeThreadCore;
        return this;
    }

    public String getEnv() {
        return env;
    }

    public LiteClientConfig setEnv(String env) {
        this.env = env;
        return this;
    }

    public String getRegion() {
        return region;
    }

    public LiteClientConfig setRegion(String region) {
        this.region = region;
        return this;
    }

    public String getIdc() {
        return idc;
    }

    public LiteClientConfig setIdc(String idc) {
        this.idc = idc;
        return this;
    }

    public String getDcn() {
        return dcn;
    }

    public LiteClientConfig setDcn(String dcn) {
        this.dcn = dcn;
        return this;
    }

    public String getIp() {
        return ip;
    }

    public LiteClientConfig setIp(String ip) {
        this.ip = ip;
        return this;
    }

    public String getPid() {
        return pid;
    }

    public LiteClientConfig setPid(String pid) {
        this.pid = pid;
        return this;
    }

    public String getSys() {
        return sys;
    }

    public LiteClientConfig setSys(String sys) {
        this.sys = sys;
        return this;
    }

    public String getUserName() {
        return userName;
    }

    public LiteClientConfig setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public LiteClientConfig setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getLiteProxyAddr() {
        return liteProxyAddr;
    }

    public LiteClientConfig setLiteProxyAddr(String liteProxyAddr) {
        this.liteProxyAddr = liteProxyAddr;
        return this;
    }

    public boolean isRegistryEnabled() {
        return registryEnabled;
    }

    public LiteClientConfig setRegistryEnabled(boolean registryEnabled) {
        this.registryEnabled = registryEnabled;
        return this;
    }

    public String getRegistryAddr() {
        return registryAddr;
    }

    public LiteClientConfig setRegistryAddr(String registryAddr) {
        this.registryAddr = registryAddr;
        return this;
    }

    public int getRegistryFetchIntervel() {
        return registryFetchIntervel;
    }

    public LiteClientConfig setRegistryFetchIntervel(int registryFetchIntervel) {
        this.registryFetchIntervel = registryFetchIntervel;
        return this;
    }

    public void validate() throws IllegalStateException {
        Preconditions.checkState((registryEnabled && StringUtils.isNotBlank(registryAddr))
                || (!registryEnabled && StringUtils.isNotBlank(liteProxyAddr)), "liteClientConfig[registryEnabled/registryAddr/liteServerAddr] invalid");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("liteClientConfig={")
                .append("liteProxyAddr=").append(liteProxyAddr).append(",")
                .append("registryEnabled=").append(registryEnabled).append(",")
                .append("registryAddr=").append(registryAddr).append(",")
                .append("registryFetchIntervel=").append(registryFetchIntervel).append(",")
                .append("consumeThreadCore=").append(consumeThreadCore).append(",")
                .append("consumeThreadMax=").append(consumeThreadMax).append(",")
                .append("env=").append(env).append(",")
                .append("region=").append(region).append(",")
                .append("idc=").append(idc).append(",")
                .append("dcn=").append(dcn).append(",")
                .append("ip=").append(ip).append(",")
                .append("pid=").append(pid).append(",")
                .append("sys=").append(sys).append(",")
                .append("userName=").append(userName).append(",")
                .append("password=").append(password).append("}");
        return sb.toString();
    }
}
