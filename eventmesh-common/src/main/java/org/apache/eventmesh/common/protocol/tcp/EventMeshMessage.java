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

<<<<<<<< HEAD:eventmesh-admin/eventmesh-admin-rocketmq/src/main/java/org/apache/eventmesh/admin/rocketmq/response/TopicResponse.java
package org.apache.eventmesh.admin.rocketmq.response;
========
package org.apache.eventmesh.common.protocol.tcp;
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-common/src/main/java/org/apache/eventmesh/common/protocol/tcp/EventMeshMessage.java

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

<<<<<<<< HEAD:eventmesh-admin/eventmesh-admin-rocketmq/src/main/java/org/apache/eventmesh/admin/rocketmq/response/TopicResponse.java
public class TopicResponse {
========
public class EventMeshMessage {
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-common/src/main/java/org/apache/eventmesh/common/protocol/tcp/EventMeshMessage.java

    private String topic;
    private String createdTime;

<<<<<<<< HEAD:eventmesh-admin/eventmesh-admin-rocketmq/src/main/java/org/apache/eventmesh/admin/rocketmq/response/TopicResponse.java
    @JsonCreator
    public TopicResponse(@JsonProperty("topic") String topic,
                         @JsonProperty("created_time") String createdTime) {
        super();
========
    public EventMeshMessage() {
    }

    public EventMeshMessage(String topic, Map<String, String> properties, String body) {
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-common/src/main/java/org/apache/eventmesh/common/protocol/tcp/EventMeshMessage.java
        this.topic = topic;
        this.createdTime = createdTime;
    }

    @JsonProperty("topic")
    public String getTopic() {
        return this.topic;
    }

    @JsonProperty("topic")
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @JsonProperty("created_time")
    public String getCreatedTime() {
        return createdTime;
    }

    @JsonProperty("created_time")
    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    @Override
    public String toString() {
<<<<<<<< HEAD:eventmesh-admin/eventmesh-admin-rocketmq/src/main/java/org/apache/eventmesh/admin/rocketmq/response/TopicResponse.java
        StringBuilder sb = new StringBuilder();
        sb.append("TopicResponse {topic=" + this.topic + ",");
        sb.append("created_time=" + this.createdTime + "}");
        return sb.toString();
========
        return "EventMeshMessage{" +
                "topic='" + topic + '\'' +
                ", properties=" + properties +
                ", body='" + body + '\'' +
                '}';
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-common/src/main/java/org/apache/eventmesh/common/protocol/tcp/EventMeshMessage.java
    }

}
