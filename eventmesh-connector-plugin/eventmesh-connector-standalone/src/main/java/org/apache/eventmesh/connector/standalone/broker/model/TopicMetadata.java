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
import java.util.Objects;

/**
 * Topic metadata, each topic has it's own metadata
 */
public class TopicMetadata implements Serializable {

    private String topicName;

    public TopicMetadata(String topicName) {
        this.topicName = topicName;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TopicMetadata)) {
            return false;
        }
        TopicMetadata that = (TopicMetadata) o;
        return Objects.equals(topicName, that.topicName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topicName);
    }

    @Override
    public String toString() {
        return "TopicMetadata{" +
                "topic='" + topicName + '\'' +
                '}';
    }
}
