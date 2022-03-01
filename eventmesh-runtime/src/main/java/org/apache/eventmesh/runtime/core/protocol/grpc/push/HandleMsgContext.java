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

package org.apache.eventmesh.runtime.core.protocol.grpc.push;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import io.cloudevents.CloudEvent;

public class HandleMsgContext {
    private final String msgRandomNo;
    private final SubscriptionMode subscriptionMode;
    private final EventMeshGrpcServer eventMeshGrpcServer;
    private final EventMeshConsumer eventMeshConsumer;
    private final ConsumerGroupTopicConfig consumeTopicConfig;

    private final GrpcType grpcType;
    private final String consumerGroup;

    private final CloudEvent event;
    private final AbstractContext context;

    public HandleMsgContext(String consumerGroup, CloudEvent event, SubscriptionMode subscriptionMode, GrpcType grpcType,
                            AbstractContext context, EventMeshGrpcServer eventMeshGrpcServer,
                            EventMeshConsumer eventMeshConsumer, ConsumerGroupTopicConfig consumeTopicConfig) {
        this.msgRandomNo = EventMeshUtil.buildPushMsgSeqNo();
        this.consumerGroup = consumerGroup;
        this.grpcType = grpcType;
        this.subscriptionMode = subscriptionMode;
        this.event = event;
        this.context = context;
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.eventMeshConsumer = eventMeshConsumer;
        this.consumeTopicConfig = consumeTopicConfig;
    }

    public String getMsgRandomNo() {
        return msgRandomNo;
    }

    public ConsumerGroupTopicConfig getConsumeTopicConfig() {
        return consumeTopicConfig;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public CloudEvent getEvent() {
        return event;
    }

    public AbstractContext getContext() {
        return context;
    }

    public EventMeshGrpcServer getEventMeshGrpcServer() {
        return eventMeshGrpcServer;
    }

    public EventMeshConsumer getEventMeshConsumer() {
        return eventMeshConsumer;
    }

    public SubscriptionMode getSubscriptionMode() {
        return subscriptionMode;
    }

    public GrpcType getGrpcType() {
        return grpcType;
    }


    @Override
    public String toString() {
        return "handleMsgContext={"
            + "consumerGroup=" + consumerGroup
            + ",subscriptionMode=" + subscriptionMode
            + ",consumeTopicConfig=" + consumeTopicConfig
            + "}";
    }
}
