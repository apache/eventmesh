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

package org.apache.eventmesh.runtime.metrics.http;

import org.apache.eventmesh.metrics.api.model.InstrumentFurther;
import org.apache.eventmesh.metrics.api.model.LongCounterMetric;
import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.metrics.api.model.ObservableDoubleGaugeMetric;
import org.apache.eventmesh.metrics.api.model.ObservableLongGaugeMetric;
import org.apache.eventmesh.runtime.metrics.MetricInstrumentUnit;

import org.apache.commons.collections4.MapUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

public class HttpMetrics {

    private static Attributes EMPTY = Attributes.builder().build();

    private static final int STATIC_PERIOD = 30 * 1000;

    private static final String HTTP_METRICS_NAME_PREFIX = "eventmesh.http.";

    private static final String METRIC_NAME = "HTTP";

    private float wholeCost = 0f;

    private final AtomicLong wholeRequestNum = new AtomicLong(0);

    //cumulative value
    private final AtomicLong httpDiscard = new AtomicLong(0);

    private LongCounterMetric httpDiscardMetric;

    private final AtomicLong maxCost = new AtomicLong(0);

    private final AtomicLong httpRequestPerSecond = new AtomicLong(0);

    private final LinkedList<Integer> httpRequestTPSSnapshots = new LinkedList<>();

    private float httpDecodeTimeCost = 0f;

    private final AtomicLong httpDecodeNum = new AtomicLong(0);

    private final AtomicLong sendBatchMsgNumPerSecond = new AtomicLong(0);

    private final AtomicLong sendBatchMsgNumSum = new AtomicLong(0);

    private LongCounterMetric sendBatchMsgNumSumMetric;

    private final AtomicLong sendBatchMsgFailNumSum = new AtomicLong(0);

    private LongCounterMetric sendBatchMsgFailNumSumMetric;

    // This is a cumulative value
    private final AtomicLong sendBatchMsgDiscardNumSum = new AtomicLong(0);

    private LongCounterMetric sendBatchMsgDiscardNumSumMetric;

    private final LinkedList<Integer> sendBatchMsgTPSSnapshots = new LinkedList<Integer>();

    private final AtomicLong sendMsgNumSum = new AtomicLong(0);

    private LongCounterMetric sendMsgNumSumMetric;

    private final AtomicLong sendMsgFailNumSum = new AtomicLong(0);

    private LongCounterMetric sendMsgFailNumSumMetric;

    private final AtomicLong replyMsgNumSum = new AtomicLong(0);

    private LongCounterMetric replyMsgNumSumMetric;

    private final AtomicLong replyMsgFailNumSum = new AtomicLong(0);

    private LongCounterMetric replyMsgFailNumSumMetric;

    private final AtomicLong sendMsgNumPerSecond = new AtomicLong(0);

    private final LinkedList<Integer> sendMsgTPSSnapshots = new LinkedList<Integer>();

    private float wholePushCost = 0f;

    private final AtomicLong wholePushRequestNum = new AtomicLong(0);

    private final AtomicLong maxHttpPushLatency = new AtomicLong(0);

    private final AtomicLong pushMsgNumPerSecond = new AtomicLong(0);

    private final LinkedList<Integer> pushMsgTPSSnapshots = new LinkedList<Integer>();

    private final AtomicLong httpPushMsgNumSum = new AtomicLong(0);

    private LongCounterMetric httpPushMsgNumSumMetric;

    private final AtomicLong httpPushFailNumSum = new AtomicLong(0);

    private LongCounterMetric httpPushFailNumSumMetric;

    private float batchSend2MQWholeCost = 0f;

    private final AtomicLong batchSend2MQNum = new AtomicLong(0);

    private float send2MQWholeCost = 0f;

    private final AtomicLong send2MQNum = new AtomicLong(0);

    private float reply2MQWholeCost = 0f;

    private final AtomicLong reply2MQNum = new AtomicLong(0);

    // execute metrics
    private final ThreadPoolExecutor batchMsgExecutor;

    private final ThreadPoolExecutor sendMsgExecutor;

    private final ThreadPoolExecutor pushMsgExecutor;

    private final DelayQueue<?> httpFailedQueue;

    private final Map<String, String> labelMap;

    private final Map<String, Metric> metrics = new HashMap<>(32);

    private ObservableDoubleGaugeMetric avgHttpBodyDecodeCostMetric;

    private ObservableDoubleGaugeMetric maxHttpTpsMetric;

    private ObservableDoubleGaugeMetric avgHttpTpsMetric;

    private ObservableLongGaugeMetric maxHttpCostMetric;

    private ObservableDoubleGaugeMetric avgHttpCostMetric;

    private ObservableDoubleGaugeMetric maxBatchSendMsgTpsMetric;

    private ObservableDoubleGaugeMetric avgBatchSendMsgTpsMetric;

    private ObservableDoubleGaugeMetric sumBatchFailRateMetric;

    private ObservableDoubleGaugeMetric maxSendMsgTpsMetric;

