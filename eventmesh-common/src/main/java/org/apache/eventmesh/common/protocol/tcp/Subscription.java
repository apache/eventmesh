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

package org.apache.eventmesh.common.protocol.tcp;

import java.util.LinkedList;
import java.util.List;

import org.apache.eventmesh.common.protocol.SubscriptionItem;

public class Subscription {

    private List<SubscriptionItem> topicList = new LinkedList<>();

    public Subscription() {
    }

    public Subscription(List<SubscriptionItem> topicList) {
        this.topicList = topicList;
    }

    public List<SubscriptionItem> getTopicList() {
        return topicList;
    }

    public void setTopicList(List<SubscriptionItem> topicList) {
        this.topicList = topicList;
    }

    @Override
    public String toString() {
        return "Subscription{"
                + "topicList=" + topicList
                + '}';
    }
}
