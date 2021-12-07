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

import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.metrics.opentelemetry.OpenTelemetryHttpMetricsExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpMetricsServer {

    private EventMeshHTTPServer eventMeshHttpServer;

    public SummaryMetrics summaryMetrics;

    public HealthMetrics healthMetrics;

    public TopicMetrics topicMetrics;

    public GroupMetrics groupMetrics;

    public OpenTelemetryHttpMetricsExporter openTelemetryHttpMetricsExporter;

    private Logger httpLogger = LoggerFactory.getLogger("httpMonitor");

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpMetricsServer(EventMeshHTTPServer eventMeshHttpServer) {
        this.eventMeshHttpServer = eventMeshHttpServer;
    }

    public void init() throws Exception {
        summaryMetrics = new SummaryMetrics(this.eventMeshHttpServer);
        topicMetrics = new TopicMetrics(this.eventMeshHttpServer);
        groupMetrics = new GroupMetrics(this.eventMeshHttpServer);
        healthMetrics = new HealthMetrics(this.eventMeshHttpServer);

        openTelemetryHttpMetricsExporter = new OpenTelemetryHttpMetricsExporter(this,
                this.eventMeshHttpServer.getEventMeshHttpConfiguration());

        logger.info("HTTPMetricsServer inited......");
    }

    public void start() throws Exception {
        openTelemetryHttpMetricsExporter.start();
        metricsSchedule.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    summaryMetrics.snapshotHttpTps();
                    summaryMetrics.snapshotSendBatchMsgTps();
                    summaryMetrics.snapshotSendMsgTps();
                    summaryMetrics.snapshotPushMsgTps();
                } catch (Exception ex) {
                    logger.warn("eventMesh snapshot tps metrics err", ex);
                }
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        metricsSchedule.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    logPrintServerMetrics();
                } catch (Exception ex) {
                    logger.warn("eventMesh print metrics err", ex);
                }
            }
        }, 1000, SummaryMetrics.STATIC_PERIOD, TimeUnit.MILLISECONDS);

        logger.info("HTTPMetricsServer started......");
    }

    public void shutdown() throws Exception {
        metricsSchedule.shutdown();
        openTelemetryHttpMetricsExporter.shutdown();
        logger.info("HTTPMetricsServer shutdown......");
    }

    protected static ScheduledExecutorService metricsSchedule = Executors.newScheduledThreadPool(
            2, new ThreadFactory() {
                private AtomicInteger seq = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        seq.incrementAndGet();
                        Thread t = new Thread(r, "eventMesh-metrics-" + seq.get());
                        t.setDaemon(true);
                        return t;
                    }
            }
    );

    private void logPrintServerMetrics() {
        httpLogger.info("=====================SERVER METRICS==============================");

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_HTTP,
                summaryMetrics.maxHttpTps(),
                summaryMetrics.avgHttpTps(),
                summaryMetrics.maxHttpCost(),
                summaryMetrics.avgHttpCost(),
                summaryMetrics.avgHttpBodyDecodeCost(),
                summaryMetrics.getHttpDiscard()));
        summaryMetrics.httpStatInfoClear();

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_BATCHSENDMSG,
                summaryMetrics.maxSendBatchMsgTps(),
                summaryMetrics.avgSendBatchMsgTps(),
                summaryMetrics.getSendBatchMsgNumSum(),
                summaryMetrics.getSendBatchMsgFailNumSum(),
                summaryMetrics.getSendBatchMsgFailRate(),
                summaryMetrics.getSendBatchMsgDiscardNumSum()
        ));
        summaryMetrics.cleanSendBatchStat();

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_SENDMSG,
                summaryMetrics.maxSendMsgTps(),
                summaryMetrics.avgSendMsgTps(),
                summaryMetrics.getSendMsgNumSum(),
                summaryMetrics.getSendMsgFailNumSum(),
                summaryMetrics.getSendMsgFailRate(),
                summaryMetrics.getReplyMsgNumSum(),
                summaryMetrics.getReplyMsgFailNumSum()
        ));
        summaryMetrics.cleanSendMsgStat();

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_PUSHMSG,
                summaryMetrics.maxPushMsgTps(),
                summaryMetrics.avgPushMsgTps(),
                summaryMetrics.getHttpPushMsgNumSum(),
                summaryMetrics.getHttpPushFailNumSum(),
                summaryMetrics.getHttpPushMsgFailRate(),
                summaryMetrics.maxHttpPushLatency(),
                summaryMetrics.avgHttpPushLatency()
        ));
        summaryMetrics.cleanHttpPushMsgStat();

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_BLOCKQ,
                eventMeshHttpServer.getBatchMsgExecutor().getQueue().size(),
                eventMeshHttpServer.getSendMsgExecutor().getQueue().size(),
                eventMeshHttpServer.getPushMsgExecutor().getQueue().size(),
                eventMeshHttpServer.getHttpRetryer().size()));

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_MQ_CLIENT,
                summaryMetrics.avgBatchSendMsgCost(),
                summaryMetrics.avgSendMsgCost(),
                summaryMetrics.avgReplyMsgCost()));
        summaryMetrics.send2MqStatInfoClear();
    }

    public int getBatchMsgQ() {
        return eventMeshHttpServer.getBatchMsgExecutor().getQueue().size();
    }

    public int getSendMsgQ() {
        return eventMeshHttpServer.getSendMsgExecutor().getQueue().size();
    }

    public int getPushMsgQ() {
        return eventMeshHttpServer.getPushMsgExecutor().getQueue().size();
    }

    public int getHttpRetryQ() {
        return eventMeshHttpServer.getHttpRetryer().size();
    }

    public HealthMetrics getHealthMetrics() {
        return healthMetrics;
    }

    public TopicMetrics getTopicMetrics() {
        return topicMetrics;
    }

    public GroupMetrics getGroupMetrics() {
        return groupMetrics;
    }
}
