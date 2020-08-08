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

package cn.webank.eventmesh.common.protocol.http.body.client;

import cn.webank.eventmesh.common.protocol.http.body.Body;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegRequestBody extends Body {

    public static final String CLIENTTYPE = "clientType";

    public static final String TOPICS = "topics";

    public static final String ENDPOINT = "endpoint";

    private String clientType;

    private String endPoint;

    private List<String> topics;

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(String endPoint) {
        this.endPoint = endPoint;
    }

    public static RegRequestBody buildBody(Map<String, Object> bodyParam) {
        RegRequestBody body = new RegRequestBody();
        body.setClientType(MapUtils.getString(bodyParam, CLIENTTYPE));
        body.setEndPoint(MapUtils.getString(bodyParam, ENDPOINT));
        body.setTopics(JSONArray.parseArray(MapUtils.getString(bodyParam, TOPICS), String.class));
        return body;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(CLIENTTYPE, clientType);
        map.put(ENDPOINT, endPoint);
        map.put(TOPICS, JSON.toJSONString(topics));
        return map;
    }

    @Override
    public String toString() {
        return "regRequestBody{" +
                "clientType='" + clientType + '\'' +
                ", endPoint='" + endPoint + '\'' +
                ", topics=" + topics +
                '}';
    }
}