    private ObservableDoubleGaugeMetric avgSendMsgTpsMetric;

    private ObservableDoubleGaugeMetric sumFailRateMetric;

    private ObservableDoubleGaugeMetric maxPushMsgTpsMetric;

    private ObservableDoubleGaugeMetric avgPushMsgTpsMetric;

    private ObservableDoubleGaugeMetric pushSumFailRateMetric;

    private ObservableDoubleGaugeMetric maxClientLatencyMetric;

    private ObservableDoubleGaugeMetric avgClientLatencyMetric;

    private ObservableLongGaugeMetric batchMsgQMetric;

    private ObservableLongGaugeMetric sendMsgQMetric;

    private ObservableLongGaugeMetric pushMsgQMetric;

    private ObservableLongGaugeMetric httpRetryQMetric;

    private ObservableDoubleGaugeMetric batchAvgSend2MQCostMetric;

    private ObservableDoubleGaugeMetric avgSend2MQCostMetric;

    private ObservableDoubleGaugeMetric avgReply2MQCostMetric;

    public HttpMetrics(final ThreadPoolExecutor batchMsgExecutor,
        final ThreadPoolExecutor sendMsgExecutor,
        final ThreadPoolExecutor pushMsgExecutor,
        final DelayQueue<?> httpFailedQueue,
        final Map<String, String> labelMap) {
        this.batchMsgExecutor = batchMsgExecutor;
        this.sendMsgExecutor = sendMsgExecutor;
        this.pushMsgExecutor = pushMsgExecutor;
        this.httpFailedQueue = httpFailedQueue;
        this.labelMap = Optional.ofNullable(labelMap).orElse(new HashMap<>(0));
        initMetrics();
    }

