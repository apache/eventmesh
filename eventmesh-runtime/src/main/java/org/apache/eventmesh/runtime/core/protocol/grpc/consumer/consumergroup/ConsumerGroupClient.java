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

import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;

import java.util.Date;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ConsumerGroupClient {

    public final String env;

    public final String idc;

    public final String consumerGroup;

    public final String topic;

    private final GrpcType grpcType;

    public String url;

    private EventEmitter<CloudEvent> eventEmitter;

    private final SubscriptionMode subscriptionMode;

    public final String sys;

    public final String ip;

    public final String pid;

    public final String hostname;

    public final String apiVersion;

    private Date lastUpTime;

    public void setUrl(String url) {
        this.url = url;
    }

    public void setEventEmitter(EventEmitter<CloudEvent> emitter) {
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
