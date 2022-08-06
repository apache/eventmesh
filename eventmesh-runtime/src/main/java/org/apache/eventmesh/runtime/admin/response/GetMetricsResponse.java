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
    public long SendBatchMsgDiscardNumSum;
    public float maxSendMsgTPS;
    public float avgsendMsgTPS;
    public long sendMsgNumSum;
    public long sendMsgFailNumSum;
    public float sendMsgFailRate;
    public long replyMsgNumSum;
    public long replyMsgFailNumSum;
    public float maxPushMsgTPS;
    public float avgPushMsgTPS;
    public long HTTPpushMsgNumSum;
    public long HTTPpushMsgFailNumSum;
    public float HTTPpushMsgFailRate;
    public float maxHTTPPushLatency;
    public float avgHTTPPushLatency;
    public int batchMsgQueueSize;
    public int sendMsgQueueSize;
    public int pushMsgQueueSize;
    public int HTTPRetryQueueSize;
    public float avgBatchSendMsgCost;
    public float avgSendMsgCost;
    public float avgReplyMsgCost;

    //TCP Metrics
    public int TCPretryQueueSize;
    public int TCPclient2eventMeshTPS;
    public int TCPeventMesh2mqTPS;
    public int TCPMq2eventMeshTPS;
    public int TCPeventMesh2clientTPS;
    public int allTCPTPS;
    public int allTCPConnections;
    public int TCPSubTopicNum;


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
            @JsonProperty("SendBatchMsgDiscardNumSum") long SendBatchMsgDiscardNumSum,
            @JsonProperty("maxSendMsgTPS") float maxSendMsgTPS,
            @JsonProperty("avgsendMsgTPS") float avgsendMsgTPS,
            @JsonProperty("sendMsgNumSum") long sendMsgNumSum,
            @JsonProperty("sendMsgFailNumSum") long sendMsgFailNumSum,
            @JsonProperty("sendMsgFailRate") float sendMsgFailRate,
            @JsonProperty("replyMsgNumSum") long replyMsgNumSum,
            @JsonProperty("replyMsgFailNumSum") long replyMsgFailNumSum,
            @JsonProperty("maxPushMsgTPS") float maxPushMsgTPS,
            @JsonProperty("avgPushMsgTPS") float avgPushMsgTPS,
            @JsonProperty("HTTPpushMsgNumSum") long HTTPpushMsgNumSum,
            @JsonProperty("HTTPpushMsgFailNumSum") long HTTPpushMsgFailNumSum,
            @JsonProperty("HTTPpushMsgFailRate") float HTTPpushMsgFailRate,
            @JsonProperty("maxHTTPPushLatency") float maxHTTPPushLatency,
            @JsonProperty("avgHTTPPushLatency") float avgHTTPPushLatency,
            @JsonProperty("batchMsgQueueSize") int batchMsgQueueSize,
            @JsonProperty("sendMsgQueueSize") int sendMsgQueueSize,
            @JsonProperty("pushMsgQueueSize") int pushMsgQueueSize,
            @JsonProperty("HTTPRetryQueueSize") int HTTPRetryQueueSize,
            @JsonProperty("avgBatchSendMsgCost") float avgBatchSendMsgCost,
            @JsonProperty("avgSendMsgCost") float avgSendMsgCost,
            @JsonProperty("avgReplyMsgCost") float avgReplyMsgCost,
            //TCP Metrics
            @JsonProperty("TCPretryQueueSize") int TCPretryQueueSize,
            @JsonProperty("TCPclient2eventMeshTPS") int TCPclient2eventMeshTPS,
            @JsonProperty("TCPeventMesh2mqTPS") int TCPeventMesh2mqTPS,
            @JsonProperty("TCPMq2eventMeshTPS") int TCPMq2eventMeshTPS,
            @JsonProperty("TCPeventMesh2clientTPS") int TCPeventMesh2clientTPS,
            @JsonProperty("allTCPTPS") int allTCPTPS,
            @JsonProperty("allTCPConnections") int allTCPConnections,
            @JsonProperty("TCPSubTopicNum") int TCPSubTopicNum
    ){
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
        this.SendBatchMsgDiscardNumSum = SendBatchMsgDiscardNumSum;
        this.maxSendMsgTPS = maxSendMsgTPS;
        this.avgsendMsgTPS = avgsendMsgTPS;
        this.sendMsgNumSum = sendMsgNumSum;
        this.sendMsgFailNumSum = sendMsgFailNumSum;
        this.sendMsgFailRate = sendMsgFailRate;
        this.replyMsgNumSum = replyMsgNumSum;
        this.replyMsgFailNumSum = replyMsgFailNumSum;
        this.maxPushMsgTPS = maxPushMsgTPS;
        this.avgPushMsgTPS = avgPushMsgTPS;
        this.HTTPpushMsgNumSum = HTTPpushMsgNumSum;
        this.HTTPpushMsgFailNumSum = HTTPpushMsgFailNumSum;
        this.HTTPpushMsgFailRate = HTTPpushMsgFailRate;
        this.maxHTTPPushLatency = maxHTTPPushLatency;
        this.avgHTTPPushLatency = avgHTTPPushLatency;
        this.batchMsgQueueSize = batchMsgQueueSize;
        this.sendMsgQueueSize = sendMsgQueueSize;
        this.pushMsgQueueSize = pushMsgQueueSize;
        this.HTTPRetryQueueSize = HTTPRetryQueueSize;
        this.avgBatchSendMsgCost = avgBatchSendMsgCost;
        this.avgSendMsgCost = avgSendMsgCost;
        this.avgReplyMsgCost = avgReplyMsgCost;
        this.TCPretryQueueSize = TCPretryQueueSize;
        this.TCPclient2eventMeshTPS = TCPclient2eventMeshTPS;
        this.TCPeventMesh2mqTPS = TCPeventMesh2mqTPS;
        this.TCPMq2eventMeshTPS = TCPMq2eventMeshTPS;
        this.TCPeventMesh2clientTPS = TCPeventMesh2clientTPS;
        this.allTCPTPS = allTCPTPS;
        this.allTCPConnections = allTCPConnections;
        this.TCPSubTopicNum = TCPSubTopicNum;
    }


}
