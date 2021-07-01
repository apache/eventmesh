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

package org.apache.eventmesh.common.protocol;

public class SubscriptionItem {

    private String topic;

    private SubscriptionMode mode;

    private SubcriptionType type;

    public SubscriptionItem() {
    }

    public SubscriptionItem(String topic, SubscriptionMode mode, SubcriptionType type) {
        this.topic = topic;
        this.mode = mode;
        this.type = type;
    }

    public SubcriptionType getType() {
        return type;
    }

    public void setType(SubcriptionType type) {
        this.type = type;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public SubscriptionMode getMode() {
        return mode;
    }

    public void setMode(SubscriptionMode mode) {
        this.mode = mode;
    }

    @Override
    public String toString() {
        return "SubscriptionItem{" +
                "topic=" + topic +
                ", mode=" + mode +
                ", type=" + type +
                '}';
    }
}


