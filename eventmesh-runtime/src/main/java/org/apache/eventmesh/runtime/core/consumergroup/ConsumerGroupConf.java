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

package org.apache.eventmesh.runtime.core.consumergroup;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Maps;

public class ConsumerGroupConf implements Cloneable {
    //eg . 5013-1A0
    private String consumerGroup;

    private Map<String, ConsumerGroupTopicConf> consumerGroupTopicConf = Maps.newConcurrentMap();

    public ConsumerGroupConf(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public Map<String, ConsumerGroupTopicConf> getConsumerGroupTopicConf() {
        return consumerGroupTopicConf;
    }

    public void setConsumerGroupTopicConf(Map<String, ConsumerGroupTopicConf> consumerGroupTopicConf) {
        this.consumerGroupTopicConf = consumerGroupTopicConf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConsumerGroupConf that = (ConsumerGroupConf) o;

        return consumerGroup.equals(that.consumerGroup)
                &&
                Objects.equals(consumerGroupTopicConf, that.consumerGroupTopicConf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerGroup, consumerGroupTopicConf);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("consumerGroupConfig={")
                .append("groupName=").append(consumerGroup).append(",")
                .append(",consumerGroupTopicConf=").append(consumerGroupTopicConf).append("}");
        return sb.toString();
    }

    @Override
    public ConsumerGroupConf clone() {
        ConsumerGroupConf clone = new ConsumerGroupConf(consumerGroup);

        Map<String, ConsumerGroupTopicConf> cloneGroupTopicConf = new ConcurrentHashMap<>();
        consumerGroupTopicConf.forEach((topic, groupTopicConf) -> cloneGroupTopicConf.put(topic, groupTopicConf.clone()));

        clone.setConsumerGroupTopicConf(cloneGroupTopicConf);
        return clone;
    }
}