    private void initMetrics() {

        InstrumentFurther furtherHttpDiscard = new InstrumentFurther();
        furtherHttpDiscard.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherHttpDiscard.setDescription("Http request discard num.");
        furtherHttpDiscard.setName(HTTP_METRICS_NAME_PREFIX + "request.discard.num");
        httpDiscardMetric = new LongCounterMetric(furtherHttpDiscard, METRIC_NAME);
        metrics.put("httpDiscardMetric", httpDiscardMetric);

        //sum of batch send message number
        InstrumentFurther furtherSendBatchMsgNumSum = new InstrumentFurther();
        furtherSendBatchMsgNumSum.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherSendBatchMsgNumSum.setDescription("Sum of batch send message number.");
        furtherSendBatchMsgNumSum.setName(HTTP_METRICS_NAME_PREFIX + "batch.send.message.num");
        sendBatchMsgNumSumMetric = new LongCounterMetric(furtherSendBatchMsgNumSum, METRIC_NAME);
        metrics.put("sendBatchMsgNumSumMetric", sendBatchMsgNumSumMetric);

        //sum of batch send message fail message number.
        InstrumentFurther furtherSendBatchMsgFailNumSum = new InstrumentFurther();
        furtherSendBatchMsgFailNumSum.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherSendBatchMsgFailNumSum.setDescription("Sum of batch send message fail message number.");
        furtherSendBatchMsgFailNumSum.setName(HTTP_METRICS_NAME_PREFIX + "batch.send.message.fail.num");
        sendBatchMsgFailNumSumMetric = new LongCounterMetric(furtherSendBatchMsgFailNumSum, METRIC_NAME);
        metrics.put("sendBatchMsgFailNumSumMetric", sendBatchMsgFailNumSumMetric);

        //sum of send batch message discard number.
        InstrumentFurther furtherSendBatchMsgDiscardNumSum = new InstrumentFurther();
        furtherSendBatchMsgDiscardNumSum.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherSendBatchMsgDiscardNumSum.setDescription("Sum of batch send message fail message number.");
        furtherSendBatchMsgDiscardNumSum.setName(HTTP_METRICS_NAME_PREFIX + "batch.send.message.discard.num");
        sendBatchMsgDiscardNumSumMetric = new LongCounterMetric(furtherSendBatchMsgDiscardNumSum, METRIC_NAME);
        metrics.put("sendBatchMsgDiscardNumSumMetric", sendBatchMsgDiscardNumSumMetric);

        //Sum of send message number.
        InstrumentFurther furtherSendMsgNumSum = new InstrumentFurther();
        furtherSendMsgNumSum.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherSendMsgNumSum.setDescription("Sum of send message number.");
        furtherSendMsgNumSum.setName(HTTP_METRICS_NAME_PREFIX + "send.message.num");
        sendMsgNumSumMetric = new LongCounterMetric(furtherSendMsgNumSum, METRIC_NAME);
        metrics.put("sendMsgNumSumMetric", sendMsgNumSumMetric);

        //Sum of send message fail number.
        InstrumentFurther furtherSendMsgFailNumSum = new InstrumentFurther();
        furtherSendMsgFailNumSum.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherSendMsgFailNumSum.setDescription("Sum of send message fail number.");
        furtherSendMsgFailNumSum.setName(HTTP_METRICS_NAME_PREFIX + "send.message.fail.num");
        sendMsgFailNumSumMetric = new LongCounterMetric(furtherSendMsgFailNumSum, METRIC_NAME);
        metrics.put("sendMsgFailNumSumMetric", sendMsgFailNumSumMetric);

        //Sum of reply message number.
        InstrumentFurther furtherReplyMsgNumSum = new InstrumentFurther();
        furtherReplyMsgNumSum.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherReplyMsgNumSum.setDescription("Sum of reply message number.");
        furtherReplyMsgNumSum.setName(HTTP_METRICS_NAME_PREFIX + "reply.message.num");
        replyMsgNumSumMetric = new LongCounterMetric(furtherReplyMsgNumSum, METRIC_NAME);
        metrics.put("replyMsgNumSumMetric", replyMsgNumSumMetric);

        //Sum of reply message fail number.
        InstrumentFurther furtherReplyMsgFailNumSum = new InstrumentFurther();
        furtherReplyMsgFailNumSum.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherReplyMsgFailNumSum.setDescription("Sum of reply message fail number.");
        furtherReplyMsgFailNumSum.setName(HTTP_METRICS_NAME_PREFIX + "reply.message.fail.num");
        replyMsgFailNumSumMetric = new LongCounterMetric(furtherReplyMsgFailNumSum, METRIC_NAME);
        metrics.put("replyMsgFailNumSumMetric", replyMsgFailNumSumMetric);

        //Sum of http push message number.
        InstrumentFurther furtherHttpPushMsgNumSum = new InstrumentFurther();
        furtherHttpPushMsgNumSum.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherHttpPushMsgNumSum.setDescription("Sum of http push message number.");
        furtherHttpPushMsgNumSum.setName(HTTP_METRICS_NAME_PREFIX + "push.message.num");
        httpPushMsgNumSumMetric = new LongCounterMetric(furtherHttpPushMsgNumSum, METRIC_NAME);
        metrics.put("httpPushMsgNumSumMetric", httpPushMsgNumSumMetric);

        //Sum of http push message fail number.
        InstrumentFurther furtherHttpPushFailNumSum = new InstrumentFurther();
        furtherHttpPushFailNumSum.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherHttpPushFailNumSum.setDescription("Sum of http push message fail number.");
        furtherHttpPushFailNumSum.setName(HTTP_METRICS_NAME_PREFIX + "push.message.fail.num");
        httpPushFailNumSumMetric = new LongCounterMetric(furtherHttpPushFailNumSum, METRIC_NAME);
        metrics.put("httpPushFailNumSumMetric", httpPushFailNumSumMetric);

        //avg body decode cost time of http
        InstrumentFurther furtherHttpDecode = new InstrumentFurther();
        furtherHttpDecode.setUnit(MetricInstrumentUnit.MILLISECONDS);
        furtherHttpDecode.setDescription("Avg body decode cost time of http");
        furtherHttpDecode.setName(HTTP_METRICS_NAME_PREFIX + "body.decode.cost.avg");
        avgHttpBodyDecodeCostMetric = new ObservableDoubleGaugeMetric(furtherHttpDecode, METRIC_NAME, () -> this.avgHTTPBodyDecodeCost());
        avgHttpBodyDecodeCostMetric.putAll(labelMap);
        metrics.put("avgHttpBodyDecodeCostMetric", avgHttpBodyDecodeCostMetric);

        //Max TPS of HTTP.
        InstrumentFurther furtherMaxHttpTps = new InstrumentFurther();
        furtherMaxHttpTps.setUnit(MetricInstrumentUnit.TPS);
        furtherMaxHttpTps.setDescription("Max TPS of HTTP.");
        furtherMaxHttpTps.setName(HTTP_METRICS_NAME_PREFIX + "request.tps.max");
        maxHttpTpsMetric = new ObservableDoubleGaugeMetric(furtherMaxHttpTps, METRIC_NAME, () -> this.maxHTTPTPS());
        maxHttpTpsMetric.putAll(labelMap);
        metrics.put("maxHttpTpsMetric", maxHttpTpsMetric);

        //Avg TPS of HTTP.
        InstrumentFurther furtherAvgHttpTps = new InstrumentFurther();
        furtherAvgHttpTps.setUnit(MetricInstrumentUnit.TPS);
        furtherAvgHttpTps.setDescription("Avg TPS of HTTP.");
        furtherAvgHttpTps.setName(HTTP_METRICS_NAME_PREFIX + "request.tps.avg");
        avgHttpTpsMetric = new ObservableDoubleGaugeMetric(furtherAvgHttpTps, METRIC_NAME, () -> this.avgHTTPTPS());
        avgHttpTpsMetric.putAll(labelMap);
        metrics.put("avgHttpTpsMetric", avgHttpTpsMetric);

        //max cost of HTTP.
        InstrumentFurther furtherMaxCostHttpTps = new InstrumentFurther();
        furtherMaxCostHttpTps.setUnit(MetricInstrumentUnit.MILLISECONDS);
        furtherMaxCostHttpTps.setDescription("Max cost of HTTP.");
        furtherMaxCostHttpTps.setName(HTTP_METRICS_NAME_PREFIX + "request.cost.max");
        maxHttpCostMetric = new ObservableLongGaugeMetric(furtherMaxCostHttpTps, METRIC_NAME, () -> this.maxHTTPCost());
        maxHttpCostMetric.putAll(labelMap);
        metrics.put("maxHttpCostMetric", maxHttpCostMetric);

        //Avg cost of HTTP.
        InstrumentFurther furtherAvgHttpCost = new InstrumentFurther();
        furtherAvgHttpCost.setUnit(MetricInstrumentUnit.TPS);
        furtherAvgHttpCost.setDescription("Avg cost of HTTP.");
        furtherAvgHttpCost.setName(HTTP_METRICS_NAME_PREFIX + "request.cost.avg");
        avgHttpCostMetric = new ObservableDoubleGaugeMetric(furtherAvgHttpCost, METRIC_NAME, () -> this.avgHTTPCost());
        avgHttpCostMetric.putAll(labelMap);
        metrics.put("avgHttpCostMetric", avgHttpCostMetric);

        //Max of batch send message tps
        InstrumentFurther furtherMaxBatchSendMsgTps = new InstrumentFurther();
        furtherMaxBatchSendMsgTps.setUnit(MetricInstrumentUnit.TPS);
        furtherMaxBatchSendMsgTps.setDescription("Max of batch send message tps");
        furtherMaxBatchSendMsgTps.setName(HTTP_METRICS_NAME_PREFIX + "batch.send.message.tps.max");
        maxBatchSendMsgTpsMetric = new ObservableDoubleGaugeMetric(furtherMaxBatchSendMsgTps, METRIC_NAME, () -> this.maxSendBatchMsgTPS());
        maxBatchSendMsgTpsMetric.putAll(labelMap);
        metrics.put("maxBatchSendMsgTpsMetric", maxBatchSendMsgTpsMetric);

        //Avg of batch send message tps.
        InstrumentFurther furtherAvgBatchSendMsgTps = new InstrumentFurther();
        furtherAvgBatchSendMsgTps.setUnit(MetricInstrumentUnit.TPS);
        furtherAvgBatchSendMsgTps.setDescription("Avg of batch send message tps.");
        furtherAvgBatchSendMsgTps.setName(HTTP_METRICS_NAME_PREFIX + "batch.send.message.tps.avg");
        avgBatchSendMsgTpsMetric = new ObservableDoubleGaugeMetric(furtherAvgBatchSendMsgTps, METRIC_NAME, () -> this.avgSendBatchMsgTPS());
        avgBatchSendMsgTpsMetric.putAll(labelMap);
        metrics.put("avgBatchSendMsgTpsMetric", avgBatchSendMsgTpsMetric);

        //Send batch message fail rate.
        InstrumentFurther furtherSumBatchFailRate = new InstrumentFurther();
        furtherSumBatchFailRate.setUnit(MetricInstrumentUnit.PERCENT);
        furtherSumBatchFailRate.setDescription("Send batch message fail rate.");
        furtherSumBatchFailRate.setName(HTTP_METRICS_NAME_PREFIX + "batch.send.message.fail.rate");
        sumBatchFailRateMetric = new ObservableDoubleGaugeMetric(furtherSumBatchFailRate, METRIC_NAME, () -> this.getSendBatchMsgFailRate());
        sumBatchFailRateMetric.putAll(labelMap);
        metrics.put("sumBatchFailRateMetric", sumBatchFailRateMetric);

        //Max of send message tps
        InstrumentFurther furtherMaxSendMsgTps = new InstrumentFurther();
        furtherMaxSendMsgTps.setUnit(MetricInstrumentUnit.TPS);
        furtherMaxSendMsgTps.setDescription("Max of send message tps");
        furtherMaxSendMsgTps.setName(HTTP_METRICS_NAME_PREFIX + "send.message.tps.max");
        maxSendMsgTpsMetric = new ObservableDoubleGaugeMetric(furtherMaxSendMsgTps, METRIC_NAME, () -> this.maxSendMsgTPS());
        maxSendMsgTpsMetric.putAll(labelMap);
        metrics.put("maxSendMsgTpsMetric", maxSendMsgTpsMetric);

        //Avg of send message tps
        InstrumentFurther furtherAvgSendMsgTps = new InstrumentFurther();
        furtherAvgSendMsgTps.setUnit(MetricInstrumentUnit.TPS);
        furtherAvgSendMsgTps.setDescription("Avg of send message tps");
        furtherAvgSendMsgTps.setName(HTTP_METRICS_NAME_PREFIX + "send.message.tps.avg");
        avgSendMsgTpsMetric = new ObservableDoubleGaugeMetric(furtherAvgSendMsgTps, METRIC_NAME, () -> this.avgSendMsgTPS());
        avgSendMsgTpsMetric.putAll(labelMap);
        metrics.put("avgSendMsgTpsMetric", avgSendMsgTpsMetric);

        //Send message fail rate.
        InstrumentFurther furtherSumFailRate = new InstrumentFurther();
        furtherSumFailRate.setUnit(MetricInstrumentUnit.PERCENT);
        furtherSumFailRate.setDescription("Send message fail rate.");
        furtherSumFailRate.setName(HTTP_METRICS_NAME_PREFIX + "send.message.fail.rate");
        sumFailRateMetric = new ObservableDoubleGaugeMetric(furtherSumFailRate, METRIC_NAME, () -> this.getSendBatchMsgFailRate());
        sumFailRateMetric.putAll(labelMap);
        metrics.put("sumFailRateMetric", sumFailRateMetric);

        //Max of push message tps.
        InstrumentFurther furtherMaxPushMsgTps = new InstrumentFurther();
        furtherMaxPushMsgTps.setUnit(MetricInstrumentUnit.TPS);
        furtherMaxPushMsgTps.setDescription("Max of push message tps.");
        furtherMaxPushMsgTps.setName(HTTP_METRICS_NAME_PREFIX + "push.message.tps.max");
        maxPushMsgTpsMetric = new ObservableDoubleGaugeMetric(furtherMaxPushMsgTps, METRIC_NAME, () -> this.maxPushMsgTPS());
        maxPushMsgTpsMetric.putAll(labelMap);
        metrics.put("maxPushMsgTpsMetric", maxPushMsgTpsMetric);

        //Avg of push message tps.
        InstrumentFurther furtherAvgPushMsgTps = new InstrumentFurther();
        furtherAvgPushMsgTps.setUnit(MetricInstrumentUnit.TPS);
        furtherAvgPushMsgTps.setDescription("Avg of push message tps.");
        furtherAvgPushMsgTps.setName(HTTP_METRICS_NAME_PREFIX + "push.message.tps.avg");
        avgPushMsgTpsMetric = new ObservableDoubleGaugeMetric(furtherAvgPushMsgTps, METRIC_NAME, () -> this.avgPushMsgTPS());
        avgPushMsgTpsMetric.putAll(labelMap);
        metrics.put("avgPushMsgTpsMetric", avgPushMsgTpsMetric);

        //Http push message fail rate.
        InstrumentFurther furtherPushSumFailRate = new InstrumentFurther();
        furtherPushSumFailRate.setUnit(MetricInstrumentUnit.PERCENT);
        furtherPushSumFailRate.setDescription("Http push message fail rate.");
        furtherPushSumFailRate.setName(HTTP_METRICS_NAME_PREFIX + "push.message.fail.rate");
        pushSumFailRateMetric = new ObservableDoubleGaugeMetric(furtherPushSumFailRate, METRIC_NAME, () -> this.getHttpPushMsgFailRate());
        pushSumFailRateMetric.putAll(labelMap);
        metrics.put("pushSumFailRateMetric", pushSumFailRateMetric);

        //Max of http push latency.
        InstrumentFurther furtherMaxClientLatency = new InstrumentFurther();
        furtherMaxClientLatency.setUnit(MetricInstrumentUnit.MILLISECONDS);
        furtherMaxClientLatency.setDescription("Max of http push latency.");
        furtherMaxClientLatency.setName(HTTP_METRICS_NAME_PREFIX + "push.latency.max");
        maxClientLatencyMetric = new ObservableDoubleGaugeMetric(furtherMaxClientLatency, METRIC_NAME, () -> this.maxHTTPPushLatency());
        maxClientLatencyMetric.putAll(labelMap);
        metrics.put("maxClientLatencyMetric", maxClientLatencyMetric);

        //Avg of http push latency.
        InstrumentFurther furtherAvgClientLatency = new InstrumentFurther();
        furtherAvgClientLatency.setUnit(MetricInstrumentUnit.MILLISECONDS);
        furtherAvgClientLatency.setDescription("Avg of http push latency.");
        furtherAvgClientLatency.setName(HTTP_METRICS_NAME_PREFIX + "push.latency.avg");
        avgClientLatencyMetric = new ObservableDoubleGaugeMetric(furtherAvgClientLatency, METRIC_NAME, () -> this.avgHTTPPushLatency());
        avgClientLatencyMetric.putAll(labelMap);
        metrics.put("avgClientLatencyMetric", avgClientLatencyMetric);

        //Size of batch message queue.
        InstrumentFurther furtherBatchMsgQ = new InstrumentFurther();
        furtherBatchMsgQ.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherBatchMsgQ.setDescription("Size of batch message queue.");
        furtherBatchMsgQ.setName(HTTP_METRICS_NAME_PREFIX + "batch.message.queue.size");
        batchMsgQMetric = new ObservableLongGaugeMetric(furtherBatchMsgQ, METRIC_NAME, () -> this.getBatchMsgQueueSize());
        batchMsgQMetric.putAll(labelMap);
        metrics.put("batchMsgQMetric", batchMsgQMetric);

        //Size of send message queue.
        InstrumentFurther furtherSendMsgQ = new InstrumentFurther();
        furtherSendMsgQ.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherSendMsgQ.setDescription("Size of send message queue.");
        furtherSendMsgQ.setName(HTTP_METRICS_NAME_PREFIX + "send.message.queue.size");
        sendMsgQMetric = new ObservableLongGaugeMetric(furtherSendMsgQ, METRIC_NAME, () -> this.getSendMsgQueueSize());
        sendMsgQMetric.putAll(labelMap);
        metrics.put("sendMsgQMetric", sendMsgQMetric);

        //Size of push message queue.
        InstrumentFurther furtherPushMsgQ = new InstrumentFurther();
        furtherPushMsgQ.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherPushMsgQ.setDescription("Size of push message queue.");
        furtherPushMsgQ.setName(HTTP_METRICS_NAME_PREFIX + "push.message.queue.size");
        pushMsgQMetric = new ObservableLongGaugeMetric(furtherPushMsgQ, METRIC_NAME, () -> this.getPushMsgQueueSize());
        pushMsgQMetric.putAll(labelMap);
        metrics.put("pushMsgQMetric", pushMsgQMetric);

        //Size of  http retry queue.
        InstrumentFurther furtherHttpRetryQ = new InstrumentFurther();
        furtherHttpRetryQ.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherHttpRetryQ.setDescription("Size of http retry queue.");
        furtherHttpRetryQ.setName(HTTP_METRICS_NAME_PREFIX + "retry.queue.size");
        httpRetryQMetric = new ObservableLongGaugeMetric(furtherHttpRetryQ, METRIC_NAME, () -> this.getHttpRetryQueueSize());
        httpRetryQMetric.putAll(labelMap);
        metrics.put("httpRetryQMetric", httpRetryQMetric);


        //Avg of batch send message cost.
        InstrumentFurther furtherBatchAvgSend2MQCost = new InstrumentFurther();
        furtherBatchAvgSend2MQCost.setUnit(MetricInstrumentUnit.MILLISECONDS);
        furtherBatchAvgSend2MQCost.setDescription("Avg of batch send message cost.");
        furtherBatchAvgSend2MQCost.setName(HTTP_METRICS_NAME_PREFIX + "batch.send.message.cost.avg");
        batchAvgSend2MQCostMetric = new ObservableDoubleGaugeMetric(furtherBatchAvgSend2MQCost, METRIC_NAME, () -> this.avgBatchSendMsgCost());
        batchAvgSend2MQCostMetric.putAll(labelMap);
        metrics.put("avgClientLatencyMetric", batchAvgSend2MQCostMetric);

        //Avg of send message cost.
        InstrumentFurther furtherAvgSend2MQCost = new InstrumentFurther();
        furtherAvgSend2MQCost.setUnit(MetricInstrumentUnit.TPS);
        furtherAvgSend2MQCost.setDescription("Avg of send message cost.");
        furtherAvgSend2MQCost.setName(HTTP_METRICS_NAME_PREFIX + "send.message.cost.avg");
        avgSend2MQCostMetric = new ObservableDoubleGaugeMetric(furtherAvgSend2MQCost, METRIC_NAME, () -> this.avgSendMsgCost());
        avgSend2MQCostMetric.putAll(labelMap);
        metrics.put("avgSend2MQCostMetric", avgSend2MQCostMetric);

        //Avg of reply message cost.
        InstrumentFurther furtherAvgReply2MQCost = new InstrumentFurther();
        furtherAvgReply2MQCost.setUnit(MetricInstrumentUnit.TPS);
        furtherAvgReply2MQCost.setDescription("Avg of reply message cost.");
        furtherAvgReply2MQCost.setName(HTTP_METRICS_NAME_PREFIX + "reply.message.cost.avg");
        avgReply2MQCostMetric = new ObservableDoubleGaugeMetric(furtherAvgReply2MQCost, METRIC_NAME, () -> this.avgReplyMsgCost());
        avgReply2MQCostMetric.putAll(labelMap);
        metrics.put("avgReply2MQCostMetric", avgReply2MQCostMetric);
    }

