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

package com.webank.eventmesh.common.protocol.http.body.client;

import com.webank.eventmesh.common.protocol.http.body.Body;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeartbeatRequestBody extends Body {

    public static final String CLIENTTYPE = "clientType";
    public static final String HEARTBEATENTITIES = "heartbeatEntities";


    private String clientType;

    private List<HeartbeatEntity> heartbeatEntities;

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public List<HeartbeatEntity> getHeartbeatEntities() {
        return heartbeatEntities;
    }

    public void setHeartbeatEntities(List<HeartbeatEntity> heartbeatEntities) {
        this.heartbeatEntities = heartbeatEntities;
    }

    public static HeartbeatRequestBody buildBody(Map<String, Object> bodyParam) {
        HeartbeatRequestBody body = new HeartbeatRequestBody();
        body.setClientType(MapUtils.getString(bodyParam, CLIENTTYPE));
        body.setHeartbeatEntities(JSONArray.parseArray(MapUtils.getString(bodyParam, HEARTBEATENTITIES), HeartbeatEntity.class));
        return body;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(CLIENTTYPE, clientType);
        map.put(HEARTBEATENTITIES, JSON.toJSONString(heartbeatEntities));
        return map;
    }

    public static class HeartbeatEntity {
        public String topic;
        public String serviceId;
        public String instanceId;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("heartbeatEntity={")
                    .append("topic=").append(topic).append(",")
                    .append("heartbeatEntities=").append(serviceId).append(",")
                    .append("instanceId=").append(instanceId).append("}");
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("heartbeatRequestBody={")
                .append("clientType=").append(clientType).append("}");
        return sb.toString();
    }
}
