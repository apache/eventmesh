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

package org.apache.eventmesh.openconnect.api.monitor;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public abstract class AbstractConnectorMonitor implements Monitor {

    private final String taskId;
    private final String jobId;
    private final String ip;
    private final LongAdder totalRecordNum;
    private final LongAdder totalTimeCost;
    protected final AtomicLong startTime;
    private final AtomicLong maxTimeCost;
    private long averageTime = 0;
    private double tps = 0;

    public AbstractConnectorMonitor(String taskId, String jobId, String ip) {
        this.taskId = taskId;
        this.jobId = jobId;
        this.ip = ip;
        this.totalRecordNum = new LongAdder();
        this.totalTimeCost = new LongAdder();
        this.startTime = new AtomicLong(System.currentTimeMillis());
        this.maxTimeCost = new AtomicLong();
    }

    @Override
    public synchronized void recordProcess(long timeCost) {
        totalRecordNum.increment();
        totalTimeCost.add(timeCost);
        maxTimeCost.updateAndGet(max -> Math.max(max, timeCost));
    }

    @Override
    public synchronized void recordProcess(int recordCount, long timeCost) {
        totalRecordNum.add(recordCount);
        totalTimeCost.add(timeCost);
        maxTimeCost.updateAndGet(max -> Math.max(max, timeCost));
    }

    @Override
    public synchronized void printMetrics() {
        long totalRecords = totalRecordNum.sum();
        long totalCost = totalTimeCost.sum();
        averageTime = totalRecords > 0 ? totalCost / totalRecords : 0;
        long elapsedTime = (System.currentTimeMillis() - startTime.get()) / 1000; // in seconds
        tps = elapsedTime > 0 ? (double) totalRecords / elapsedTime : 0;

        log.info("========== Metrics ==========");
        log.info("TaskId: {}|JobId: {}|ip: {}", taskId, jobId, ip);
        log.info("Total records: {}", totalRecordNum);
        log.info("Total time (ms): {}", totalTimeCost);
        log.info("Max time per record (ms): {}", maxTimeCost);
        log.info("Average time per record (ms): {}", averageTime);
        log.info("TPS: {}", tps);
    }
}
