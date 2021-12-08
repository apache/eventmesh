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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

public class TcpSummaryMetrics {

    public Logger logger = LoggerFactory.getLogger("httpMonitor");

    private EventMeshHTTPServer eventMeshHttpServer;

    public TcpSummaryMetrics(EventMeshHTTPServer eventMeshHttpServer) {
        this.eventMeshHttpServer = eventMeshHttpServer;
    }

    public static final int STATIC_PERIOD = 30 * 1000;

    private float avg(LinkedList<Integer> linkedList) {
        float sum = 0.0f;
        if (linkedList.isEmpty()) {
            return sum;
        }

        Iterator<Integer> it = linkedList.iterator();
        while (it.hasNext()) {
            float tps = (float) it.next();
            sum += tps;
        }

        return sum / linkedList.size();
    }

    ////////////////////////////////////////////////////////////////////////////////
    //EVENTMESH tps related to accepting external http requests
    public static final String EVENTMESH_MONITOR_FORMAT_HTTP = "%15s : {\"maxHTTPTPS\":\"%.1f\","
            + "\"avgHTTPTPS\":\"%.1f\",\"maxHTTPCOST\":\"%s\",\"avgHTTPCOST\":\"%.1f\","
            + "\"avgHTTPBodyDecodeCost\":\"%.1f\"}";

    private float wholeCost = 0f;

    private AtomicLong wholeRequestNum = new AtomicLong(0);

    private AtomicLong maxCost = new AtomicLong(0);

    private AtomicLong httpRequestPerSecond = new AtomicLong(0);

    private LinkedList<Integer> httpRequestTpsSnapshots = new LinkedList<Integer>();

    public float avgHttpCost() {
        float cost = (wholeRequestNum.longValue() == 0L) ? 0f
                : wholeCost / wholeRequestNum.longValue();
        return cost;
    }

    public long maxHttpCost() {
        long cost = maxCost.longValue();
        return cost;
    }

    public void recordHttpRequest() {
        httpRequestPerSecond.incrementAndGet();
    }

    public void snapshotHttpTps() {
        Integer tps = httpRequestPerSecond.intValue();
        httpRequestTpsSnapshots.add(tps);
        httpRequestPerSecond.set(0);
        if (httpRequestTpsSnapshots.size() > STATIC_PERIOD / 1000) {
            httpRequestTpsSnapshots.removeFirst();
        }
    }

    public float maxHttpTps() {
        float tps = Collections.max(httpRequestTpsSnapshots);
        return tps;
    }

    public float avgHttpTps() {
        float tps = avg(httpRequestTpsSnapshots);
        return tps;
    }

