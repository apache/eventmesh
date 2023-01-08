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

import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPMetricsServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HTTPMetricsServer.class);

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
                eventMeshHTTPServer.batchMsgExecutor,
                eventMeshHTTPServer.sendMsgExecutor,
                eventMeshHTTPServer.pushMsgExecutor,
                eventMeshHTTPServer.getHttpRetryer().getFailedQueue());

        init();
    }

    private void init() {
        metricsRegistries.forEach(MetricsRegistry::start);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("HTTPMetricsServer initialized.");
        }
    }

    public void start() {
        metricsRegistries.forEach(metricsRegistry -> {
            metricsRegistry.register(summaryMetrics);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Register httpMetrics to {}", metricsRegistry.getClass().getName());
            }
        });

        metricsSchedule.scheduleAtFixedRate(() -> {
            try {
                summaryMetrics.snapshotHTTPTPS();
                summaryMetrics.snapshotSendBatchMsgTPS();
                summaryMetrics.snapshotSendMsgTPS();
                summaryMetrics.snapshotPushMsgTPS();
            } catch (Exception ex) {
                LOGGER.error("eventMesh snapshot tps metrics err", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        metricsSchedule.scheduleAtFixedRate(() -> {
            try {
                logPrintServerMetrics(summaryMetrics, eventMeshHTTPServer);
            } catch (Exception ex) {
                LOGGER.error("eventMesh print metrics err", ex);
            }
        }, 1000, 30 * 1000, TimeUnit.MILLISECONDS);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("HTTPMetricsServer started.");
        }
    }

    public void shutdown() {
        metricsSchedule.shutdown();
        metricsRegistries.forEach(MetricsRegistry::showdown);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("HTTPMetricsServer shutdown.");
        }
    }

    private static ScheduledExecutorService metricsSchedule = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        private final transient AtomicInteger seq = new AtomicInteger(0);

        @Override
        public Thread newThread(final Runnable r) {
            seq.incrementAndGet();
            final Thread t = new Thread(r, "eventMesh-metrics-" + seq.get());
            t.setDaemon(true);
            return t;
        }
    });

    // todo: move this into standalone metrics plugin

    private void logPrintServerMetrics(final HttpSummaryMetrics summaryMetrics,
                                       final EventMeshHTTPServer eventMeshHTTPServer) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("===========================================SERVER METRICS==================================================");

            LOGGER.info("maxHTTPTPS: {}, avgHTTPTPS: {}, maxHTTPCOST: {}, avgHTTPCOST: {}, avgHTTPBodyDecodeCost: {}, httpDiscard: {}",
                    summaryMetrics.maxHTTPTPS(),
                    summaryMetrics.avgHTTPTPS(),
                    summaryMetrics.maxHTTPCost(),
                    summaryMetrics.avgHTTPCost(),
                    summaryMetrics.avgHTTPBodyDecodeCost(),
                    summaryMetrics.getHttpDiscard());
        }

        summaryMetrics.httpStatInfoClear();


        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("maxBatchSendMsgTPS: {}, avgBatchSendMsgTPS: {}, sum: {}. sumFail: {}, sumFailRate: {}, discard : {}",
                    summaryMetrics.maxSendBatchMsgTPS(),
                    summaryMetrics.avgSendBatchMsgTPS(),
                    summaryMetrics.getSendBatchMsgNumSum(),
                    summaryMetrics.getSendBatchMsgFailNumSum(),
                    summaryMetrics.getSendBatchMsgFailRate(),
                    summaryMetrics.getSendBatchMsgDiscardNumSum()
            );
        }

        summaryMetrics.cleanSendBatchStat();


        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("maxSendMsgTPS: {}, avgSendMsgTPS: {}, sum: {}, sumFail: {}, sumFailRate: {}, replyMsg: {}, replyFail: {}",
                    summaryMetrics.maxSendMsgTPS(),
                    summaryMetrics.avgSendMsgTPS(),
                    summaryMetrics.getSendMsgNumSum(),
                    summaryMetrics.getSendMsgFailNumSum(),
                    summaryMetrics.getSendMsgFailRate(),
                    summaryMetrics.getReplyMsgNumSum(),
                    summaryMetrics.getReplyMsgFailNumSum()
            );
        }

        summaryMetrics.cleanSendMsgStat();


        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "maxPushMsgTPS: {}, avgPushMsgTPS: {}, sum: {}, sumFail: {}, sumFailRate: {}, maxClientLatency: {}, avgClientLatency: {}",
                    summaryMetrics.maxPushMsgTPS(),
                    summaryMetrics.avgPushMsgTPS(),
                    summaryMetrics.getHttpPushMsgNumSum(),
                    summaryMetrics.getHttpPushFailNumSum(),
                    summaryMetrics.getHttpPushMsgFailRate(),
                    summaryMetrics.maxHTTPPushLatency(),
                    summaryMetrics.avgHTTPPushLatency()
            );
        }

        summaryMetrics.cleanHttpPushMsgStat();


        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("batchMsgQ: {}, sendMsgQ: {}, pushMsgQ: {}, httpRetryQ: {}",
                    eventMeshHTTPServer.getBatchMsgExecutor().getQueue().size(),
                    eventMeshHTTPServer.getSendMsgExecutor().getQueue().size(),
                    eventMeshHTTPServer.getPushMsgExecutor().getQueue().size(),
                    eventMeshHTTPServer.getHttpRetryer().size());
        }


        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("batchAvgSend2MQCost: {}, avgSend2MQCost: {}, avgReply2MQCost: {}",
                    summaryMetrics.avgBatchSendMsgCost(),
                    summaryMetrics.avgSendMsgCost(),
                    summaryMetrics.avgReplyMsgCost());
        }
        summaryMetrics.send2MQStatInfoClear();
    }

    public HttpSummaryMetrics getSummaryMetrics() {
        return summaryMetrics;
    }
}
