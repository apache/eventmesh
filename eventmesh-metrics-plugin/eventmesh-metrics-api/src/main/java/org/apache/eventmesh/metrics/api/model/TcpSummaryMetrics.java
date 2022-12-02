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

import java.util.concurrent.atomic.AtomicInteger;


public class TcpSummaryMetrics implements Metric {

    private AtomicInteger client2eventMeshMsgNum;
    private AtomicInteger eventMesh2mqMsgNum;
    private AtomicInteger mq2eventMeshMsgNum;
    private AtomicInteger eventMesh2clientMsgNum;

    private double client2eventMeshTPS;
    private double eventMesh2clientTPS;
    private double eventMesh2mqTPS;
    private double mq2eventMeshTPS;
    private int subTopicNum;

    private int allConnections;

    private int retrySize;

    public TcpSummaryMetrics() {
        this.client2eventMeshMsgNum = new AtomicInteger(0);
        this.eventMesh2mqMsgNum = new AtomicInteger(0);
        this.mq2eventMeshMsgNum = new AtomicInteger(0);
        this.eventMesh2clientMsgNum = new AtomicInteger(0);
    }

    public AtomicInteger getClient2eventMeshMsgNum() {
        return client2eventMeshMsgNum;
    }

    public AtomicInteger getEventMesh2mqMsgNum() {
        return eventMesh2mqMsgNum;
    }

    public AtomicInteger getMq2eventMeshMsgNum() {
        return mq2eventMeshMsgNum;
    }


    public AtomicInteger getEventMesh2clientMsgNum() {
        return eventMesh2clientMsgNum;
    }


    public int client2eventMeshMsgNum() {
        return client2eventMeshMsgNum.get();
    }

    public int eventMesh2mqMsgNum() {
        return eventMesh2mqMsgNum.get();
    }

    public int mq2eventMeshMsgNum() {
        return mq2eventMeshMsgNum.get();
    }

    public int eventMesh2clientMsgNum() {
        return eventMesh2clientMsgNum.get();
    }

    public void resetClient2EventMeshMsgNum() {
        this.client2eventMeshMsgNum = new AtomicInteger(0);
    }

    public void resetEventMesh2mqMsgNum() {
        this.eventMesh2mqMsgNum = new AtomicInteger(0);
    }

    public void resetMq2eventMeshMsgNum() {
        this.mq2eventMeshMsgNum = new AtomicInteger(0);
    }

    public void resetEventMesh2ClientMsgNum() {
        this.eventMesh2clientMsgNum = new AtomicInteger(0);
    }

    public double getClient2eventMeshTPS() {
        return client2eventMeshTPS;
    }

    public void setClient2eventMeshTPS(double client2eventMeshTPS) {
        this.client2eventMeshTPS = client2eventMeshTPS;
    }

    public double getEventMesh2clientTPS() {
        return eventMesh2clientTPS;
    }

    public void setEventMesh2clientTPS(double eventMesh2clientTPS) {
        this.eventMesh2clientTPS = eventMesh2clientTPS;
    }

    public double getEventMesh2mqTPS() {
        return eventMesh2mqTPS;
    }

    public void setEventMesh2mqTPS(double eventMesh2mqTPS) {
        this.eventMesh2mqTPS = eventMesh2mqTPS;
    }

    public double getMq2eventMeshTPS() {
        return mq2eventMeshTPS;
    }

    public void setMq2eventMeshTPS(double mq2eventMeshTPS) {
        this.mq2eventMeshTPS = mq2eventMeshTPS;
    }

    public double getAllTPS() {
        return client2eventMeshTPS + eventMesh2clientTPS;
    }

    public int getSubTopicNum() {
        return subTopicNum;
    }

    public void setSubTopicNum(int subTopicNum) {
        this.subTopicNum = subTopicNum;
    }

    public int getAllConnections() {
        return allConnections;
    }

    public void setAllConnections(int allConnections) {
        this.allConnections = allConnections;
    }

    public void setRetrySize(int retrySize) {
        this.retrySize = retrySize;
    }

    public int getRetrySize() {
        return retrySize;
    }
}
