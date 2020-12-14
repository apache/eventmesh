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

package com.webank.runtime.metrics.http;

import com.webank.runtime.boot.ProxyHTTPServer;
import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

public class SummaryMetrics {

    public Logger logger = LoggerFactory.getLogger("httpMonitor");

    private ProxyHTTPServer proxyHTTPServer;

    private MetricRegistry metricRegistry;

    public SummaryMetrics(ProxyHTTPServer proxyHTTPServer, MetricRegistry metricRegistry) {
        this.proxyHTTPServer = proxyHTTPServer;
        this.metricRegistry = metricRegistry;
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
    public static final String PROXY_MONITOR_FORMAT_HTTP = "{\"maxHTTPTPS\":\"%.1f\",\"avgHTTPTPS\":\"%.1f\"," +  //PROXY 接受外部HTTP 请求的TPS相关
            "\"maxHTTPCOST\":\"%s\",\"avgHTTPCOST\":\"%.1f\",\"avgHTTPBodyDecodeCost\":\"%.1f\", \"httpDiscard\":\"%s\"}";

    private float wholeCost = 0f;

    private AtomicLong wholeRequestNum = new AtomicLong(0);

    //累计值
    private AtomicLong httpDiscard = new AtomicLong(0);

    private AtomicLong maxCost = new AtomicLong(0);

    private AtomicLong httpRequestPerSecond = new AtomicLong(0);

    private LinkedList<Integer> httpRequestTPSSnapshots = new LinkedList<Integer>();

    public float avgHTTPCost() {
        float cost = (wholeRequestNum.longValue() == 0l) ? 0f : wholeCost / wholeRequestNum.longValue();
        return cost;
    }

    public long maxHTTPCost() {
        long cost = maxCost.longValue();
        return cost;
    }

    public long getHttpDiscard() {
        return httpDiscard.longValue();
    }

    public void recordHTTPRequest() {
        httpRequestPerSecond.incrementAndGet();
    }

    public void recordHTTPDiscard() {
        httpDiscard.incrementAndGet();
    }

    public void snapshotHTTPTPS() {
        Integer tps = httpRequestPerSecond.intValue();
        httpRequestTPSSnapshots.add(tps);
        httpRequestPerSecond.set(0);
        if (httpRequestTPSSnapshots.size() > STATIC_PERIOD / 1000) {
            httpRequestTPSSnapshots.removeFirst();
        }
    }

    public float maxHTTPTPS() {
        float tps = Collections.max(httpRequestTPSSnapshots);
        return tps;
    }

    public float avgHTTPTPS() {
        float tps = avg(httpRequestTPSSnapshots);
        return tps;
    }

    public void recordHTTPReqResTimeCost(long cost) {
        wholeRequestNum.incrementAndGet();
        wholeCost = wholeCost + cost;
        if (cost > maxCost.longValue()) {
            maxCost.set(cost);
        }
    }

    public void httpStatInfoClear() {
        wholeRequestNum.set(0l);
        wholeCost = 0f;
        maxCost.set(0l);
        httpDecodeNum.set(0l);
        httpDecodeTimeCost = 0f;
    }

    private float httpDecodeTimeCost = 0f;

    private AtomicLong httpDecodeNum = new AtomicLong(0);

    public void recordDecodeTimeCost(long cost) {
        httpDecodeNum.incrementAndGet();
        httpDecodeTimeCost = httpDecodeTimeCost + cost;
    }

    public float avgHTTPBodyDecodeCost() {
        float cost = (httpDecodeNum.longValue() == 0l) ? 0f : httpDecodeTimeCost / httpDecodeNum.longValue();
        return cost;
    }


    //////////////////////////////////////////////////////////////////////////
    public static final String PROXY_MONITOR_FORMAT_BATCHSENDMSG = "{\"maxBatchSendMsgTPS\":\"%.1f\",\"avgBatchSendMsgTPS\":\"%.1f\"," +
            " \"sum\":\"%s\", \"sumFail\":\"%s\", \"sumFailRate\":\"%.2f\", \"discard\":\"%s\"}";

    private AtomicLong sendBatchMsgNumPerSecond = new AtomicLong(0);

    private AtomicLong sendBatchMsgNumSum = new AtomicLong(0);

    private AtomicLong sendBatchMsgFailNumSum = new AtomicLong(0);

    //累计值
    private AtomicLong sendBatchMsgDiscardNumSum = new AtomicLong(0);

    public void recordSendBatchMsgDiscard(long delta) {
        sendBatchMsgDiscardNumSum.addAndGet(delta);
    }

    private LinkedList<Integer> sendBatchMsgTPSSnapshots = new LinkedList<Integer>();

    public void snapshotSendBatchMsgTPS() {
        Integer tps = sendBatchMsgNumPerSecond.intValue();
        sendBatchMsgTPSSnapshots.add(tps);
        sendBatchMsgNumPerSecond.set(0);
        if (sendBatchMsgTPSSnapshots.size() > STATIC_PERIOD / 1000) {
            sendBatchMsgTPSSnapshots.removeFirst();
        }
    }

    public float maxSendBatchMsgTPS() {
        float tps = Collections.max(sendBatchMsgTPSSnapshots);
        return tps;
    }

    public float avgSendBatchMsgTPS() {
        float tps = avg(sendBatchMsgTPSSnapshots);
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
        return (sendBatchMsgNumSum.longValue() == 0l) ? 0f : sendBatchMsgFailNumSum.floatValue() / sendBatchMsgNumSum.longValue();
    }

    public void cleanSendBatchStat() {
        sendBatchMsgNumSum.set(0l);
        sendBatchMsgFailNumSum.set(0l);
    }

    public long getSendBatchMsgDiscardNumSum() {
        return sendBatchMsgDiscardNumSum.longValue();
    }

    //////////////////////////////////////////////////////////////////////////
    public static final String PROXY_MONITOR_FORMAT_SENDMSG = "{\"maxSendMsgTPS\":\"%.1f\",\"avgSendMsgTPS\":\"%.1f\"," +
            " \"sum\":\"%s\", \"sumFail\":\"%s\", \"sumFailRate\":\"%.2f\", \"replyMsg\":\"%s\", \"replyFail\":\"%s\"}";

    private AtomicLong sendMsgNumSum = new AtomicLong(0);

    private AtomicLong sendMsgFailNumSum = new AtomicLong(0);

    private AtomicLong replyMsgNumSum = new AtomicLong(0);

    private AtomicLong replyMsgFailNumSum = new AtomicLong(0);

    private AtomicLong sendMsgNumPerSecond = new AtomicLong(0);

    private LinkedList<Integer> sendMsgTPSSnapshots = new LinkedList<Integer>();

    public void snapshotSendMsgTPS() {
        Integer tps = sendMsgNumPerSecond.intValue();
        sendMsgTPSSnapshots.add(tps);
        sendMsgNumPerSecond.set(0);
        if (sendMsgTPSSnapshots.size() > STATIC_PERIOD / 1000) {
            sendMsgTPSSnapshots.removeFirst();
        }
    }

    public float maxSendMsgTPS() {
        float tps = Collections.max(sendMsgTPSSnapshots);
        return tps;
    }

    public float avgSendMsgTPS() {
        float tps = avg(sendMsgTPSSnapshots);
        return tps;
    }

    public void recordSendMsg() {
        sendMsgNumPerSecond.incrementAndGet();
        sendMsgNumSum.incrementAndGet();
    }

    public void recordReplyMsg() {
        replyMsgNumSum.incrementAndGet();
    }

    public void recordReplyMsgFailed() {
        replyMsgFailNumSum.incrementAndGet();
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
        return (sendMsgNumSum.longValue() == 0l) ? 0f : sendMsgFailNumSum.floatValue() / sendMsgNumSum.longValue();
    }

    public void recordSendMsgFailed() {
        sendMsgFailNumSum.incrementAndGet();
    }

    public void cleanSendMsgStat() {
        sendMsgNumSum.set(0l);
        replyMsgNumSum.set(0l);
        sendMsgFailNumSum.set(0l);
        replyMsgFailNumSum.set(0l);
    }

    ////////////////////////////////////////////////////////////////////////////
    public static final String PROXY_MONITOR_FORMAT_PUSHMSG = "{\"maxPushMsgTPS\":\"%.1f\",\"avgPushMsgTPS\":\"%.1f\"," +
            " \"sum\":\"%s\", \"sumFail\":\"%s\", \"sumFailRate\":\"%.1f\", \"maxClientLatency\":\"%.1f\", \"avgClientLatency\":\"%.1f\"}";

    private float wholePushCost = 0f;

    private AtomicLong wholePushRequestNum = new AtomicLong(0);

    private AtomicLong maxHttpPushLatency = new AtomicLong(0);

    private AtomicLong pushMsgNumPerSecond = new AtomicLong(0);

    private LinkedList<Integer> pushMsgTPSSnapshots = new LinkedList<Integer>();

    private AtomicLong httpPushMsgNumSum = new AtomicLong(0);

    private AtomicLong httpPushFailNumSum = new AtomicLong(0);

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

    public float avgHTTPPushLatency() {
        return (wholePushRequestNum.longValue() == 0l) ? 0f : wholePushCost / wholePushRequestNum.longValue();
    }

    public float maxHTTPPushLatency() {
        return maxHttpPushLatency.floatValue();
    }

    public float maxPushMsgTPS() {
        float tps = Collections.max(pushMsgTPSSnapshots);
        return tps;
    }

    public float avgPushMsgTPS() {
        float tps = avg(pushMsgTPSSnapshots);
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
        return (httpPushMsgNumSum.longValue() == 0l) ? 0f : httpPushFailNumSum.floatValue() / httpPushMsgNumSum.longValue();
    }

    public void recordHttpPushMsgFailed() {
        sendMsgFailNumSum.incrementAndGet();
    }

    public void cleanHttpPushMsgStat() {
        httpPushFailNumSum.set(0l);
        httpPushMsgNumSum.set(0l);
        wholeRequestNum.set(0l);
        wholeCost = 0f;
        maxCost.set(0l);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static final String PROXY_MONITOR_FORMAT_BLOCKQ = "{\"batchMsgQ\":\"%s\",\"sendMsgQ\":\"%s\"," +
            "\"pushMsgQ\":\"%s\",\"httpRetryQ\":\"%s\"}";

    ///////////////////////////////////////////////////////////////////////////
    public static final String PROXY_MONITOR_FORMAT_MQ_CLIENT = "{\"batchAvgSend2MQCost\":\"%.1f\", \"avgSend2MQCost\":\"%.1f\", \"avgReply2MQCost\":\"%.1f\"}";

    private float batchSend2MQWholeCost = 0f;

    private AtomicLong batchSend2MQNum = new AtomicLong(0);

    private float send2MQWholeCost = 0f;

    private AtomicLong send2MQNum = new AtomicLong(0);

    private float reply2MQWholeCost = 0f;

    private AtomicLong reply2MQNum = new AtomicLong(0);

    public void recordBatchSendMsgCost(long cost) {
        batchSend2MQNum.incrementAndGet();
        batchSend2MQWholeCost = batchSend2MQWholeCost + cost;
    }

    public float avgBatchSendMsgCost() {
        float cost = (batchSend2MQNum.intValue() == 0) ? 0f : batchSend2MQWholeCost / batchSend2MQNum.intValue();
        return cost;
    }

    public void recordSendMsgCost(long cost) {
        send2MQNum.incrementAndGet();
        send2MQWholeCost = send2MQWholeCost + cost;
    }

    public float avgSendMsgCost() {
        float cost = (send2MQNum.intValue() == 0) ? 0f : send2MQWholeCost / send2MQNum.intValue();
        return cost;
    }

    public void recordReplyMsgCost(long cost) {
        reply2MQNum.incrementAndGet();
        reply2MQWholeCost = reply2MQWholeCost + cost;
    }

    public float avgReplyMsgCost() {
        float cost = (reply2MQNum.intValue() == 0) ? 0f : reply2MQWholeCost / reply2MQNum.intValue();
        return cost;
    }

    public void send2MQStatInfoClear() {
        batchSend2MQWholeCost = 0f;
        batchSend2MQNum.set(0l);
        send2MQWholeCost = 0f;
        send2MQNum.set(0l);
        reply2MQWholeCost = 0f;
        reply2MQNum.set(0l);
    }
}
