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

import java.util.concurrent.atomic.AtomicLong;

public class GrpcSummaryMetrics implements Metric {

    private final AtomicLong client2EventMeshMsgNum;
    private final AtomicLong eventMesh2MqMsgNum;
    private final AtomicLong mq2EventMeshMsgNum;
    private final AtomicLong eventMesh2ClientMsgNum;

    private long client2EventMeshTPS;
    private long eventMesh2ClientTPS;
    private long eventMesh2MqTPS;
    private long mq2EventMeshTPS;

    private long retrySize;
    private long subscribeTopicNum;

    public GrpcSummaryMetrics() {
        this.client2EventMeshMsgNum = new AtomicLong(0);
        this.eventMesh2MqMsgNum = new AtomicLong(0);
        this.mq2EventMeshMsgNum = new AtomicLong(0);
        this.eventMesh2ClientMsgNum = new AtomicLong(0);
    }

    public void clearAllMessageCounter() {
        client2EventMeshMsgNum.set(0L);
        eventMesh2MqMsgNum.set(0L);
        mq2EventMeshMsgNum.set(0L);
        eventMesh2ClientMsgNum.set(0L);
    }

    public void refreshTpsMetrics(long intervalMills) {
        client2EventMeshTPS = 1000 * client2EventMeshMsgNum.get() / intervalMills;
        eventMesh2ClientTPS = 1000 * eventMesh2MqMsgNum.get() / intervalMills;
        eventMesh2MqTPS = 1000 * mq2EventMeshMsgNum.get() / intervalMills;
        mq2EventMeshTPS = 1000 * eventMesh2ClientMsgNum.get() / intervalMills;
    }

    public AtomicLong getClient2EventMeshMsgNum() {
        return client2EventMeshMsgNum;
    }

    public AtomicLong getEventMesh2MqMsgNum() {
        return eventMesh2MqMsgNum;
    }

    public AtomicLong getMq2EventMeshMsgNum() {
        return mq2EventMeshMsgNum;
    }

    public AtomicLong getEventMesh2ClientMsgNum() {
        return eventMesh2ClientMsgNum;
    }

    public long getClient2EventMeshTPS() {
        return client2EventMeshTPS;
    }

    public long getEventMesh2ClientTPS() {
        return eventMesh2ClientTPS;
    }

    public long getEventMesh2MqTPS() {
        return eventMesh2MqTPS;
    }

    public long getMq2EventMeshTPS() {
        return mq2EventMeshTPS;
    }

    public long getRetrySize() {
        return retrySize;
    }

    public void setRetrySize(long retrySize) {
        this.retrySize = retrySize;
    }

    public long getSubscribeTopicNum() {
        return subscribeTopicNum;
    }

    public void setSubscribeTopicNum(long subscribeTopicNum) {
        this.subscribeTopicNum = subscribeTopicNum;
    }
}
