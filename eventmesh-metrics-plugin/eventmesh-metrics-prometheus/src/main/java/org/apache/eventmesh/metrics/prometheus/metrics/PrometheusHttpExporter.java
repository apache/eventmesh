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
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.observeOfValue;

import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PrometheusHttpExporter {

    /**
     * Map structure : [metric name, description of name] -> the method of get corresponding metric.
     */
    private Map<String[], Function<HttpSummaryMetrics, Number>> paramPairs;

    static {
        paramPairs = new HashMap<String[], Function<HttpSummaryMetrics, Number>>() {

            {
                // maxHTTPTPS
                put(join("eventmesh.http.request.tps.max", "max TPS of HTTP."),
                    HttpSummaryMetrics::maxHTTPTPS);

                // avgHTTPTPS
                put(join("eventmesh.http.request.tps.avg", "avg TPS of HTTP."),
                    HttpSummaryMetrics::avgHTTPTPS);

                // maxHTTPCost
                put(join("eventmesh.http.request.cost.max", "max cost of HTTP."),
                    HttpSummaryMetrics::maxHTTPCost);

                // avgHTTPCost
                put(join("eventmesh.http.request.cost.avg", "avg cost of HTTP."),
                    HttpSummaryMetrics::avgHTTPCost);

                // avgHTTPBodyDecodeCost
                put(join("eventmesh.http.body.decode.cost.avg", "avg body decode cost of HTTP."),
                    HttpSummaryMetrics::avgHTTPBodyDecodeCost);

                // httpDiscard
                put(join("eventmesh.http.request.discard.num", "http request discard num."),
                    HttpSummaryMetrics::getHttpDiscard);

                // maxBatchSendMsgTPS
                put(join("eventmesh.batch.send.message.tps.max", "max of batch send message tps."),
                    HttpSummaryMetrics::maxSendBatchMsgTPS);

                // avgBatchSendMsgTPS
                put(join("eventmesh.batch.send.message.tps.avg", "avg of batch send message tps."),
                    HttpSummaryMetrics::avgSendBatchMsgTPS);

                // sum
                put(join("eventmesh.batch.send.message.num", "sum of batch send message number."),
                    HttpSummaryMetrics::getSendBatchMsgNumSum);

                // sumFail
                put(join("eventmesh.batch.send.message.fail.num", "sum of batch send message fail message number."),
                    HttpSummaryMetrics::getSendBatchMsgFailNumSum);

                // sumFailRate
                put(join("eventmesh.batch.send.message.fail.rate", "send batch message fail rate."),
                    HttpSummaryMetrics::getSendBatchMsgFailRate);
                // discard
                put(join("eventmesh.batch.send.message.discard.num", "sum of send batch message discard number."),
                    HttpSummaryMetrics::getSendBatchMsgDiscardNumSum);

                // maxSendMsgTPS
                put(join("eventmesh.send.message.tps.max", "max of send message tps."),
                    HttpSummaryMetrics::maxSendMsgTPS);

                // avgSendMsgTPS
                put(join("eventmesh.send.message.tps.avg", "avg of send message tps."),
                    HttpSummaryMetrics::avgSendMsgTPS);

                // sum
                put(join("eventmesh.send.message.num", "sum of send message number."),
                    HttpSummaryMetrics::getSendMsgNumSum);

                // sumFail
                put(join("eventmesh.send.message.fail.num", "sum of send message fail number."),
                    HttpSummaryMetrics::getSendMsgFailNumSum);

                // sumFailRate
                put(join("eventmesh.send.message.fail.rate", "send message fail rate."),
                    HttpSummaryMetrics::getSendMsgFailRate);

                // replyMsg
                put(join("eventmesh.reply.message.num", "sum of reply message number."),
                    HttpSummaryMetrics::getReplyMsgNumSum);

                // replyFail
                put(join("eventmesh.reply.message.fail.num", "sum of reply message fail number."),
                    HttpSummaryMetrics::getReplyMsgFailNumSum);

                // maxPushMsgTPS
                put(join("eventmesh.push.message.tps.max", "max of push message tps."),
                    HttpSummaryMetrics::maxPushMsgTPS);

                // avgPushMsgTPS
                put(join("eventmesh.push.message.tps.avg", "avg of push message tps."),
                    HttpSummaryMetrics::avgPushMsgTPS);

                // sum
                put(join("eventmesh.http.push.message.num", "sum of http push message number."),
                    HttpSummaryMetrics::getHttpPushMsgNumSum);
                // sumFail
                put(join("eventmesh.http.push.message.fail.num", "sum of http push message fail number."),
                    HttpSummaryMetrics::getHttpPushFailNumSum);

                // sumFailRate
                put(join("eventmesh.http.push.message.fail.rate", "http push message fail rate."),
                    HttpSummaryMetrics::getHttpPushMsgFailRate);

                // maxClientLatency
                put(join("eventmesh.http.push.latency.max", "max of http push latency."),
                    HttpSummaryMetrics::maxHTTPPushLatency);

                // avgClientLatency
                put(join("eventmesh.http.push.latency.avg", "avg of http push latency."),
                    HttpSummaryMetrics::avgHTTPPushLatency);
                // batchMsgQ
                put(join("eventmesh.batch.message.queue.size", "size of batch message queue."),
                    HttpSummaryMetrics::getBatchMsgQueueSize);

                // sendMsgQ
                put(join("eventmesh.send.message.queue.size", "size of send message queue."),
                    HttpSummaryMetrics::getSendMsgQueueSize);

                // pushMsgQ
                put(join("eventmesh.push.message.queue.size", "size of push message queue."),
                    HttpSummaryMetrics::getPushMsgQueueSize);

                // httpRetryQ
                put(join("eventmesh.http.retry.queue.size", "size of http retry queue."),
                    HttpSummaryMetrics::getHttpRetryQueueSize);

                // batchAvgSend2MQCost
                put(join("eventmesh.batch.send.message.cost.avg", "avg of batch send message cost."),
                    HttpSummaryMetrics::avgBatchSendMsgCost);

                // avgSend2MQCost
                put(join("eventmesh.send.message.cost.avg", "avg of send message cost."),
                    HttpSummaryMetrics::avgSendMsgCost);

                // avgReply2MQCost
                put(join("eventmesh.reply.message.cost.avg", "avg of reply message cost."),
                    HttpSummaryMetrics::avgReplyMsgCost);
            }
        };
    }

    public void export(String name, HttpSummaryMetrics summaryMetrics) {
        Meter meter = GlobalMeterProvider.getMeter(name);
        paramPairs.forEach((metricInfo, getMetric) -> observeOfValue(meter, metricInfo[0], metricInfo[1], HTTP, summaryMetrics, getMetric));
    }

}
