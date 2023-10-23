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

package org.apache.eventmesh.runtime.core.protocol;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadPoolExecutor;

import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.runtime.core.retry.Retryer;
import org.apache.eventmesh.runtime.core.timer.HashedWheelTimer;
import org.apache.eventmesh.runtime.core.timer.Timer;
import org.apache.eventmesh.runtime.core.timer.TimerTask;

@Slf4j
public abstract class AbstractRetryer implements Retryer {

    protected final DelayQueue<DelayRetryable> retrys = new DelayQueue<>();

    protected ThreadPoolExecutor pool;

    protected abstract void pushRetry(DelayRetryable delayRetryable);

    protected abstract void init();

    private volatile Timer timer;

    @Override
    public void newTimeout(TimerTask timerTask, long delay, TimeUnit timeUnit) {
        timer.newTimeout(timerTask, delay, timeUnit);
    }

    public void shutdown() {
        timer.stop();
        log.info("EventMesh retryer shutdown......");
    }

    public void start() throws Exception {
        if (timer == null) {
            synchronized (this) {
                if (timer == null) {
                    timer = new HashedWheelTimer(
                        new EventMeshThreadFactory("failback-cluster-timer", true),
                        1,
                        TimeUnit.SECONDS, 512, 100);
                }
            }
        }
        log.info("EventMesh retryer started......");
    }

    public long getPendingTimeouts() {
        if (timer == null) {
            return 0;
        }
        return timer.pendingTimeouts();
    }
}
