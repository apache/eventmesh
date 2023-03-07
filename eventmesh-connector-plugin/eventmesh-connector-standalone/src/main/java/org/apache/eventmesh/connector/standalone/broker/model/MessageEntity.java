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

package org.apache.eventmesh.connector.standalone.broker.model;

import java.io.Serializable;

import io.cloudevents.CloudEvent;

public class MessageEntity implements Serializable {

    private TopicMetadata topicMetadata;

    private CloudEvent message;

    private long offset;

    private long createTimeMills;

    public MessageEntity(TopicMetadata topicMetadata, CloudEvent message, long offset, long currentTimeMills) {
        this.topicMetadata = topicMetadata;
        this.message = message;
        this.offset = offset;
        this.createTimeMills = currentTimeMills;
    }

    public TopicMetadata getTopicMetadata() {
        return topicMetadata;
    }

    public void setTopicMetadata(TopicMetadata topicMetadata) {
        this.topicMetadata = topicMetadata;
    }

    public CloudEvent getMessage() {
        return message;
    }

    public void setMessage(CloudEvent message) {
        this.message = message;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getCreateTimeMills() {
        return createTimeMills;
    }

    public void setCreateTimeMills(long createTimeMills) {
        this.createTimeMills = createTimeMills;
    }
}