    public Collection<Metric> getMetrics() {
        return metrics.values();
    }

    public double avgHTTPCost() {
        return (wholeRequestNum.longValue() == 0L) ? 0f : wholeCost / wholeRequestNum.longValue();
    }

    public long maxHTTPCost() {
        return maxCost.longValue();
    }

    public long getHttpDiscard() {
        return httpDiscard.longValue();
    }

    public void recordHTTPRequest() {
        httpRequestPerSecond.incrementAndGet();
    }

    public void recordHTTPDiscard() {
        httpDiscard.incrementAndGet();
        Map<String, String> attributes = new HashMap<>(this.labelMap);
        httpDiscardMetric.getInstrument().add(1, buildAttributes(attributes));
    }

    private static Attributes buildAttributes(final Map<String, String> attributes) {
        if (MapUtils.isEmpty(attributes)) {
            return EMPTY;
        }
        AttributesBuilder attributesBuilder = Attributes.builder();
        attributes.forEach(attributesBuilder::put);
        return attributesBuilder.build();
    }

    public void snapshotHTTPTPS() {
        Integer tps = httpRequestPerSecond.intValue();
        httpRequestTPSSnapshots.add(tps);
        httpRequestPerSecond.set(0);
        if (httpRequestTPSSnapshots.size() > STATIC_PERIOD / 1000) {
            httpRequestTPSSnapshots.removeFirst();
        }
    }

