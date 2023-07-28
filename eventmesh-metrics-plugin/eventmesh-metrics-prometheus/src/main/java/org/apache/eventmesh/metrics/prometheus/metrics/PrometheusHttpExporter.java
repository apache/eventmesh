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

package org.apache.eventmesh.metrics.prometheus.metrics;

import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterConstants.HTTP;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.join;

import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;

public class PrometheusHttpExporter extends PrometheusExporter<HttpSummaryMetrics> {

    public PrometheusHttpExporter() {
        //maxHTTPTPS
        paramPairs.put(join("eventmesh.http.request.tps.max", "max TPS of HTTP."),
            HttpSummaryMetrics::getMaxHTTPTPS);
        //avgHTTPTPS
        paramPairs.put(join("eventmesh.http.request.tps.avg", "avg TPS of HTTP."),
            HttpSummaryMetrics::getAvgHTTPTPS);
        //maxHTTPCost
        paramPairs.put(join("eventmesh.http.request.cost.max", "max cost of HTTP."),
            HttpSummaryMetrics::getMaxHTTPCost);
        //avgHTTPCost
        paramPairs.put(join("eventmesh.http.request.cost.avg", "avg cost of HTTP."),
            HttpSummaryMetrics::getAvgHTTPCost);
        //avgHTTPBodyDecodeCost
        paramPairs.put(join("eventmesh.http.body.decode.cost.avg", "avg body decode cost of HTTP."),
            HttpSummaryMetrics::getAvgHTTPBodyDecodeCost);
        //httpDiscard
        paramPairs.put(join("eventmesh.http.request.discard.num", "http request discard num."),
            HttpSummaryMetrics::getHttpDiscard);
        //maxBatchSendMsgTPS
        paramPairs.put(join("eventmesh.batch.send.message.tps.max", "max of batch send message tps."),
            HttpSummaryMetrics::getMaxSendBatchMsgTPS);
        //avgBatchSendMsgTPS
        paramPairs.put(join("eventmesh.batch.send.message.tps.avg", "avg of batch send message tps."),
            HttpSummaryMetrics::getAvgSendBatchMsgTPS);
        //sum
        paramPairs.put(join("eventmesh.batch.send.message.num", "sum of batch send message number."),
            HttpSummaryMetrics::getSendBatchMsgNumSum);
        //sumFail
        paramPairs.put(join("eventmesh.batch.send.message.fail.num", "sum of batch send message fail message number."),
            HttpSummaryMetrics::getSendBatchMsgFailNumSum);
        //sumFailRate
        paramPairs.put(join("eventmesh.batch.send.message.fail.rate", "send batch message fail rate."),
            HttpSummaryMetrics::getSendBatchMsgFailRate);
        //discard
        paramPairs.put(join("eventmesh.batch.send.message.discard.num", "sum of send batch message discard number."),
            HttpSummaryMetrics::getSendBatchMsgDiscardNumSum);
        //maxSendMsgTPS
        paramPairs.put(join("eventmesh.send.message.tps.max", "max of send message tps."),
            HttpSummaryMetrics::getMaxSendMsgTPS);
        //avgSendMsgTPS
        paramPairs.put(join("eventmesh.send.message.tps.avg", "avg of send message tps."),
            HttpSummaryMetrics::getAvgSendMsgTPS);
        //sum
        paramPairs.put(join("eventmesh.send.message.num", "sum of send message number."),
            HttpSummaryMetrics::getSendMsgNumSum);
        //sumFail
        paramPairs.put(join("eventmesh.send.message.fail.num", "sum of send message fail number."),
            HttpSummaryMetrics::getSendMsgFailNumSum);
        //sumFailRate
        paramPairs.put(join("eventmesh.send.message.fail.rate", "send message fail rate."),
            HttpSummaryMetrics::getSendMsgFailRate);
        //replyMsg
        paramPairs.put(join("eventmesh.reply.message.num", "sum of reply message number."),
            HttpSummaryMetrics::getReplyMsgNumSum);
        //replyFail
        paramPairs.put(join("eventmesh.reply.message.fail.num", "sum of reply message fail number."),
            HttpSummaryMetrics::getReplyMsgFailNumSum);
        //maxPushMsgTPS
        paramPairs.put(join("eventmesh.push.message.tps.max", "max of push message tps."),
            HttpSummaryMetrics::getMaxPushMsgTPS);
        //avgPushMsgTPS
        paramPairs.put(join("eventmesh.push.message.tps.avg", "avg of push message tps."),
            HttpSummaryMetrics::getAvgPushMsgTPS);
        //sum
        paramPairs.put(join("eventmesh.http.push.message.num", "sum of http push message number."),
            HttpSummaryMetrics::getHttpPushMsgNumSum);
        //sumFail
        paramPairs.put(join("eventmesh.http.push.message.fail.num", "sum of http push message fail number."),
            HttpSummaryMetrics::getHttpPushFailNumSum);
        //sumFailRate
        paramPairs.put(join("eventmesh.http.push.message.fail.rate", "http push message fail rate."),
            HttpSummaryMetrics::getHttpPushMsgFailRate);
        //maxClientLatency
        paramPairs.put(join("eventmesh.http.push.latency.max", "max of http push latency."),
            HttpSummaryMetrics::getMaxHTTPPushLatency);
        //avgClientLatency
        paramPairs.put(join("eventmesh.http.push.latency.avg", "avg of http push latency."),
            HttpSummaryMetrics::getAvgHTTPPushLatency);
        //batchMsgQ
        paramPairs.put(join("eventmesh.batch.message.queue.size", "size of batch message queue."),
            HttpSummaryMetrics::getBatchMsgQueueSize);
        //sendMsgQ
        paramPairs.put(join("eventmesh.send.message.queue.size", "size of send message queue."),
            HttpSummaryMetrics::getSendMsgQueueSize);
        //pushMsgQ
        paramPairs.put(join("eventmesh.push.message.queue.size", "size of push message queue."),
            HttpSummaryMetrics::getPushMsgQueueSize);
        //httpRetryQ
        paramPairs.put(join("eventmesh.http.retry.queue.size", "size of http retry queue."),
            HttpSummaryMetrics::getHttpRetryQueueSize);
        //batchAvgSend2MQCost
        paramPairs.put(join("eventmesh.batch.send.message.cost.avg", "avg of batch send message cost."),
            HttpSummaryMetrics::getAvgBatchSendMsgCost);
        //avgSend2MQCost
        paramPairs.put(join("eventmesh.send.message.cost.avg", "avg of send message cost."),
            HttpSummaryMetrics::getAvgSendMsgCost);
        //avgReply2MQCost
        paramPairs.put(join("eventmesh.reply.message.cost.avg", "avg of reply message cost."),
            HttpSummaryMetrics::getAvgReplyMsgCost);
    }

    @Override
    protected String getMetricName(String[] metricInfo) {
        return metricInfo[0];
    }

    @Override
    protected String getProtocol() {
        return HTTP.getValue();
    }
}
