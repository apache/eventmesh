/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.eventmesh.runtime.core.protocol.http.processor.inf;

import java.util.Date;

import com.alibaba.fastjson.JSONObject;

import org.apache.commons.lang3.StringUtils;

public class Client {

    public String env;

    public String idc;

    public String consumerGroup;

    public String topic;

    public String url;

    public String sys;

    public String ip;

    public String pid;

    public String hostname;

    public String apiVersion;

    public Date lastUpTime;

    public static Client buildClientFromJSONObject(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }

        Client client = null;
        try {
            client = new Client();

            client.env = StringUtils.trim(jsonObject.getString("env"));
            client.consumerGroup = StringUtils.trim(jsonObject.getString("groupName"));
            client.topic = StringUtils.trim(jsonObject.getString("topic"));
            client.url = StringUtils.trim(jsonObject.getString("url"));
            client.sys = StringUtils.trim(jsonObject.getString("sys"));
            client.idc = StringUtils.trim(jsonObject.getString("idc"));
            client.ip = StringUtils.trim(jsonObject.getString("ip"));
            client.pid = StringUtils.trim(jsonObject.getString("pid"));
            client.hostname = StringUtils.trim(jsonObject.getString("hostname"));
            client.apiVersion = StringUtils.trim(jsonObject.getString("apiversion"));
        } catch (Exception ex) {
        }
        return client;
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
}