    public double maxHTTPTPS() {
        return Collections.max(httpRequestTPSSnapshots);
    }

    public double avgHTTPTPS() {
        return avg(httpRequestTPSSnapshots);
    }

    public void recordHTTPReqResTimeCost(long cost) {
        wholeRequestNum.incrementAndGet();
        wholeCost = wholeCost + cost;
        if (cost > maxCost.longValue()) {
            maxCost.set(cost);
        }
    }

    public void httpStatInfoClear() {
        wholeRequestNum.set(0L);
        wholeCost = 0f;
        maxCost.set(0L);
        httpDecodeNum.set(0L);
        httpDecodeTimeCost = 0f;
    }


    public void recordDecodeTimeCost(long cost) {
        httpDecodeNum.incrementAndGet();
        httpDecodeTimeCost = httpDecodeTimeCost + cost;
    }

    public double avgHTTPBodyDecodeCost() {
        return (httpDecodeNum.longValue() == 0L) ? 0d : (double) httpDecodeTimeCost / httpDecodeNum.longValue();
    }


    public void recordSendBatchMsgDiscard(long delta) {
        sendBatchMsgDiscardNumSum.addAndGet(delta);
        sendBatchMsgDiscardNumSumMetric.getInstrument().add(delta, buildAttributes(labelMap));
    }

