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

package org.apache.eventmesh.admin.rocketmq.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A data transfer object (DTO) that represents a response containing a topic and its creation time.
 * <p>
 * It includes the values returned upon the creation of a new topic, an update to an existing topic,
 * or the retrieval of a topic's details.
 * <p>
 * It is used to encapsulate the topic information being sent to the client from the server.
 */

public class TopicResponse {

    private String topic;
    private String createdTime;

    @JsonCreator
    public TopicResponse(@JsonProperty("topic") String topic,
                         @JsonProperty("created_time") String createdTime) {
        super();
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
        StringBuilder sb = new StringBuilder();
        sb.append("TopicResponse {topic=").append(this.topic).append(",").append("created_time=").append(this.createdTime).append("}");
        return sb.toString();
    }

}
