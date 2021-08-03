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

package org.apache.eventmesh.runtime.metrics.openTelemetry;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.common.Labels;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.metrics.http.SummaryMetrics;

public class OpenTelemetryExporter {
    OpenTelemetryExporterConfiguration configuration = new OpenTelemetryExporterConfiguration();

    private SummaryMetrics summaryMetrics;

    private Meter meter;

    public OpenTelemetryExporter(SummaryMetrics summaryMetrics, EventMeshHTTPConfiguration eventMeshHTTPConfiguration) {
        this.summaryMetrics = summaryMetrics;

        // it is important to initialize the OpenTelemetry SDK as early as possible in your process.
        MeterProvider meterProvider = configuration.initializeOpenTelemetry(eventMeshHTTPConfiguration);

        meter = meterProvider.get("OpenTelemetryExporter", "0.13.1");
    }

    public void start(){
        //maxHTTPTPS
        meter
                .doubleValueObserverBuilder("eventmesh.http.request.tps.elapsed.max")
                .setDescription("max TPS of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxHTTPTPS(),Labels.empty()))
                .build();

        //avgHTTPTPS
        meter
                .doubleValueObserverBuilder("eventmesh.http.request.tps.elapsed.avg")
                .setDescription("avg TPS of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgHTTPTPS(),Labels.empty()))
                .build();

        //maxHTTPCost
        meter
                .longValueObserverBuilder("eventmesh.http.request.elapsed.max")
                .setDescription("max cost of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxHTTPCost(), Labels.empty()))
                .build();

        //avgHTTPCost
        meter
                .doubleValueObserverBuilder("eventmesh.http.request.elapsed.avg")
                .setDescription("avg cost of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgHTTPCost(), Labels.empty()))
                .build();

        //avgHTTPBodyDecodeCost
        meter
                .doubleValueObserverBuilder("eventmesh.http.body.decode.cost.elapsed.avg")
                .setDescription("avg body decode cost of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgHTTPBodyDecodeCost(), Labels.empty()))
                .build();

        //httpDiscard
        meter
                .longValueObserverBuilder("eventmesh.http.request.discard.elapsed")
                .setDescription("http request discard")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getHttpDiscard(), Labels.empty()))
                .build();

        //maxBatchSendMsgTPS
        meter
                .doubleValueObserverBuilder("eventmesh.batch.send.message.tps.elapsed.max")
                .setDescription("max of batch send message tps")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxSendBatchMsgTPS(), Labels.empty()))
                .build();

        //avgBatchSendMsgTPS
        meter
                .doubleValueObserverBuilder("eventmesh.batch.send.message.tps.elapsed.avg")
                .setDescription("avg of batch send message tps")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgSendBatchMsgTPS(), Labels.empty()))
                .build();

