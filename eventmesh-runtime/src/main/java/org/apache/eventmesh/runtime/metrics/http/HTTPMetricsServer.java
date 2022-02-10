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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPMetricsServer {

    private EventMeshHTTPServer eventMeshHTTPServer;

    private Logger httpLogger = LoggerFactory.getLogger("httpMonitor");

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private List<MetricsRegistry> metricsRegistries;

    private final HttpSummaryMetrics summaryMetrics;

    public HTTPMetricsServer(EventMeshHTTPServer eventMeshHTTPServer, List<MetricsRegistry> metricsRegistries) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.metricsRegistries = metricsRegistries;
        this.summaryMetrics = new HttpSummaryMetrics(
            eventMeshHTTPServer.batchMsgExecutor,
            eventMeshHTTPServer.sendMsgExecutor,
            eventMeshHTTPServer.pushMsgExecutor,
            eventMeshHTTPServer.getHttpRetryer().getFailedQueue());
    }

    public void init() throws Exception {
        metricsRegistries.forEach(MetricsRegistry::start);
        logger.info("HTTPMetricsServer initialized......");
    }

    public void start() throws Exception {
        metricsRegistries.forEach(metricsRegistry -> {
            metricsRegistry.register(summaryMetrics);
            logger.info("Register httpMetrics to " + metricsRegistry.getClass().getName());
        });
        metricsSchedule.scheduleAtFixedRate(() -> {
            try {
                summaryMetrics.snapshotHTTPTPS();
                summaryMetrics.snapshotSendBatchMsgTPS();
                summaryMetrics.snapshotSendMsgTPS();
                summaryMetrics.snapshotPushMsgTPS();
            } catch (Exception ex) {
                logger.warn("eventMesh snapshot tps metrics err", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        metricsSchedule.scheduleAtFixedRate(() -> {
            try {
                logPrintServerMetrics();
            } catch (Exception ex) {
                logger.warn("eventMesh print metrics err", ex);
            }
        }, 1000, 30 * 1000, TimeUnit.MILLISECONDS);
    
        logger.info("HTTPMetricsServer started......");
    }

    public void shutdown() throws Exception {
        metricsSchedule.shutdown();
        metricsRegistries.forEach(MetricsRegistry::showdown);
        logger.info("HTTPMetricsServer shutdown......");
    }

    protected static ScheduledExecutorService metricsSchedule = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        private AtomicInteger seq = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            seq.incrementAndGet();
            Thread t = new Thread(r, "eventMesh-metrics-" + seq.get());
            t.setDaemon(true);
            return t;
        }
    });

    // todo: move this into standalone metrics plugin
    private void logPrintServerMetrics() {
        httpLogger.info("===========================================SERVER METRICS==================================================");

        httpLogger.info(String.format(HttpSummaryMetrics.EVENTMESH_MONITOR_FORMAT_HTTP,
            summaryMetrics.maxHTTPTPS(),
            summaryMetrics.avgHTTPTPS(),
            summaryMetrics.maxHTTPCost(),
            summaryMetrics.avgHTTPCost(),
            summaryMetrics.avgHTTPBodyDecodeCost(),
            summaryMetrics.getHttpDiscard()));
        summaryMetrics.httpStatInfoClear();

        httpLogger.info(String.format(HttpSummaryMetrics.EVENTMESH_MONITOR_FORMAT_BATCHSENDMSG,
            summaryMetrics.maxSendBatchMsgTPS(),
            summaryMetrics.avgSendBatchMsgTPS(),
            summaryMetrics.getSendBatchMsgNumSum(),
            summaryMetrics.getSendBatchMsgFailNumSum(),
            summaryMetrics.getSendBatchMsgFailRate(),
            summaryMetrics.getSendBatchMsgDiscardNumSum()
        ));
        summaryMetrics.cleanSendBatchStat();

        httpLogger.info(String.format(HttpSummaryMetrics.EVENTMESH_MONITOR_FORMAT_SENDMSG,
            summaryMetrics.maxSendMsgTPS(),
            summaryMetrics.avgSendMsgTPS(),
            summaryMetrics.getSendMsgNumSum(),
            summaryMetrics.getSendMsgFailNumSum(),
            summaryMetrics.getSendMsgFailRate(),
            summaryMetrics.getReplyMsgNumSum(),
            summaryMetrics.getReplyMsgFailNumSum()
        ));
        summaryMetrics.cleanSendMsgStat();

        httpLogger.info(String.format(HttpSummaryMetrics.EVENTMESH_MONITOR_FORMAT_PUSHMSG,
            summaryMetrics.maxPushMsgTPS(),
            summaryMetrics.avgPushMsgTPS(),
            summaryMetrics.getHttpPushMsgNumSum(),
            summaryMetrics.getHttpPushFailNumSum(),
            summaryMetrics.getHttpPushMsgFailRate(),
            summaryMetrics.maxHTTPPushLatency(),
            summaryMetrics.avgHTTPPushLatency()
        ));
        summaryMetrics.cleanHttpPushMsgStat();

        httpLogger.info(String.format(HttpSummaryMetrics.EVENTMESH_MONITOR_FORMAT_BLOCKQ,
            eventMeshHTTPServer.getBatchMsgExecutor().getQueue().size(),
            eventMeshHTTPServer.getSendMsgExecutor().getQueue().size(),
            eventMeshHTTPServer.getPushMsgExecutor().getQueue().size(),
            eventMeshHTTPServer.getHttpRetryer().size()));

        httpLogger.info(String.format(HttpSummaryMetrics.EVENTMESH_MONITOR_FORMAT_MQ_CLIENT,
            summaryMetrics.avgBatchSendMsgCost(),
            summaryMetrics.avgSendMsgCost(),
            summaryMetrics.avgReplyMsgCost()));
        summaryMetrics.send2MQStatInfoClear();
    }

    public HttpSummaryMetrics getSummaryMetrics() {
        return summaryMetrics;
    }
}
