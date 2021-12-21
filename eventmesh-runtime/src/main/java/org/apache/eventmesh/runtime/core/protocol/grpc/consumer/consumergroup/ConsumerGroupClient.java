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

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;

import java.util.Date;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ConsumerGroupClient {

    private String env;

    private String idc;

    private String consumerGroup;

    private String topic;

    private String protocolDesc;

    private String url;

    private StreamObserver<EventMeshMessage> eventEmitter;

    private SubscriptionMode subscriptionMode;

    private String sys;

    private String ip;

    private String pid;

    private String hostname;

    private String apiVersion;

    private Date lastUpTime;

    @Override
    public String toString() {
        return "endPoint={env=" + env +
            ",idc=" + idc +
            ",consumerGroup=" + consumerGroup +
            ",topic=" + topic +
            ",protocolDesc=" + protocolDesc +
            ",url=" + url +
            ",sys=" + sys +
            ",ip=" + ip +
            ",pid=" + pid +
            ",hostname=" + hostname +
            ",apiVersion=" + apiVersion +
            ",registerTime=" + "}";
    }
}

