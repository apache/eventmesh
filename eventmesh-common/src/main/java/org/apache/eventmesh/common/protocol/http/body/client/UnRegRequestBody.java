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

public class UnRegRequestBody extends Body {

    public static final String CLIENTTYPE = "clientType";

    public static final String TOPICS = "topics";

    private String clientType;

    private List<UnRegTopicEntity> topics;

    public List<UnRegTopicEntity> getTopics() {
        return topics;
    }

    public void setTopics(List<UnRegTopicEntity> topics) {
        this.topics = topics;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public static UnRegRequestBody buildBody(Map<String, Object> bodyParam) {
        UnRegRequestBody body = new UnRegRequestBody();
        body.setClientType(MapUtils.getString(bodyParam, CLIENTTYPE));
        body.setTopics(JsonUtils.deserialize(MapUtils.getString(bodyParam, TOPICS),
            new TypeReference<List<UnRegTopicEntity>>() {
            }));
        return body;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(CLIENTTYPE, clientType);
        map.put(TOPICS, JsonUtils.serialize(topics));
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("regRequestBody={")
            .append("clientType=").append(clientType)
            .append("topics=").append(topics)
            .append("}");
        return sb.toString();
    }

    public static class UnRegTopicEntity {
        public String topic;
        public String serviceId;
        public String instanceId;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("unRegTopicEntity={")
                .append("topic=").append(topic).append(",")
                .append("serviceId=").append(serviceId).append(",")
                .append("instanceId=").append(instanceId).append("}");
            return sb.toString();
        }
    }
}
