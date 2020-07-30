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

package cn.webank.emesher.core.consumergroup;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ConsumerGroupTopicConf {

    public static Logger logger = LoggerFactory.getLogger(ConsumerGroupTopicConf.class);

    private String consumerGroup;

    private String topic;

    /**
     * PUSH的URL
     */
    private Map<String /** IDC*/, List<String> /** IDC内URL列表*/> idcUrls = Maps.newConcurrentMap();

    /**
     * IDC 无关的全量URL
     */
    private Set<String> urls = Sets.newConcurrentHashSet();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsumerGroupTopicConf that = (ConsumerGroupTopicConf) o;
        return consumerGroup.equals(that.consumerGroup) &&
                Objects.equals(topic, that.topic) &&
                Objects.equals(idcUrls, that.idcUrls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerGroup, topic, idcUrls);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("consumeTopicConfig={consumerGroup=").append(consumerGroup)
                .append(",topic=").append(topic)
                .append(",idcUrls=").append(idcUrls).append("}");
        return sb.toString();
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Map<String, List<String>> getIdcUrls() {
        return idcUrls;
    }

    public void setIdcUrls(Map<String, List<String>> idcUrls) {
        this.idcUrls = idcUrls;
    }

    public Set<String> getUrls() {
        return urls;
    }

    public void setUrls(Set<String> urls) {
        this.urls = urls;
    }
}
