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

package org.apache.eventmesh.runtime.admin.response.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetMetricsResponse {

    // HTTP Metrics
    private double maxHTTPTPS;
    private double avgHTTPTPS;
    private long maxHTTPCost;
    private double avgHTTPCost;
    private double avgHTTPBodyDecodeCost;
    private long httpDiscard;
    private double maxBatchSendMsgTPS;
    private double avgBatchSendMsgTPS;
    private long sendBatchMsgNumSum;
    private long sendBatchMsgFailNumSum;
    private double sendBatchMsgFailRate;
    private long sendBatchMsgDiscardNumSum;
    private double maxSendMsgTPS;
    private double avgSendMsgTPS;
    private long sendMsgNumSum;
    private long sendMsgFailNumSum;
    private double sendMsgFailRate;
    private long replyMsgNumSum;
    private long replyMsgFailNumSum;
    private double maxPushMsgTPS;
    private double avgPushMsgTPS;
    private long pushHTTPMsgNumSum;
    private long pushHTTPMsgFailNumSum;
    private double pushHTTPMsgFailRate;
    private double maxHTTPPushLatency;
    private double avgHTTPPushLatency;
    private long batchMsgQueueSize;
    private long sendMsgQueueSize;
    private long pushMsgQueueSize;
    private long retryHTTPQueueSize;
    private double avgBatchSendMsgCost;
    private double avgSendMsgCost;
    private double avgReplyMsgCost;

    // TCP Metrics
    private int retryTCPQueueSize;
    private double client2eventMeshTCPTPS;
    private double eventMesh2mqTCPTPS;
    private double mq2eventMeshTCPTPS;
    private double eventMesh2clientTCPTPS;
    private double allTCPTPS;
    private long allTCPConnections;
    private long subTopicTCPNum;


    @JsonCreator
    public GetMetricsResponse(
        // HTTP Metrics
        @JsonProperty("maxHTTPTPS") double maxHTTPTPS,
        @JsonProperty("avgHTTPTPS") double avgHTTPTPS,
        @JsonProperty("maxHTTPCost") long maxHTTPCost,
        @JsonProperty("avgHTTPCost") double avgHTTPCost,
        @JsonProperty("avgHTTPBodyDecodeCost") double avgHTTPBodyDecodeCost,
        @JsonProperty("httpDiscard") long httpDiscard,
        @JsonProperty("maxBatchSendMsgTPS") double maxBatchSendMsgTPS,
        @JsonProperty("avgBatchSendMsgTPS") double avgBatchSendMsgTPS,
        @JsonProperty("sendBatchMsgNumSum") long sendBatchMsgNumSum,
        @JsonProperty("sendBatchMsgFailNumSum") long sendBatchMsgFailNumSum,
        @JsonProperty("sendBatchMsgFailRate") double sendBatchMsgFailRate,
        @JsonProperty("sendBatchMsgDiscardNumSum") long sendBatchMsgDiscardNumSum,
        @JsonProperty("maxSendMsgTPS") double maxSendMsgTPS,
        @JsonProperty("avgSendMsgTPS") double avgSendMsgTPS,
        @JsonProperty("sendMsgNumSum") long sendMsgNumSum,
        @JsonProperty("sendMsgFailNumSum") long sendMsgFailNumSum,
        @JsonProperty("sendMsgFailRate") double sendMsgFailRate,
        @JsonProperty("replyMsgNumSum") long replyMsgNumSum,
        @JsonProperty("replyMsgFailNumSum") long replyMsgFailNumSum,
        @JsonProperty("maxPushMsgTPS") double maxPushMsgTPS,
        @JsonProperty("avgPushMsgTPS") double avgPushMsgTPS,
        @JsonProperty("pushHTTPMsgNumSum") long pushHTTPMsgNumSum,
        @JsonProperty("pushHTTPMsgFailNumSum") long pushHTTPMsgFailNumSum,
        @JsonProperty("pushHTTPMsgFailRate") double pushHTTPMsgFailRate,
        @JsonProperty("maxHTTPPushLatency") double maxHTTPPushLatency,
        @JsonProperty("avgHTTPPushLatency") double avgHTTPPushLatency,
        @JsonProperty("batchMsgQueueSize") long batchMsgQueueSize,
        @JsonProperty("sendMsgQueueSize") long sendMsgQueueSize,
        @JsonProperty("pushMsgQueueSize") long pushMsgQueueSize,
        @JsonProperty("retryHTTPQueueSize") long retryHTTPQueueSize,
        @JsonProperty("avgBatchSendMsgCost") double avgBatchSendMsgCost,
        @JsonProperty("avgSendMsgCost") double avgSendMsgCost,
        @JsonProperty("avgReplyMsgCost") double avgReplyMsgCost,
        // TCP Metrics
        @JsonProperty("retryTCPQueueSize") int retryTCPQueueSize,
        @JsonProperty("client2eventMeshTCPTPS") double client2eventMeshTCPTPS,
        @JsonProperty("eventMesh2mqTCPTPS") double eventMesh2mqTCPTPS,
        @JsonProperty("mq2eventMeshTCPTPS") double mq2eventMeshTCPTPS,
        @JsonProperty("eventMesh2clientTCPTPS") double eventMesh2clientTCPTPS,
        @JsonProperty("allTCPTPS") double allTCPTPS,
        @JsonProperty("allTCPConnections") long allTCPConnections,
        @JsonProperty("subTopicTCPNum") long subTopicTCPNum) {

        super();
        this.maxHTTPTPS = maxHTTPTPS;
        this.avgHTTPTPS = avgHTTPTPS;
        this.maxHTTPCost = maxHTTPCost;
        this.avgHTTPCost = avgHTTPCost;
        this.avgHTTPBodyDecodeCost = avgHTTPBodyDecodeCost;
        this.httpDiscard = httpDiscard;
        this.maxBatchSendMsgTPS = maxBatchSendMsgTPS;
        this.avgBatchSendMsgTPS = avgBatchSendMsgTPS;
        this.sendBatchMsgNumSum = sendBatchMsgNumSum;
        this.sendBatchMsgFailNumSum = sendBatchMsgFailNumSum;
        this.sendBatchMsgFailRate = sendBatchMsgFailRate;
        this.sendBatchMsgDiscardNumSum = sendBatchMsgDiscardNumSum;
        this.maxSendMsgTPS = maxSendMsgTPS;
        this.avgSendMsgTPS = avgSendMsgTPS;
        this.sendMsgNumSum = sendMsgNumSum;
        this.sendMsgFailNumSum = sendMsgFailNumSum;
        this.sendMsgFailRate = sendMsgFailRate;
        this.replyMsgNumSum = replyMsgNumSum;
        this.replyMsgFailNumSum = replyMsgFailNumSum;
        this.maxPushMsgTPS = maxPushMsgTPS;
        this.avgPushMsgTPS = avgPushMsgTPS;
        this.pushHTTPMsgNumSum = pushHTTPMsgNumSum;
        this.pushHTTPMsgFailNumSum = pushHTTPMsgFailNumSum;
        this.pushHTTPMsgFailRate = pushHTTPMsgFailRate;
        this.maxHTTPPushLatency = maxHTTPPushLatency;
        this.avgHTTPPushLatency = avgHTTPPushLatency;
        this.batchMsgQueueSize = batchMsgQueueSize;
        this.sendMsgQueueSize = sendMsgQueueSize;
        this.pushMsgQueueSize = pushMsgQueueSize;
        this.retryHTTPQueueSize = retryHTTPQueueSize;
        this.avgBatchSendMsgCost = avgBatchSendMsgCost;
        this.avgSendMsgCost = avgSendMsgCost;
        this.avgReplyMsgCost = avgReplyMsgCost;
        this.retryTCPQueueSize = retryTCPQueueSize;
        this.client2eventMeshTCPTPS = client2eventMeshTCPTPS;
        this.eventMesh2mqTCPTPS = eventMesh2mqTCPTPS;
        this.mq2eventMeshTCPTPS = mq2eventMeshTCPTPS;
        this.eventMesh2clientTCPTPS = eventMesh2clientTCPTPS;
        this.allTCPTPS = allTCPTPS;
        this.allTCPConnections = allTCPConnections;
        this.subTopicTCPNum = subTopicTCPNum;
    }
}