        //sum
        meter
                .doubleValueObserverBuilder("eventmesh.batch.send.message.elapsed.sum")
                .setDescription("sum of batch send message number")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getSendBatchMsgNumSum(), Labels.empty()))
                .build();

        //sumFail
        meter
                .doubleValueObserverBuilder("eventmesh.batch.send.message.fail.elapsed.sum")
                .setDescription("sum of batch send message fail message number")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getSendBatchMsgFailNumSum(), Labels.empty()))
                .build();

        //sumFailRate
        meter
                .doubleValueObserverBuilder("eventmesh.batch.send.message.fail.rate.elapsed")
                .setDescription("send batch message fail rate")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getSendBatchMsgFailRate(), Labels.empty()))
                .build();

        //discard
        meter
                .doubleValueObserverBuilder("eventmesh.batch.send.message.discard.elapsed.sum")
                .setDescription("sum of send batch message discard number")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getSendBatchMsgDiscardNumSum(), Labels.empty()))
                .build();

        //maxSendMsgTPS
        meter
                .doubleValueObserverBuilder("eventmesh.send.message.tps.elapsed.max")
                .setDescription("max of send message tps")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxSendMsgTPS(), Labels.empty()))
                .build();

        //avgSendMsgTPS
        meter
                .doubleValueObserverBuilder("eventmesh.send.message.tps.elapsed.avg")
                .setDescription("avg of send message tps")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgSendMsgTPS(), Labels.empty()))
                .build();

        //sum
        meter
                .doubleValueObserverBuilder("eventmesh.send.message.elapsed.sum")
                .setDescription("sum of send message number")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getSendMsgNumSum(), Labels.empty()))
                .build();

        //sumFail
        meter
                .doubleValueObserverBuilder("eventmesh.send.message.fail.elapsed.sum")
                .setDescription("sum of send message fail number")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getSendMsgFailNumSum(), Labels.empty()))
                .build();

        //sumFailRate
        meter
                .doubleValueObserverBuilder("eventmesh.send.message.fail.rate.elapsed")
                .setDescription("send message fail rate")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getSendMsgFailRate(), Labels.empty()))
                .build();

        //replyMsg
        meter
                .doubleValueObserverBuilder("eventmesh.reply.message.elapsed.sum")
                .setDescription("sum of reply message number")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getReplyMsgNumSum(), Labels.empty()))
                .build();

        //replyFail
        meter
                .doubleValueObserverBuilder("eventmesh.reply.message.fail.elapsed.sum")
                .setDescription("sum of reply message fail number")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getReplyMsgFailNumSum(), Labels.empty()))
                .build();

        //maxPushMsgTPS
        meter
                .doubleValueObserverBuilder("eventmesh.push.message.tps.elapsed.max")
                .setDescription("max of push message tps")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxPushMsgTPS(), Labels.empty()))
                .build();

        //avgPushMsgTPS
        meter
                .doubleValueObserverBuilder("eventmesh.push.message.tps.elapsed.avg")
                .setDescription("avg of push message tps")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgPushMsgTPS(), Labels.empty()))
                .build();

        //sum
        meter
                .doubleValueObserverBuilder("eventmesh.http.push.message.elapsed.sum")
                .setDescription("sum of http push message number")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getHttpPushMsgNumSum(), Labels.empty()))
                .build();

        //sumFail
        meter
                .doubleValueObserverBuilder("eventmesh.http.push.message.fail.elapsed.sum")
                .setDescription("sum of http push message fail number")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getHttpPushFailNumSum(), Labels.empty()))
                .build();

        //sumFailRate
        meter
                .doubleValueObserverBuilder("eventmesh.http.push.message.fail.rate.elapsed")
                .setDescription("http push message fail rate")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.getHttpPushMsgFailRate(), Labels.empty()))
                .build();

        //maxClientLatency
        meter
                .doubleValueObserverBuilder("eventmesh.http.push.latency.elapsed.max")
                .setDescription("max of http push latency")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxHTTPPushLatency(), Labels.empty()))
                .build();

        //avgClientLatency
        meter
                .doubleValueObserverBuilder("eventmesh.http.push.latency.elapsed.avg")
                .setDescription("avg of http push latency")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgHTTPPushLatency(), Labels.empty()))
                .build();

        //batchMsgQ


        //sendMsgQ


        //pushMsgQ


        //httpRetryQ


        //batchAvgSend2MQCost
        meter
                .doubleValueObserverBuilder("eventmesh.batch.send.message.cost.elapsed.avg")
                .setDescription("avg of batch send message cost")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgBatchSendMsgCost(), Labels.empty()))
                .build();

        //avgSend2MQCost
        meter
                .doubleValueObserverBuilder("eventmesh.send.message.cost.elapsed.avg")
                .setDescription("avg of send message cost")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgSendMsgCost(), Labels.empty()))
                .build();

        //avgReply2MQCost
        meter
                .doubleValueObserverBuilder("eventmesh.reply.message.cost.elapsed.avg")
                .setDescription("avg of reply message cost")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgReplyMsgCost(), Labels.empty()))
                .build();
    }

    public void shutdown(){
        configuration.shutdownPrometheusEndpoint();
    }
}
