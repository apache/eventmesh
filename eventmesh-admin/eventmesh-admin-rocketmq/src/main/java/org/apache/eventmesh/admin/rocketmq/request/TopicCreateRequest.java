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

package org.apache.eventmesh.admin.rocketmq.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A data transfer object (DTO) that represents a request to create a new topic.
 * <p>
 * This class provides a convenient way to encapsulate the topic information when creating a new topic.
 * <p>
 * Empty or null values will not be included in the serialized JSON.
 * <p>
 * Any unknown properties will be ignored when deserializing JSON into this class.
 * <p>
 * Example usage:
 * <pre>
 * String params = NetUtils.parsePostBody(httpExchange);
 * TopicCreateRequest topicCreateRequest = JsonUtils.parseObject(params, TopicCreateRequest.class);
 * String topic = topicCreateRequest.getTopic();
 * topicCreateRequest.setTopic("UpdatedTopic");
 * </pre>
 */

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopicCreateRequest {

    private String topic;

    /**
     * Constructs a new instance of {@link TopicCreateRequest}.
     *
     * @param topic the topic for the request
     */
    @JsonCreator
    public TopicCreateRequest(@JsonProperty("topic") String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return this.topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

}
