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

package org.apache.eventmesh.metrics.api.model;

import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

// todo: split this class
@Slf4j
public class HttpSummaryMetrics implements Metric {

    private static final int STATIC_PERIOD = 30 * 1000;

    public HttpSummaryMetrics(final ThreadPoolExecutor batchMsgExecutor,
                              final ThreadPoolExecutor sendMsgExecutor,
                              final ThreadPoolExecutor pushMsgExecutor,
                              final DelayQueue<?> httpFailedQueue) {
        this.batchMsgExecutor = batchMsgExecutor;
        this.sendMsgExecutor = sendMsgExecutor;
        this.pushMsgExecutor = pushMsgExecutor;
        this.httpFailedQueue = httpFailedQueue;
    }

    private float wholeCost = 0f;

    private AtomicLong wholeRequestNum = new AtomicLong(0);

    //cumulative value
    private AtomicLong httpDiscard = new AtomicLong(0);

    private AtomicLong maxCost = new AtomicLong(0);

    private AtomicLong httpRequestPerSecond = new AtomicLong(0);

    private LinkedList<Integer> httpRequestTPSSnapshots = new LinkedList<>();

    public float avgHTTPCost() {
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
        return Collections.max(httpRequestTPSSnapshots);
    }

    public float avgHTTPTPS() {
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

    private float httpDecodeTimeCost = 0f;

    private AtomicLong httpDecodeNum = new AtomicLong(0);

    public void recordDecodeTimeCost(long cost) {
        httpDecodeNum.incrementAndGet();
        httpDecodeTimeCost = httpDecodeTimeCost + cost;
    }

    public float avgHTTPBodyDecodeCost() {
        return (httpDecodeNum.longValue() == 0L) ? 0f : httpDecodeTimeCost / httpDecodeNum.longValue();
    }
    
    private AtomicLong sendBatchMsgNumPerSecond = new AtomicLong(0);

    private AtomicLong sendBatchMsgNumSum = new AtomicLong(0);

    private AtomicLong sendBatchMsgFailNumSum = new AtomicLong(0);

    // This is a cumulative value
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
        return Collections.max(sendBatchMsgTPSSnapshots);
    }

    public float avgSendBatchMsgTPS() {
        return avg(sendBatchMsgTPSSnapshots);
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
        return (sendBatchMsgNumSum.longValue() == 0L) ? 0f : sendBatchMsgFailNumSum.floatValue() / sendBatchMsgNumSum.longValue();
    }

    public void cleanSendBatchStat() {
        sendBatchMsgNumSum.set(0L);
        sendBatchMsgFailNumSum.set(0L);
    }

    public long getSendBatchMsgDiscardNumSum() {
        return sendBatchMsgDiscardNumSum.longValue();
    }
    
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
        return Collections.max(sendMsgTPSSnapshots);
    }

    public float avgSendMsgTPS() {
        return avg(sendMsgTPSSnapshots);
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
        return (sendMsgNumSum.longValue() == 0L) ? 0f : sendMsgFailNumSum.floatValue() / sendMsgNumSum.longValue();
    }

    public void recordSendMsgFailed() {
        sendMsgFailNumSum.incrementAndGet();
    }

    public void cleanSendMsgStat() {
        sendMsgNumSum.set(0L);
        replyMsgNumSum.set(0L);
        sendMsgFailNumSum.set(0L);
        replyMsgFailNumSum.set(0L);
    }
    
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
        return (wholePushRequestNum.longValue() == 0L) ? 0f : wholePushCost / wholePushRequestNum.longValue();
    }

    public float maxHTTPPushLatency() {
        return maxHttpPushLatency.floatValue();
    }

    public float maxPushMsgTPS() {
        return Collections.max(pushMsgTPSSnapshots);
    }

    public float avgPushMsgTPS() {
        return avg(pushMsgTPSSnapshots);
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
        return (httpPushMsgNumSum.longValue() == 0L) ? 0f : httpPushFailNumSum.floatValue() / httpPushMsgNumSum.longValue();
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
        return (batchSend2MQNum.intValue() == 0) ? 0f : batchSend2MQWholeCost / batchSend2MQNum.intValue();
    }

    public void recordSendMsgCost(long cost) {
        send2MQNum.incrementAndGet();
        send2MQWholeCost = send2MQWholeCost + cost;
    }

    public float avgSendMsgCost() {
        return (send2MQNum.intValue() == 0) ? 0f : send2MQWholeCost / send2MQNum.intValue();
    }

    public void recordReplyMsgCost(long cost) {
        reply2MQNum.incrementAndGet();
        reply2MQWholeCost = reply2MQWholeCost + cost;
    }

    public float avgReplyMsgCost() {
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

    // execute metrics
    private final ThreadPoolExecutor batchMsgExecutor;

    private final ThreadPoolExecutor sendMsgExecutor;

    private final ThreadPoolExecutor pushMsgExecutor;

    private final DelayQueue<?> httpFailedQueue;

    public int getBatchMsgQueueSize() {
        return batchMsgExecutor.getQueue().size();
    }

    public int getSendMsgQueueSize() {
        return sendMsgExecutor.getQueue().size();
    }

    public int getPushMsgQueueSize() {
        return pushMsgExecutor.getQueue().size();
    }

    public int getHttpRetryQueueSize() {
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