    public void snapshotSendBatchMsgTPS() {
        Integer tps = sendBatchMsgNumPerSecond.intValue();
        sendBatchMsgTPSSnapshots.add(tps);
        sendBatchMsgNumPerSecond.set(0);
        if (sendBatchMsgTPSSnapshots.size() > STATIC_PERIOD / 1000) {
            sendBatchMsgTPSSnapshots.removeFirst();
        }
    }

    public double maxSendBatchMsgTPS() {
        return Collections.max(sendBatchMsgTPSSnapshots);
    }

    public double avgSendBatchMsgTPS() {
        return avg(sendBatchMsgTPSSnapshots);
    }

    public void recordSendBatchMsg(long delta) {
        sendBatchMsgNumPerSecond.addAndGet(delta);
        sendBatchMsgNumSum.addAndGet(delta);
        sendBatchMsgNumSumMetric.getInstrument().add(delta, buildAttributes(labelMap));
    }

    public void recordSendBatchMsgFailed(long delta) {
        sendBatchMsgFailNumSum.getAndAdd(delta);
        sendBatchMsgFailNumSumMetric.getInstrument().add(delta, buildAttributes(labelMap));
    }

    public long getSendBatchMsgNumSum() {
        return sendBatchMsgNumSum.longValue();
    }

