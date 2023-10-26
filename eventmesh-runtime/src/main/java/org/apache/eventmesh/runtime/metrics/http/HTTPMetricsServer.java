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

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HTTPMetricsServer {

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    private final transient List<MetricsRegistry> metricsRegistries;

    private final transient HttpSummaryMetrics summaryMetrics;

    public HTTPMetricsServer(final EventMeshHTTPServer eventMeshHTTPServer,
        final List<MetricsRegistry> metricsRegistries) {
        Objects.requireNonNull(eventMeshHTTPServer, "EventMeshHTTPServer can not be null");
        Objects.requireNonNull(metricsRegistries, "List<MetricsRegistry> can not be null");

        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.metricsRegistries = metricsRegistries;
        this.summaryMetrics = new HttpSummaryMetrics(
            eventMeshHTTPServer.getHttpThreadPoolGroup().getBatchMsgExecutor(),
            eventMeshHTTPServer.getHttpThreadPoolGroup().getSendMsgExecutor(),
            eventMeshHTTPServer.getHttpThreadPoolGroup().getPushMsgExecutor(),
            eventMeshHTTPServer.getHttpRetryer().getRetryQueue());

        init();
    }

    private void init() {
        metricsRegistries.forEach(MetricsRegistry::start);
        LogUtils.info(log, "HTTPMetricsServer initialized.");
    }

    public void start() {
        metricsRegistries.forEach(metricsRegistry -> {
            metricsRegistry.register(summaryMetrics);
            LogUtils.info(log, "Register httpMetrics to {}", metricsRegistry.getClass().getName());
        });

        metricsSchedule.scheduleAtFixedRate(() -> {
            try {
                summaryMetrics.snapshotHTTPTPS();
                summaryMetrics.snapshotSendBatchMsgTPS();
                summaryMetrics.snapshotSendMsgTPS();
                summaryMetrics.snapshotPushMsgTPS();
            } catch (Exception ex) {
                log.error("eventMesh snapshot tps metrics err", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        metricsSchedule.scheduleAtFixedRate(() -> {
            try {
                logPrintServerMetrics(summaryMetrics, eventMeshHTTPServer);
            } catch (Exception ex) {
                log.error("eventMesh print metrics err", ex);
            }
        }, 1000, 30 * 1000, TimeUnit.MILLISECONDS);

        LogUtils.info(log, "HTTPMetricsServer started.");
    }

    public void shutdown() {
        metricsSchedule.shutdown();
        metricsRegistries.forEach(MetricsRegistry::showdown);
        LogUtils.info(log, "HTTPMetricsServer shutdown.");
    }

    private static ScheduledExecutorService metricsSchedule = Executors.newScheduledThreadPool(2,
        new EventMeshThreadFactory("eventMesh-metrics", true));

    // todo: move this into standalone metrics plugin

    private void logPrintServerMetrics(final HttpSummaryMetrics summaryMetrics,
        final EventMeshHTTPServer eventMeshHTTPServer) {
        LogUtils.info(log, "===========================================SERVER METRICS==================================================");
        LogUtils.info(log, "maxHTTPTPS: {}, avgHTTPTPS: {}, maxHTTPCOST: {}, avgHTTPCOST: {}, avgHTTPBodyDecodeCost: {}, httpDiscard: {}",
            summaryMetrics.maxHTTPTPS(),
            summaryMetrics.avgHTTPTPS(),
            summaryMetrics.maxHTTPCost(),
            summaryMetrics.avgHTTPCost(),
            summaryMetrics.avgHTTPBodyDecodeCost(),
            summaryMetrics.getHttpDiscard());

        summaryMetrics.httpStatInfoClear();

        LogUtils.info(log, "maxBatchSendMsgTPS: {}, avgBatchSendMsgTPS: {}, sum: {}. sumFail: {}, sumFailRate: {}, discard : {}",
            summaryMetrics.maxSendBatchMsgTPS(),
            summaryMetrics.avgSendBatchMsgTPS(),
            summaryMetrics.getSendBatchMsgNumSum(),
            summaryMetrics.getSendBatchMsgFailNumSum(),
            summaryMetrics.getSendBatchMsgFailRate(),
            summaryMetrics.getSendBatchMsgDiscardNumSum());

        summaryMetrics.cleanSendBatchStat();

        LogUtils.info(log, "maxSendMsgTPS: {}, avgSendMsgTPS: {}, sum: {}, sumFail: {}, sumFailRate: {}, replyMsg: {}, replyFail: {}",
            summaryMetrics.maxSendMsgTPS(),
            summaryMetrics.avgSendMsgTPS(),
            summaryMetrics.getSendMsgNumSum(),
            summaryMetrics.getSendMsgFailNumSum(),
            summaryMetrics.getSendMsgFailRate(),
            summaryMetrics.getReplyMsgNumSum(),
            summaryMetrics.getReplyMsgFailNumSum());

        summaryMetrics.cleanSendMsgStat();

        LogUtils.info(log, "maxPushMsgTPS: {}, avgPushMsgTPS: {}, sum: {}, sumFail: {}, sumFailRate: {}, maxClientLatency: {}, avgClientLatency: {}",
            summaryMetrics.maxPushMsgTPS(),
            summaryMetrics.avgPushMsgTPS(),
            summaryMetrics.getHttpPushMsgNumSum(),
            summaryMetrics.getHttpPushFailNumSum(),
            summaryMetrics.getHttpPushMsgFailRate(),
            summaryMetrics.maxHTTPPushLatency(),
            summaryMetrics.avgHTTPPushLatency());

        summaryMetrics.cleanHttpPushMsgStat();

        LogUtils.info(log, "batchMsgQ: {}, sendMsgQ: {}, pushMsgQ: {}, httpRetryQ: {}",
            eventMeshHTTPServer.getHttpThreadPoolGroup().getBatchMsgExecutor().getQueue().size(),
            eventMeshHTTPServer.getHttpThreadPoolGroup().getSendMsgExecutor().getQueue().size(),
            eventMeshHTTPServer.getHttpThreadPoolGroup().getPushMsgExecutor().getQueue().size(),
            eventMeshHTTPServer.getHttpRetryer().getRetrySize());

        LogUtils.info(log, "batchAvgSend2MQCost: {}, avgSend2MQCost: {}, avgReply2MQCost: {}",
            summaryMetrics.avgBatchSendMsgCost(),
            summaryMetrics.avgSendMsgCost(),
            summaryMetrics.avgReplyMsgCost());
        summaryMetrics.send2MQStatInfoClear();
    }

    public HttpSummaryMetrics getSummaryMetrics() {
        return summaryMetrics;
    }
}
