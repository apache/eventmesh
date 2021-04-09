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

package com.webank.eventmesh.common.protocol.tcp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventMeshMessage {

    private String topic;
    Map<String, String> properties = new ConcurrentHashMap<>();
    private String body;

    public EventMeshMessage() {
    }

    public EventMeshMessage(String topic, Map<String, String> properties, String body) {
        this.topic = topic;
        this.properties = properties;
        this.body = body;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "AccessMessage{" +
                "topic='" + topic + '\'' +
                ", properties=" + properties +
                ", body='" + body + '\'' +
                '}';
    }
}
