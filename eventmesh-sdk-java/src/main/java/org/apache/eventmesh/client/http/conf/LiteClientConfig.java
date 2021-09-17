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

package org.apache.eventmesh.client.http.conf;

import org.apache.eventmesh.common.loadbalance.LoadBalanceType;

public class LiteClientConfig {

    /**
     * The event server address list
     * <p>
     * If it's a cluster, please use ; to split, and the address format is related to loadBalanceType.
     * <p>
     * E.g.
     * <p>If you use Random strategy, the format like: 127.0.0.1:10105;127.0.0.2:10105
     * <p>If you use weighted round robin or weighted random strategy, the format like: 127.0.0.1:10105:1;127.0.0.2:10105:2
     */
    private String liteEventMeshAddr = "127.0.0.1:10105";

    private LoadBalanceType loadBalanceType = LoadBalanceType.RANDOM;

    private int consumeThreadCore = 2;

    private int consumeThreadMax = 5;

    private String env;

    private String consumerGroup = "DefaultConsumerGroup";

    private String producerGroup = "DefaultProducerGroup";

    private String idc;

    private String ip = "127.0.0.1";

    private String pid;

    private String sys;

    private String userName = "userName";

    private String password = "password";

    private boolean useTls = false;

    public String getLiteEventMeshAddr() {
        return liteEventMeshAddr;
    }

    public LiteClientConfig setLiteEventMeshAddr(String liteEventMeshAddr) {
        this.liteEventMeshAddr = liteEventMeshAddr;
        return this;
    }

    public LoadBalanceType getLoadBalanceType() {
        return loadBalanceType;
    }

    public LiteClientConfig setLoadBalanceType(LoadBalanceType loadBalanceType) {
        this.loadBalanceType = loadBalanceType;
        return this;
    }

    public int getConsumeThreadCore() {
        return consumeThreadCore;
    }

    public LiteClientConfig setConsumeThreadCore(int consumeThreadCore) {
        this.consumeThreadCore = consumeThreadCore;
        return this;
    }

    public int getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public LiteClientConfig setConsumeThreadMax(int consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
        return this;
    }

    public String getEnv() {
        return env;
    }

    public LiteClientConfig setEnv(String env) {
        this.env = env;
        return this;
    }

    public String getIdc() {
        return idc;
    }

    public LiteClientConfig setIdc(String idc) {
        this.idc = idc;
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

    public boolean isUseTls() {
        return useTls;
    }

    public LiteClientConfig setUseTls(boolean useTls) {
        this.useTls = useTls;
        return this;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public LiteClientConfig setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
        return this;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public LiteClientConfig setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("liteClientConfig={")
                .append("liteEventMeshAddr=").append(liteEventMeshAddr).append(",")
                .append("loadBalanceType=").append(loadBalanceType).append(",")
                .append("consumeThreadCore=").append(consumeThreadCore).append(",")
                .append("consumeThreadMax=").append(consumeThreadMax).append(",")
                .append("env=").append(env).append(",")
                .append("idc=").append(idc).append(",")
                .append("producerGroup=").append(producerGroup).append(",")
                .append("consumerGroup=").append(consumerGroup).append(",")
                .append("ip=").append(ip).append(",")
                .append("pid=").append(pid).append(",")
                .append("sys=").append(sys).append(",")
                .append("userName=").append(userName).append(",")
                .append("password=").append(password).append(",")
                .append("useTls=").append(useTls).append("}");
        return sb.toString();
    }
}
