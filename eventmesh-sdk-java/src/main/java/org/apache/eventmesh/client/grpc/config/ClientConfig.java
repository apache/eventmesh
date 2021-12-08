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

package org.apache.eventmesh.client.grpc.config;

public class ClientConfig {

    private String serverAddr = "";

    private int serverPort = 0;

    private String env;

    private String consumerGroup = "DefaultConsumerGroup";

    private String producerGroup = "DefaultProducerGroup";

    private String idc;

    private String ip = "127.0.0.1";

    private String pid;

    private String sys;

    private String userName;

    private String password;

    private boolean useTls = false;

    public ClientConfig setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
        return this;
    }

    public String getServerAddr() {
        return this.serverAddr;
    }

    public ClientConfig setServerPort(int serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    public int getServerPort() {
        return this.serverPort;
    }

    public String getEnv() {
        return env;
    }

    public ClientConfig setEnv(String env) {
        this.env = env;
        return this;
    }

    public String getIdc() {
        return idc;
    }

    public ClientConfig setIdc(String idc) {
        this.idc = idc;
        return this;
    }

    public String getIp() {
        return ip;
    }

    public ClientConfig setIp(String ip) {
        this.ip = ip;
        return this;
    }

    public String getPid() {
        return pid;
    }

    public ClientConfig setPid(String pid) {
        this.pid = pid;
        return this;
    }

    public String getSys() {
        return sys;
    }

    public ClientConfig setSys(String sys) {
        this.sys = sys;
        return this;
    }

    public String getUserName() {
        return userName;
    }

    public ClientConfig setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public ClientConfig setPassword(String password) {
        this.password = password;
        return this;
    }

    public boolean isUseTls() {
        return useTls;
    }

    public ClientConfig setUseTls(boolean useTls) {
        this.useTls = useTls;
        return this;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public ClientConfig setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
        return this;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public ClientConfig setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ClientConfig={")
                .append("ServerAddr=").append(serverAddr).append(",")
                .append("ServerPort=").append(serverPort).append(",")
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
