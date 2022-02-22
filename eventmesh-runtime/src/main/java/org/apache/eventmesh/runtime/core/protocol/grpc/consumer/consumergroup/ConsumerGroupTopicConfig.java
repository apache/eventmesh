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

package org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup;

import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConsumerGroupTopicConfig {
    private final Logger logger = LoggerFactory.getLogger(ConsumerGroupTopicConfig.class);

    protected final String consumerGroup;

    protected final String topic;

    protected final SubscriptionMode subscriptionMode;

    protected final GrpcType grpcType;

    protected ConsumerGroupTopicConfig(String consumerGroup, String topic, SubscriptionMode subscriptionMode, GrpcType grpcType) {
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.subscriptionMode = subscriptionMode;
        this.grpcType = grpcType;
    }

    public static ConsumerGroupTopicConfig buildTopicConfig(String consumerGroup, String topic, SubscriptionMode subscriptionMode,
                                                            GrpcType grpcType) {
        if (GrpcType.STREAM.equals(grpcType)) {
            return new StreamTopicConfig(consumerGroup, topic, subscriptionMode);
        } else {
            return new WebhookTopicConfig(consumerGroup, topic, subscriptionMode);
        }
    }

    public abstract void registerClient(ConsumerGroupClient client);

    public abstract void deregisterClient(ConsumerGroupClient client);

    public abstract int getSize();

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public String getTopic() {
        return topic;
    }

    public SubscriptionMode getSubscriptionMode() {
        return subscriptionMode;
    }

    public GrpcType getGrpcType() {
        return grpcType;
    }
}