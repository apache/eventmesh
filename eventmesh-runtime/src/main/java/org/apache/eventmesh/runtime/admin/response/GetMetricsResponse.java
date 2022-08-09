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

package org.apache.eventmesh.runtime.admin.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class GetMetricsResponse {
    //HTTP Metrics
    public float maxHTTPTPS;
    public float avgHTTPTPS;
    public long maxHTTPCost;
    public float avgHTTPCost;
    public float avgHTTPBodyDecodeCost;
    public long httpDiscard;
    public float maxBatchSendMsgTPS;
    public float avgBatchSendMsgTPS;
    public long sendBatchMsgNumSum;
    public long sendBatchMsgFailNumSum;
    public float sendBatchMsgFailRate;
    public long sendBatchMsgDiscardNumSum;
    public float maxSendMsgTPS;
    public float avgSendMsgTPS;
    public long sendMsgNumSum;
    public long sendMsgFailNumSum;
    public float sendMsgFailRate;
    public long replyMsgNumSum;
    public long replyMsgFailNumSum;
    public float maxPushMsgTPS;
    public float avgPushMsgTPS;
    public long pushHTTPMsgNumSum;
    public long pushHTTPMsgFailNumSum;
    public float pushHTTPMsgFailRate;
    public float maxHTTPPushLatency;
    public float avgHTTPPushLatency;
    public int batchMsgQueueSize;
    public int sendMsgQueueSize;
    public int pushMsgQueueSize;
    public int retryHTTPQueueSize;
    public float avgBatchSendMsgCost;
    public float avgSendMsgCost;
    public float avgReplyMsgCost;

    //TCP Metrics
    public int retryTCPQueueSize;
    public int client2eventMeshTCPTPS;
    public int eventMesh2mqTCPTPS;
    public int mq2eventMeshTCPTPS;
    public int eventMesh2clientTCPTPS;
    public int allTCPTPS;
    public int allTCPConnections;
    public int subTopicTCPNum;


    @JsonCreator
    public GetMetricsResponse(
            //HTTP Metrics
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
            @JsonProperty("retryHTTPQueueSize") int retryHTTPQueueSize,
            @JsonProperty("avgBatchSendMsgCost") float avgBatchSendMsgCost,
            @JsonProperty("avgSendMsgCost") float avgSendMsgCost,
            @JsonProperty("avgReplyMsgCost") float avgReplyMsgCost,
            //TCP Metrics
            @JsonProperty("retryTCPQueueSize") int retryTCPQueueSize,
            @JsonProperty("client2eventMeshTCPTPS") int client2eventMeshTCPTPS,
            @JsonProperty("eventMesh2mqTCPTPS") int eventMesh2mqTCPTPS,
            @JsonProperty("mq2eventMeshTCPTPS") int mq2eventMeshTCPTPS,
            @JsonProperty("eventMesh2clientTCPTPS") int eventMesh2clientTCPTPS,
            @JsonProperty("allTCPTPS") int allTCPTPS,
            @JsonProperty("allTCPConnections") int allTCPConnections,
            @JsonProperty("subTopicTCPNum") int subTopicTCPNum
    ) {
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