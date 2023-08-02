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

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ConsumerGroupConf implements Serializable {

    private String sysId;
    //eg . 5013-1A0
    private String groupName;

    private final ConcurrentHashMap<String/*topic*/, ConsumerGroupTopicConf> consumerGroupTopicConfMapping
        = new ConcurrentHashMap<>();

    public ConsumerGroupConf(String groupName) {
        this.groupName = groupName;
    }

    public ConsumerGroupConf(String sysId, String groupName) {
        this.sysId = sysId;
        this.groupName = groupName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Map<String, ConsumerGroupTopicConf> getConsumerGroupTopicConfMapping() {
        return consumerGroupTopicConfMapping;
    }

    public String getSysId() {
        return sysId;
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
        return Objects.equals(sysId, that.sysId)
                && Objects.equals(groupName, that.groupName)
                && Objects.equals(consumerGroupTopicConfMapping, that.consumerGroupTopicConfMapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sysId, groupName, consumerGroupTopicConfMapping);
    }

    @Override
    public String toString() {
        return "ConsumerGroupConf{"
                + "sysId='" + sysId + '\''
                + ", groupName='" + groupName + '\''
                + ", consumerGroupTopicConfMapping=" + consumerGroupTopicConfMapping
                + '}';
    }

}
