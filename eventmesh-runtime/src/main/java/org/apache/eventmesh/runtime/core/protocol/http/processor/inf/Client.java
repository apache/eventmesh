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

package org.apache.eventmesh.runtime.core.protocol.http.processor.inf;

import java.util.Date;

public class Client {

    private String env;

    private String idc;

    private String consumerGroup;

    private String topic;

    private String url;

    private String sys;

    private String ip;

    private String pid;

    private String hostname;

    private Date lastUpTime;

    public void setEnv(String env) {
        this.env = env;
    }

    public void setIdc(String idc) {
        this.idc = idc;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSys(String sys) {
        this.sys = sys;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public void setLastUpTime(Date lastUpTime) {
        this.lastUpTime = lastUpTime;
    }

    private String apiVersion;

    public String getEnv() {
        return env;
    }

    public String getIdc() {
        return idc;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public String getTopic() {
        return topic;
    }

    public String getUrl() {
        return url;
    }

    public String getSys() {
        return sys;
    }

    public String getIp() {
        return ip;
    }

    public String getPid() {
        return pid;
    }

    public String getHostname() {
        return hostname;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public Date getLastUpTime() {
        return lastUpTime;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("endPoint={env=").append(env)
            .append(",idc=").append(idc)
            .append(",consumerGroup=").append(consumerGroup)
            .append(",topic=").append(topic)
            .append(",url=").append(url)
            .append(",sys=").append(sys)
            .append(",ip=").append(ip)
            .append(",pid=").append(pid)
            .append(",hostname=").append(hostname)
            .append(",apiVersion=").append(apiVersion)
            .append(",registerTime=").append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Client client = (Client) o;
        return java.util.Objects.equals(env, client.env)
            && java.util.Objects.equals(idc, client.idc)
            && java.util.Objects.equals(consumerGroup, client.consumerGroup)
            && java.util.Objects.equals(topic, client.topic)
            && java.util.Objects.equals(url, client.url)
            && java.util.Objects.equals(sys, client.sys)
            && java.util.Objects.equals(ip, client.ip)
            && java.util.Objects.equals(pid, client.pid)
            && java.util.Objects.equals(hostname, client.hostname)
            && java.util.Objects.equals(apiVersion, client.apiVersion);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(env, idc, consumerGroup, topic, url, sys, ip, pid, hostname, apiVersion);
    }
}
