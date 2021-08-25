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

package org.apache.eventmeth.protocol.http.metrics;

import com.codahale.metrics.MetricRegistry;
import org.apache.eventmeth.protocol.http.EventMeshProtocolHTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HTTPMetricsServer {

    private EventMeshProtocolHTTPServer eventMeshHTTPServer;

    private MetricRegistry metricRegistry = new MetricRegistry();

    public SummaryMetrics summaryMetrics;

    public HealthMetrics healthMetrics;

    public TopicMetrics topicMetrics;

    public GroupMetrics groupMetrics;

    public OpenTelemetryHTTPMetricsExporter openTelemetryHTTPMetricsExporter;

    private Logger httpLogger = LoggerFactory.getLogger("httpMonitor");

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public HTTPMetricsServer(EventMeshProtocolHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    public void init() throws Exception {
        summaryMetrics = new SummaryMetrics(this.eventMeshHTTPServer, this.metricRegistry);
        topicMetrics = new TopicMetrics(this.eventMeshHTTPServer, this.metricRegistry);
        groupMetrics = new GroupMetrics(this.eventMeshHTTPServer, this.metricRegistry);
        healthMetrics = new HealthMetrics(this.eventMeshHTTPServer, this.metricRegistry);
        openTelemetryHTTPMetricsExporter = new OpenTelemetryHTTPMetricsExporter(this );
        logger.info("HTTPMetricsServer inited......");
    }

    public void start() throws Exception {
        metricsSchedule.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    summaryMetrics.snapshotHTTPTPS();
                    summaryMetrics.snapshotSendBatchMsgTPS();
                    summaryMetrics.snapshotSendMsgTPS();
                    summaryMetrics.snapshotPushMsgTPS();
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
        openTelemetryHTTPMetricsExporter.shutdown();
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

    private void logPrintServerMetrics() {
        httpLogger.info("===========================================SERVER METRICS==================================================");

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_HTTP,
                summaryMetrics.maxHTTPTPS(),
                summaryMetrics.avgHTTPTPS(),
                summaryMetrics.maxHTTPCost(),
                summaryMetrics.avgHTTPCost(),
                summaryMetrics.avgHTTPBodyDecodeCost(),
                summaryMetrics.getHttpDiscard()));
        summaryMetrics.httpStatInfoClear();

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_BATCHSENDMSG,
                summaryMetrics.maxSendBatchMsgTPS(),
                summaryMetrics.avgSendBatchMsgTPS(),
                summaryMetrics.getSendBatchMsgNumSum(),
                summaryMetrics.getSendBatchMsgFailNumSum(),
                summaryMetrics.getSendBatchMsgFailRate(),
                summaryMetrics.getSendBatchMsgDiscardNumSum()
        ));
        summaryMetrics.cleanSendBatchStat();

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_SENDMSG,
                summaryMetrics.maxSendMsgTPS(),
                summaryMetrics.avgSendMsgTPS(),
                summaryMetrics.getSendMsgNumSum(),
                summaryMetrics.getSendMsgFailNumSum(),
                summaryMetrics.getSendMsgFailRate(),
                summaryMetrics.getReplyMsgNumSum(),
                summaryMetrics.getReplyMsgFailNumSum()
        ));
        summaryMetrics.cleanSendMsgStat();

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_PUSHMSG,
                summaryMetrics.maxPushMsgTPS(),
                summaryMetrics.avgPushMsgTPS(),
                summaryMetrics.getHttpPushMsgNumSum(),
                summaryMetrics.getHttpPushFailNumSum(),
                summaryMetrics.getHttpPushMsgFailRate(),
                summaryMetrics.maxHTTPPushLatency(),
                summaryMetrics.avgHTTPPushLatency()
        ));
        summaryMetrics.cleanHttpPushMsgStat();

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_BLOCKQ,
                eventMeshHTTPServer.getBatchMsgExecutor().getQueue().size(),
                eventMeshHTTPServer.getSendMsgExecutor().getQueue().size(),
                eventMeshHTTPServer.getPushMsgExecutor().getQueue().size(),
                eventMeshHTTPServer.getHttpRetryer().size()));

        httpLogger.info(String.format(SummaryMetrics.EVENTMESH_MONITOR_FORMAT_MQ_CLIENT,
                summaryMetrics.avgBatchSendMsgCost(),
                summaryMetrics.avgSendMsgCost(),
                summaryMetrics.avgReplyMsgCost()));
        summaryMetrics.send2MQStatInfoClear();
    }

    public int getBatchMsgQ(){
        return eventMeshHTTPServer.getBatchMsgExecutor().getQueue().size();
    }

    public int getSendMsgQ(){
        return eventMeshHTTPServer.getSendMsgExecutor().getQueue().size();
    }

    public int getPushMsgQ(){
        return eventMeshHTTPServer.getPushMsgExecutor().getQueue().size();
    }

    public int getHttpRetryQ(){
        return eventMeshHTTPServer.getHttpRetryer().size();
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
