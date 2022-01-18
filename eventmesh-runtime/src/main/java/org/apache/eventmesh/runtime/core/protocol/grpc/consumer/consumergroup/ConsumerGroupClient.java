/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup;

import lombok.Getter;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import java.util.Date;
import lombok.Builder;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;

@Builder
@Getter
public class ConsumerGroupClient {

    private final String env;

    private final String idc;

    private final String consumerGroup;

    private final String topic;

    private final GrpcType grpcType;

    private String url;

    private EventEmitter<SimpleMessage> eventEmitter;

    private final SubscriptionMode subscriptionMode;

    private final String sys;

    private final String ip;

    private final String pid;

    private final String hostname;

    private final String apiVersion;

    private Date lastUpTime;

    public void setUrl(String url) {
        this.url = url;
    }
    public void setEventEmitter(EventEmitter<SimpleMessage> emitter) {
        this.eventEmitter = emitter;
    }

    public void setLastUpTime(Date lastUpTime) {
        this.lastUpTime = lastUpTime;
    }

    @Override
    public String toString() {
        return "endPoint={env=" + env
            + ",idc=" + idc
            + ",consumerGroup=" + consumerGroup
            + ",topic=" + topic
            + ",grpcType=" + grpcType
            + ",url=" + url
            + ",sys=" + sys
            + ",ip=" + ip
            + ",pid=" + pid
            + ",hostname=" + hostname
            + ",apiVersion=" + apiVersion
            + ",lastUpTime=" + lastUpTime + "}";
    }
}

