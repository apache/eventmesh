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

import lombok.Data;

@Data
public class GetMetricsResponse {

    // HTTP Metrics
    private float maxHTTPTPS;
    private float avgHTTPTPS;
    private long maxHTTPCost;
    private float avgHTTPCost;
    private float avgHTTPBodyDecodeCost;
    private long httpDiscard;
    private float maxBatchSendMsgTPS;
    private float avgBatchSendMsgTPS;
    private long sendBatchMsgNumSum;
    private long sendBatchMsgFailNumSum;
    private float sendBatchMsgFailRate;
    private long sendBatchMsgDiscardNumSum;
    private float maxSendMsgTPS;
    private float avgSendMsgTPS;
    private long sendMsgNumSum;
    private long sendMsgFailNumSum;
    private float sendMsgFailRate;
    private long replyMsgNumSum;
    private long replyMsgFailNumSum;
    private float maxPushMsgTPS;
    private float avgPushMsgTPS;
    private long pushHTTPMsgNumSum;
    private long pushHTTPMsgFailNumSum;
    private float pushHTTPMsgFailRate;
    private float maxHTTPPushLatency;
    private float avgHTTPPushLatency;
    private int batchMsgQueueSize;
    private int sendMsgQueueSize;
    private int pushMsgQueueSize;
    private long retryHTTPQueueSize;
    private float avgBatchSendMsgCost;
    private float avgSendMsgCost;
    private float avgReplyMsgCost;

    // TCP Metrics
    private long retryTCPQueueSize;
    private double client2eventMeshTCPTPS;
    private double eventMesh2mqTCPTPS;
    private double mq2eventMeshTCPTPS;
    private double eventMesh2clientTCPTPS;
    private double allTCPTPS;
    private int allTCPConnections;
    private int subTopicTCPNum;

    @JsonCreator
    public GetMetricsResponse(
        // HTTP Metrics
        @JsonProperty("maxHTTPTPS") float maxHTTPTPS,
        @JsonProperty("avgHTTPTPS") float avgHTTPTPS,
        @JsonProperty("maxHTTPCost") long maxHTTPCost,
        @JsonProperty("avgHTTPCost") float avgHTTPCost,
        @JsonProperty("avgHTTPBodyDecodeCost") float avgHTTPBodyDecodeCost,
        @JsonProperty("httpDiscard") long httpDiscard,
        @JsonProperty("maxBatchSendMsgTPS") float maxBatchSendMsgTPS,
        @JsonProperty("avgBatchSendMsgTPS") float avgBatchSendMsgTPS,
        @JsonProperty("sendBatchMsgNumSum") long sendBatchMsgNumSum,
        @JsonProperty("sendBatchMsgFailNumSum") long sendBatchMsgFailNumSum,
        @JsonProperty("sendBatchMsgFailRate") float sendBatchMsgFailRate,
        @JsonProperty("sendBatchMsgDiscardNumSum") long sendBatchMsgDiscardNumSum,
        @JsonProperty("maxSendMsgTPS") float maxSendMsgTPS,
        @JsonProperty("avgSendMsgTPS") float avgSendMsgTPS,
        @JsonProperty("sendMsgNumSum") long sendMsgNumSum,
        @JsonProperty("sendMsgFailNumSum") long sendMsgFailNumSum,
        @JsonProperty("sendMsgFailRate") float sendMsgFailRate,
        @JsonProperty("replyMsgNumSum") long replyMsgNumSum,
        @JsonProperty("replyMsgFailNumSum") long replyMsgFailNumSum,
        @JsonProperty("maxPushMsgTPS") float maxPushMsgTPS,
        @JsonProperty("avgPushMsgTPS") float avgPushMsgTPS,
        @JsonProperty("pushHTTPMsgNumSum") long pushHTTPMsgNumSum,
        @JsonProperty("pushHTTPMsgFailNumSum") long pushHTTPMsgFailNumSum,
        @JsonProperty("pushHTTPMsgFailRate") float pushHTTPMsgFailRate,
        @JsonProperty("maxHTTPPushLatency") float maxHTTPPushLatency,
        @JsonProperty("avgHTTPPushLatency") float avgHTTPPushLatency,
        @JsonProperty("batchMsgQueueSize") int batchMsgQueueSize,
        @JsonProperty("sendMsgQueueSize") int sendMsgQueueSize,
        @JsonProperty("pushMsgQueueSize") int pushMsgQueueSize,
        @JsonProperty("retryHTTPQueueSize") long retryHTTPQueueSize,
        @JsonProperty("avgBatchSendMsgCost") float avgBatchSendMsgCost,
        @JsonProperty("avgSendMsgCost") float avgSendMsgCost,
        @JsonProperty("avgReplyMsgCost") float avgReplyMsgCost,
        // TCP Metrics
        @JsonProperty("retryTCPQueueSize") long retryTCPQueueSize,
        @JsonProperty("client2eventMeshTCPTPS") double client2eventMeshTCPTPS,
        @JsonProperty("eventMesh2mqTCPTPS") double eventMesh2mqTCPTPS,
        @JsonProperty("mq2eventMeshTCPTPS") double mq2eventMeshTCPTPS,
        @JsonProperty("eventMesh2clientTCPTPS") double eventMesh2clientTCPTPS,
        @JsonProperty("allTCPTPS") double allTCPTPS,
        @JsonProperty("allTCPConnections") int allTCPConnections,
        @JsonProperty("subTopicTCPNum") int subTopicTCPNum) {

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
