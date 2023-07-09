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
import org.apache.eventmesh.common.MetricsConstants;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.metrics.MetricsManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshHttpMetricsManager implements MetricsManager {

    private  Map<String, String> labelMap = new HashMap<>();

    private final  EventMeshHTTPServer eventMeshHTTPServer;

    private final  List<MetricsRegistry> metricsRegistries;

    private final  HttpMetrics httpMetrics;

    public EventMeshHttpMetricsManager(final EventMeshHTTPServer eventMeshHTTPServer,
        final List<MetricsRegistry> metricsRegistries) {
        Objects.requireNonNull(eventMeshHTTPServer, "EventMeshHTTPServer can not be null");
        Objects.requireNonNull(metricsRegistries, "List<MetricsRegistry> can not be null");

        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.metricsRegistries = metricsRegistries;
        init();
        this.httpMetrics = new HttpMetrics(
            eventMeshHTTPServer.getBatchMsgExecutor(),
            eventMeshHTTPServer.getSendMsgExecutor(),
            eventMeshHTTPServer.getPushMsgExecutor(),
            eventMeshHTTPServer.getHttpRetryer().getFailedQueue(),
            labelMap);
    }

    private void init() {
        boolean useTls = eventMeshHTTPServer.getEventMeshHttpConfiguration().isEventMeshServerUseTls();
        String eventMeshServerIp = this.eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshServerIp();
        int httpServerPort = this.eventMeshHTTPServer.getEventMeshHttpConfiguration().getHttpServerPort();
        this.labelMap.put(MetricsConstants.HTTP_HTTP_SCHEME, useTls ? "https" : "http");
        this.labelMap.put(MetricsConstants.HTTP_HTTP_FLAVOR, "1.1");
        this.labelMap.put(MetricsConstants.HTTP_NET_HOST_NAME, Optional.ofNullable(eventMeshServerIp).orElse(IPUtils.getLocalAddress()));
        this.labelMap.put(MetricsConstants.HTTP_NET_HOST_PORT, Integer.toString(httpServerPort));
        this.labelMap.put(MetricsConstants.RPC_SYSTEM, "HTTP");
        this.labelMap.put(MetricsConstants.RPC_SERVICE, this.eventMeshHTTPServer.getClass().getName());
        if (log.isInfoEnabled()) {
            log.info("HTTPMetricsServer initialized.");
        }
    }

    @Override
    public void start() {

        metricsSchedule.scheduleAtFixedRate(() -> {
            try {
                httpMetrics.snapshotHTTPTPS();
                httpMetrics.snapshotSendBatchMsgTPS();
                httpMetrics.snapshotSendMsgTPS();
                httpMetrics.snapshotPushMsgTPS();
            } catch (Exception ex) {
                log.error("eventMesh snapshot tps metrics err", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        metricsSchedule.scheduleAtFixedRate(() -> {
            try {
                logPrintServerMetrics(httpMetrics, eventMeshHTTPServer);
            } catch (Exception ex) {
                log.error("eventMesh print metrics err", ex);
            }
        }, 1000, 30 * 1000, TimeUnit.MILLISECONDS);

        if (log.isInfoEnabled()) {
            log.info("HTTPMetricsServer started.");
        }
    }

    @Override
    public void shutdown() {
        metricsSchedule.shutdown();
    }

    private static ScheduledExecutorService metricsSchedule = Executors.newScheduledThreadPool(2,
        new EventMeshThreadFactory("eventMesh-metrics", true));

    // todo: move this into standalone metrics plugin

    private void logPrintServerMetrics(final HttpMetrics summaryMetrics,
        final EventMeshHTTPServer eventMeshHTTPServer) {
        if (log.isInfoEnabled()) {
            log.info("===========================================HTTP SERVER METRICS==================================================");

            log.info("maxHTTPTPS: {}, avgHTTPTPS: {}, maxHTTPCOST: {}, avgHTTPCOST: {}, avgHTTPBodyDecodeCost: {}, httpDiscard: {}",
                summaryMetrics.maxHTTPTPS(),
                summaryMetrics.avgHTTPTPS(),
                summaryMetrics.maxHTTPCost(),
                summaryMetrics.avgHTTPCost(),
                summaryMetrics.avgHTTPBodyDecodeCost(),
                summaryMetrics.getHttpDiscard());
        }

        summaryMetrics.httpStatInfoClear();

        if (log.isInfoEnabled()) {
            log.info("maxBatchSendMsgTPS: {}, avgBatchSendMsgTPS: {}, sum: {}. sumFail: {}, sumFailRate: {}, discard : {}",
                summaryMetrics.maxSendBatchMsgTPS(),
                summaryMetrics.avgSendBatchMsgTPS(),
                summaryMetrics.getSendBatchMsgNumSum(),
                summaryMetrics.getSendBatchMsgFailNumSum(),
                summaryMetrics.getSendBatchMsgFailRate(),
                summaryMetrics.getSendBatchMsgDiscardNumSum()
            );
        }

        summaryMetrics.cleanSendBatchStat();

        if (log.isInfoEnabled()) {
            log.info("maxSendMsgTPS: {}, avgSendMsgTPS: {}, sum: {}, sumFail: {}, sumFailRate: {}, replyMsg: {}, replyFail: {}",
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

        if (log.isInfoEnabled()) {
            log.info(
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

        if (log.isInfoEnabled()) {
            log.info("batchMsgQ: {}, sendMsgQ: {}, pushMsgQ: {}, httpRetryQ: {}",
                eventMeshHTTPServer.getBatchMsgExecutor().getQueue().size(),
                eventMeshHTTPServer.getSendMsgExecutor().getQueue().size(),
                eventMeshHTTPServer.getPushMsgExecutor().getQueue().size(),
                eventMeshHTTPServer.getHttpRetryer().size());
        }

        if (log.isInfoEnabled()) {
            log.info("batchAvgSend2MQCost: {}, avgSend2MQCost: {}, avgReply2MQCost: {}",
                summaryMetrics.avgBatchSendMsgCost(),
                summaryMetrics.avgSendMsgCost(),
                summaryMetrics.avgReplyMsgCost());
        }
        summaryMetrics.send2MQStatInfoClear();
    }

    public HttpMetrics getHttpMetrics() {
        return httpMetrics;
    }

    @Override
    public List<Metric> getMetrics() {
        return new ArrayList<>(httpMetrics.getMetrics());
    }

    @Override
    public String getMetricManagerName() {
        return this.getClass().getName();
    }
}