    public void recordHttpReqResTimeCost(long cost) {
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

    private float httpDecodeTimeCost = 0f;

    private AtomicLong httpDecodeNum = new AtomicLong(0);

    public void recordDecodeTimeCost(long cost) {
        httpDecodeNum.incrementAndGet();
        httpDecodeTimeCost = httpDecodeTimeCost + cost;
    }

    public float avgHttpBodyDecodeCost() {
        float cost = (httpDecodeNum.longValue() == 0l) ? 0f
                : httpDecodeTimeCost / httpDecodeNum.longValue();
        return cost;
    }

    //////////////////////////////////////////////////////////////////////////
    public static final String EVENTMESH_MONITOR_FORMAT_BATCHSENDMSG =
            "%15s : {\"maxBatchSendMsgTPS\":\"%.1f\", \"avgBatchSendMsgTPS\":\"%.1f\","
            + " \"sum\":\"%s\", \"sumFail\":\"%s\", \"sumFailRate\":\"%.2f\", \"discard\":\"%s\"}";

    private AtomicLong sendBatchMsgNumPerSecond = new AtomicLong(0);

    private AtomicLong sendBatchMsgNumSum = new AtomicLong(0);

    private AtomicLong sendBatchMsgFailNumSum = new AtomicLong(0);

    private AtomicLong sendBatchMsgDiscardNumSum = new AtomicLong(0);

    public void recordSendBatchMsgDiscard(long delta) {
        sendBatchMsgDiscardNumSum.addAndGet(delta);
    }

    private LinkedList<Integer> sendBatchMsgTpsSnapshots = new LinkedList<Integer>();

    public void snapshotSendBatchMsgTps() {
        Integer tps = sendBatchMsgNumPerSecond.intValue();
        sendBatchMsgTpsSnapshots.add(tps);
        sendBatchMsgNumPerSecond.set(0);
        if (sendBatchMsgTpsSnapshots.size() > STATIC_PERIOD / 1000) {
            sendBatchMsgTpsSnapshots.removeFirst();
        }
    }

    public float maxSendBatchMsgTps() {
        float tps = Collections.max(sendBatchMsgTpsSnapshots);
        return tps;
    }

    public float avgSendBatchMsgTps() {
        float tps = avg(sendBatchMsgTpsSnapshots);
        return tps;
    }

    public void recordSendBatchMsg(long delta) {
        sendBatchMsgNumPerSecond.addAndGet(delta);
        sendBatchMsgNumSum.addAndGet(delta);
    }

    public void recordSendBatchMsgFailed(long delta) {
        sendBatchMsgFailNumSum.getAndAdd(delta);
    }

    public long getSendBatchMsgNumSum() {
        return sendBatchMsgNumSum.longValue();
    }

    public long getSendBatchMsgFailNumSum() {
        return sendBatchMsgFailNumSum.longValue();
    }

    public float getSendBatchMsgFailRate() {
        return (sendBatchMsgNumSum.longValue() == 0L) ? 0f
                : sendBatchMsgFailNumSum.floatValue() / sendBatchMsgNumSum.longValue();
    }

    public void cleanSendBatchStat() {
        sendBatchMsgNumSum.set(0L);
        sendBatchMsgFailNumSum.set(0L);
    }

    public long getSendBatchMsgDiscardNumSum() {
        return sendBatchMsgDiscardNumSum.longValue();
    }

    //////////////////////////////////////////////////////////////////////////
    public static final String EVENTMESH_MONITOR_FORMAT_SENDMSG =
            "%15s : {\"maxSendMsgTPS\":\"%.1f\","
            + "\"avgSendMsgTPS\":\"%.1f\", \"sum\":\"%s\", \"sumFail\":\"%s\","
            + " \"sumFailRate\":\"%.2f\", \"discard\":\"%s\"}";

    private AtomicLong sendMsgNumSum = new AtomicLong(0);

    private AtomicLong sendMsgFailNumSum = new AtomicLong(0);

    private AtomicLong sendMsgDiscardNumSum = new AtomicLong(0);

    private AtomicLong sendMsgNumPerSecond = new AtomicLong(0);

    private LinkedList<Integer> sendMsgTpsSnapshots = new LinkedList<Integer>();

    public void snapshotSendMsgTps() {
        Integer tps = sendMsgNumPerSecond.intValue();
        sendMsgTpsSnapshots.add(tps);
        sendMsgNumPerSecond.set(0);
        if (sendMsgTpsSnapshots.size() > STATIC_PERIOD / 1000) {
            sendMsgTpsSnapshots.removeFirst();
        }
    }

    public float maxSendMsgTps() {
        float tps = Collections.max(sendMsgTpsSnapshots);
        return tps;
    }

    public float avgSendMsgTps() {
        float tps = avg(sendMsgTpsSnapshots);
        return tps;
    }

    public void recordSendMsg() {
        sendMsgNumPerSecond.incrementAndGet();
        sendMsgNumSum.incrementAndGet();
    }

    public long getSendMsgNumSum() {
        return sendMsgNumSum.longValue();
    }

    public long getSendMsgFailNumSum() {
        return sendMsgFailNumSum.longValue();
    }

    public float getSendMsgFailRate() {
        return (sendMsgNumSum.longValue() == 0L) ? 0f
                : sendMsgFailNumSum.floatValue() / sendMsgNumSum.longValue();
    }

    public void recordSendMsgFailed() {
        sendMsgFailNumSum.incrementAndGet();
    }

    public void recordSendMsgDiscard() {
        sendMsgDiscardNumSum.incrementAndGet();
    }

    public void cleanSendMsgStat() {
        sendMsgNumSum.set(0L);
        sendMsgFailNumSum.set(0L);
    }

    public long getSendMsgDiscardNumSum() {
        return sendMsgDiscardNumSum.longValue();
    }

    ////////////////////////////////////////////////////////////////////////////
    public static final String EVENTMESH_MONITOR_FORMAT_PUSHMSG
            = "%15s : {\"maxPushMsgTPS\":\"%.1f\", \"avgPushMsgTPS\":\"%.1f\", \"sum\":\"%s\","
            + " \"sumFail\":\"%s\", \"sumFailRate\":\"%.1f\","
            + " \"maxClientLatency\":\"%.1f\", \"avgClientLatency\":\"%.1f\"}";

    private float wholePushCost = 0f;

    private AtomicLong wholePushRequestNum = new AtomicLong(0);

    private AtomicLong maxHttpPushLatency = new AtomicLong(0);

    private AtomicLong pushMsgNumPerSecond = new AtomicLong(0);

    private LinkedList<Integer> pushMsgTpsSnapshots = new LinkedList<Integer>();

    private AtomicLong httpPushMsgNumSum = new AtomicLong(0);

    private AtomicLong httpPushFailNumSum = new AtomicLong(0);

    public void snapshotPushMsgTps() {
        Integer tps = pushMsgNumPerSecond.intValue();
        pushMsgTpsSnapshots.add(tps);
        pushMsgNumPerSecond.set(0);
        if (pushMsgTpsSnapshots.size() > STATIC_PERIOD / 1000) {
            pushMsgTpsSnapshots.removeFirst();
        }
    }

    public void recordHttpPushTimeCost(long cost) {
        wholePushRequestNum.incrementAndGet();
        wholePushCost = wholePushCost + cost;
        if (cost > maxHttpPushLatency.longValue()) {
            maxHttpPushLatency.set(cost);
        }
    }

    public float avgHttpPushLatency() {
        return (wholePushRequestNum.longValue() == 0L) ? 0f
                : wholePushCost / wholePushRequestNum.longValue();
    }

    public float maxHttpPushLatency() {
        return maxHttpPushLatency.floatValue();
    }

    public float maxPushMsgTps() {
        float tps = Collections.max(pushMsgTpsSnapshots);
        return tps;
    }

    public float avgPushMsgTps() {
        float tps = avg(pushMsgTpsSnapshots);
        return tps;
    }

    public void recordPushMsg() {
        pushMsgNumPerSecond.incrementAndGet();
        httpPushMsgNumSum.incrementAndGet();
    }

    public long getHttpPushMsgNumSum() {
        return httpPushMsgNumSum.longValue();
    }

    public long getHttpPushFailNumSum() {
        return httpPushFailNumSum.longValue();
    }

    public float getHttpPushMsgFailRate() {
        return (httpPushMsgNumSum.longValue() == 0L) ? 0f
                : httpPushFailNumSum.floatValue() / httpPushMsgNumSum.longValue();
    }

    public void recordHttpPushMsgFailed() {
        sendMsgFailNumSum.incrementAndGet();
    }

    public void cleanHttpPushMsgStat() {
        httpPushFailNumSum.set(0L);
        httpPushMsgNumSum.set(0L);
        wholeRequestNum.set(0L);
        wholeCost = 0f;
        maxCost.set(0L);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    public static final String EVENTMESH_MONITOR_FORMAT_BLOCKQ =
            "%15s : {\"batchMsgQ\":\"%s\",\"sendMsgQ\":\"%s\","
            + "\"pushMsgQ\":\"%s\",\"consumeRetryQ\":\"%s\"}";

    ///////////////////////////////////////////////////////////////////////////
    public static final String EVENTMESH_MONITOR_FORMAT_MQ_CLIENT =
            "%15s : {\"batchAvgSend2MQCost\":\"%.1f\", \"avgSend2MQCost\":\"%.1f\"}";

    private float batchSend2MqWholeCost = 0f;

    private AtomicLong batchSend2MqNum = new AtomicLong(0);

    private float send2MqWholeCost = 0f;

    private AtomicLong send2MqNum = new AtomicLong(0);

    public void recordBatchSendMsgCost(long cost) {
        batchSend2MqNum.incrementAndGet();
        batchSend2MqWholeCost = batchSend2MqWholeCost + cost;
    }

    public float avgBatchSendMsgCost() {
        float cost = (batchSend2MqNum.intValue() == 0) ? 0f
                : batchSend2MqWholeCost / batchSend2MqNum.intValue();
        return cost;
    }

    public void recordSendMsgCost(long cost) {
        send2MqNum.incrementAndGet();
        send2MqWholeCost = send2MqWholeCost + cost;
    }

    public float avgSendMsgCost() {
        float cost = (send2MqNum.intValue() == 0) ? 0f : send2MqWholeCost / send2MqNum.intValue();
        return cost;
    }

    public void send2MqStatInfoClear() {
        batchSend2MqWholeCost = 0f;
        batchSend2MqNum.set(0L);
        send2MqWholeCost = 0f;
        send2MqNum.set(0L);
    }
}
