package org.apache.eventmesh.runtime.core.protocol.http.processor.inf;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;

public class Client {

    public String env;

    public String dcn;

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
            client.dcn = StringUtils.trim(jsonObject.getString("dcn"));
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
                .append(",dcn=").append(dcn)
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

