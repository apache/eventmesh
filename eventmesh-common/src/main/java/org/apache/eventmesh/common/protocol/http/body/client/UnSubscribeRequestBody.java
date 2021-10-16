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

public class UnSubscribeRequestBody extends Body {

    public static final String TOPIC = "topic";

    public static final String URL = "url";

    public static final String CONSUMERGROUP = "consumerGroup";

    private List<String> topics;

    private String url;

    private String consumerGroup;

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public static UnSubscribeRequestBody buildBody(Map<String, Object> bodyParam) {
        UnSubscribeRequestBody body = new UnSubscribeRequestBody();
        body.setUrl(MapUtils.getString(bodyParam, URL));
        body.setTopics(JsonUtils
            .deserialize(MapUtils.getString(bodyParam, TOPIC), new TypeReference<List<String>>() {
            }));
        body.setConsumerGroup(MapUtils.getString(bodyParam, CONSUMERGROUP));
        return body;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(URL, url);
        map.put(TOPIC, JsonUtils.serialize(topics));
        map.put(CONSUMERGROUP, consumerGroup);
        return map;
    }

    @Override
    public String toString() {
        return "unSubscribeRequestBody{"
            + "consumerGroup='" + consumerGroup + '\''
            + ", url='" + url + '\''
            + ", topics=" + topics
            + '}';
    }
}
