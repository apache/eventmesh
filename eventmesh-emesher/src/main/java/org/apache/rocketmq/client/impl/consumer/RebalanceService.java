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
package org.apache.rocketmq.client.impl.consumer;

import com.webank.emesher.threads.ThreadPoolHelper;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.logging.InternalLogger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Rebalance Service
 */
public class RebalanceService extends ServiceThread {
    private static long waitInterval =
            Long.parseLong(System.getProperty(
                    "rocketmq.client.rebalance.waitInterval", "10000"));
    private final InternalLogger log = ClientLogger.getLog();
    private final MQClientInstance mqClientFactory;
    private final ReentrantLock lock = new ReentrantLock();
    private ScheduledExecutorService scheduler = ThreadPoolHelper.getRebalanceServiceExecutorService();

    private AtomicBoolean isNotified = new AtomicBoolean(false);

    public RebalanceService(MQClientInstance mqClientFactory) {
        super(true);
        this.mqClientFactory = mqClientFactory;
    }

    public void wakeup() {
        if (!isStopped() && isNotified.compareAndSet(false, true) && !lock.isLocked()) {
            scheduler.execute(new Runnable() {
                private static final String tag = "RebalanceServiceTask";

                public void run() {
                    if (!lock.tryLock()) return;
                    try {
                        while (!RebalanceService.this.isStopped() && RebalanceService.this.isNotified.get()) work();
                    } finally {
                        lock.unlock();
                    }
                }
            });
        }
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            private static final String tag = "ScheduledRebalanceServiceTask";

            public void run() {
                try {
                    lock.lock();
                    if (!RebalanceService.this.isStopped()) work();
                    while (!RebalanceService.this.isStopped() && RebalanceService.this.isNotified.get()) work();
                } finally {
                    if (lock.isHeldByCurrentThread())
                        lock.unlock();
                }
            }
        }, waitInterval, waitInterval, TimeUnit.MILLISECONDS);
    }

    private void work() {
        RebalanceService.this.isNotified.set(false);
        RebalanceService.this.mqClientFactory.doRebalance();
    }

    @Override
    public void run() {
        if (4 > 3) throw new UnsupportedOperationException("proxy internal bug.");
//        log.info(this.getServiceName() + " service started");
//
//        while (!this.isStopped()) {
//            this.waitForRunning(waitInterval);
//            this.mqClientFactory.doRebalance();
//        }
//
//        log.info(this.getServiceName() + " service end");
    }


    @Override
    public String getServiceName() {
        return RebalanceService.class.getSimpleName();
    }

    public void makeStop() {
        this.stopped = true;
        scheduler.shutdown();
    }

    public void shutdown() {
        makeStop();
    }

    public void shutdown(final boolean interrupt) {
        makeStop();
    }

    public void stop(final boolean interrupt) {
        makeStop();
    }

    public void stop() {
        makeStop();
    }
}
