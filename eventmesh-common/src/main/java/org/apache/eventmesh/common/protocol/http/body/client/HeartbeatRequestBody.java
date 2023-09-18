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

package org.apache.eventmesh.common.protocol.http.body.client;

import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString
public class HeartbeatRequestBody extends Body {

    public static final String CLIENTTYPE = "clientType";
    public static final String HEARTBEATENTITIES = "heartbeatEntities";
    public static final String CONSUMERGROUP = "consumerGroup";

    private String consumerGroup;

    private String clientType;

    private List<HeartbeatEntity> heartbeatEntities;

    public static HeartbeatRequestBody buildBody(Map<String, Object> bodyParam) {
        HeartbeatRequestBody body = new HeartbeatRequestBody();
        body.setClientType(MapUtils.getString(bodyParam, CLIENTTYPE));
        body.setConsumerGroup(MapUtils.getString(bodyParam, CONSUMERGROUP));
        body.setHeartbeatEntities(JsonUtils.parseTypeReferenceObject(MapUtils.getString(bodyParam, HEARTBEATENTITIES),
                new TypeReference<List<HeartbeatEntity>>() {
                }));
        return body;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(CLIENTTYPE, clientType);
        map.put(CONSUMERGROUP, consumerGroup);
        map.put(HEARTBEATENTITIES, JsonUtils.toJSONString(heartbeatEntities));
        return map;
    }

    @ToString
    public static class HeartbeatEntity {

        public String topic;
        public String serviceId;
        public String url;
        public String instanceId;
    }
}