    public long getSendBatchMsgFailNumSum() {
        return sendBatchMsgFailNumSum.longValue();
    }

    public double getSendBatchMsgFailRate() {
        return (sendBatchMsgNumSum.longValue() == 0L) ? 0f : sendBatchMsgFailNumSum.floatValue() / sendBatchMsgNumSum.longValue();
    }

    public void cleanSendBatchStat() {
        sendBatchMsgNumSum.set(0L);
        sendBatchMsgFailNumSum.set(0L);
    }

    public long getSendBatchMsgDiscardNumSum() {
        return sendBatchMsgDiscardNumSum.longValue();
    }


    public void snapshotSendMsgTPS() {
        Integer tps = sendMsgNumPerSecond.intValue();
        sendMsgTPSSnapshots.add(tps);
        sendMsgNumPerSecond.set(0);
        if (sendMsgTPSSnapshots.size() > STATIC_PERIOD / 1000) {
            sendMsgTPSSnapshots.removeFirst();
        }
    }

    public double maxSendMsgTPS() {
        return Collections.max(sendMsgTPSSnapshots);
    }

    public double avgSendMsgTPS() {
        return avg(sendMsgTPSSnapshots);
    }

    public void recordSendMsg() {
        sendMsgNumPerSecond.incrementAndGet();
        sendMsgNumSum.incrementAndGet();
        sendMsgNumSumMetric.getInstrument().add(1, buildAttributes(labelMap));
    }

    public void recordReplyMsg() {
        replyMsgNumSum.incrementAndGet();
        replyMsgNumSumMetric.getInstrument().add(1, buildAttributes(labelMap));
    }

    public void recordReplyMsgFailed() {
        replyMsgFailNumSum.incrementAndGet();
        replyMsgFailNumSumMetric.getInstrument().add(1, buildAttributes(labelMap));
    }

    public long getReplyMsgNumSum() {
        return replyMsgNumSum.longValue();
    }

    public long getReplyMsgFailNumSum() {
        return replyMsgFailNumSum.longValue();
    }

