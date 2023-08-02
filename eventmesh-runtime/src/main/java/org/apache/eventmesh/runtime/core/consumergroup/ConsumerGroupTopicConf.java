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

import org.apache.eventmesh.common.protocol.SubscriptionItem;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ConsumerGroupTopicConf implements Serializable {

    private static final long serialVersionUID = 4548889791666411923L;

    private String consumerGroup;

    private String topic;

    /**
     * @see org.apache.eventmesh.common.protocol.SubscriptionItem
     */
    private SubscriptionItem subscriptionItem;

    /**
     * PUSH URL Map key:IDC value:URL list in IDC
     */
    private Map<String, CopyOnWriteArrayList<String>> idcUrls = Maps.newConcurrentMap();

    /**
     * ALL IDC URLs
     */
    private Set<String> urls = Sets.newConcurrentHashSet();

    /**
     * url auth type
     */
    private final Map<String, String> httpAuthTypeMap = Maps.newConcurrentMap();

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConsumerGroupTopicConf that = (ConsumerGroupTopicConf) o;
        return consumerGroup.equals(that.consumerGroup)
            &&
            Objects.equals(topic, that.topic)
            &&
            Objects.equals(subscriptionItem, that.subscriptionItem)
            &&
            Objects.equals(idcUrls, that.idcUrls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerGroup, topic, subscriptionItem, idcUrls);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(120);
        sb.append("consumeTopicConfig={consumerGroup=").append(consumerGroup)
            .append(",topic=").append(topic)
            .append(",subscriptionMode=").append(subscriptionItem)
            .append(",idcUrls=").append(idcUrls).append('}');
        return sb.toString();
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(final String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(final String topic) {
        this.topic = topic;
    }

    public SubscriptionItem getSubscriptionItem() {
        return subscriptionItem;
    }

    public void setSubscriptionItem(final SubscriptionItem subscriptionItem) {
        this.subscriptionItem = subscriptionItem;
    }

    public Map<String, CopyOnWriteArrayList<String>> getIdcUrls() {
        return idcUrls;
    }

    public void setIdcUrls(final Map<String, CopyOnWriteArrayList<String>> idcUrls) {
        this.idcUrls = idcUrls;
    }

    public Set<String> getUrls() {
        return urls;
    }

    public void setUrls(final Set<String> urls) {
        this.urls = urls;
    }

    public Map<String, String> getHttpAuthTypeMap() {
        return httpAuthTypeMap;
    }
}