    public long getSendMsgNumSum() {
        return sendMsgNumSum.longValue();
    }

    public long getSendMsgFailNumSum() {
        return sendMsgFailNumSum.longValue();
    }

    public float getSendMsgFailRate() {
        return (sendMsgNumSum.longValue() == 0L) ? 0f : sendMsgFailNumSum.floatValue() / sendMsgNumSum.longValue();
    }

    public void recordSendMsgFailed() {
        sendMsgFailNumSum.incrementAndGet();
        sendMsgFailNumSumMetric.getInstrument().add(1, buildAttributes(labelMap));
    }

    public void cleanSendMsgStat() {
        sendMsgNumSum.set(0L);
        replyMsgNumSum.set(0L);
        sendMsgFailNumSum.set(0L);
        replyMsgFailNumSum.set(0L);
    }


    public void snapshotPushMsgTPS() {
        Integer tps = pushMsgNumPerSecond.intValue();
        pushMsgTPSSnapshots.add(tps);
        pushMsgNumPerSecond.set(0);
        if (pushMsgTPSSnapshots.size() > STATIC_PERIOD / 1000) {
            pushMsgTPSSnapshots.removeFirst();
        }
    }

    public void recordHTTPPushTimeCost(long cost) {
        wholePushRequestNum.incrementAndGet();
        wholePushCost = wholePushCost + cost;
        if (cost > maxHttpPushLatency.longValue()) {
            maxHttpPushLatency.set(cost);
        }
    }

    public double avgHTTPPushLatency() {
        return (wholePushRequestNum.longValue() == 0L) ? 0f : wholePushCost / wholePushRequestNum.longValue();
    }

    public double maxHTTPPushLatency() {
        return maxHttpPushLatency.floatValue();
    }

    public double maxPushMsgTPS() {
        return Collections.max(pushMsgTPSSnapshots);
    }

    public double avgPushMsgTPS() {
        return avg(pushMsgTPSSnapshots);
    }

    public void recordPushMsg() {
        pushMsgNumPerSecond.incrementAndGet();
        httpPushMsgNumSum.incrementAndGet();
        httpPushMsgNumSumMetric.getInstrument().add(1, buildAttributes(labelMap));
    }

    public long getHttpPushMsgNumSum() {
        return httpPushMsgNumSum.longValue();
    }

    public long getHttpPushFailNumSum() {
        return httpPushFailNumSum.longValue();
    }

    public double getHttpPushMsgFailRate() {
        return (httpPushMsgNumSum.longValue() == 0L) ? 0f : httpPushFailNumSum.floatValue() / httpPushMsgNumSum.longValue();
    }

    public void recordHttpPushMsgFailed() {
        sendMsgFailNumSum.incrementAndGet();
        sendMsgFailNumSumMetric.getInstrument().add(1, buildAttributes(labelMap));
    }

    public void cleanHttpPushMsgStat() {
        httpPushFailNumSum.set(0L);
        httpPushMsgNumSum.set(0L);
        wholeRequestNum.set(0L);
        wholeCost = 0f;
        maxCost.set(0L);
    }


    public void recordBatchSendMsgCost(long cost) {
        batchSend2MQNum.incrementAndGet();
        batchSend2MQWholeCost = batchSend2MQWholeCost + cost;
    }

    public double avgBatchSendMsgCost() {
        return (batchSend2MQNum.intValue() == 0) ? 0f : batchSend2MQWholeCost / batchSend2MQNum.intValue();
    }

    public void recordSendMsgCost(long cost) {
        send2MQNum.incrementAndGet();
        send2MQWholeCost = send2MQWholeCost + cost;
    }

    public double avgSendMsgCost() {
        return (send2MQNum.intValue() == 0) ? 0f : send2MQWholeCost / send2MQNum.intValue();
    }

    public void recordReplyMsgCost(long cost) {
        reply2MQNum.incrementAndGet();
        reply2MQWholeCost = reply2MQWholeCost + cost;
    }

    public double avgReplyMsgCost() {
        return (reply2MQNum.intValue() == 0) ? 0f : reply2MQWholeCost / reply2MQNum.intValue();
    }

    public void send2MQStatInfoClear() {
        batchSend2MQWholeCost = 0f;
        batchSend2MQNum.set(0L);
        send2MQWholeCost = 0f;
        send2MQNum.set(0L);
        reply2MQWholeCost = 0f;
        reply2MQNum.set(0L);
    }


    public long getBatchMsgQueueSize() {
        return batchMsgExecutor.getQueue().size();
    }

    public long getSendMsgQueueSize() {
        return sendMsgExecutor.getQueue().size();
    }

    public long getPushMsgQueueSize() {
        return pushMsgExecutor.getQueue().size();
    }

    public long getHttpRetryQueueSize() {
        return httpFailedQueue.size();
    }


    private float avg(LinkedList<Integer> linkedList) {
        if (linkedList.isEmpty()) {
            return 0.0f;
        }

        int sum = linkedList.stream().reduce(Integer::sum).get();
        return (float) sum / linkedList.size();
    }
}
