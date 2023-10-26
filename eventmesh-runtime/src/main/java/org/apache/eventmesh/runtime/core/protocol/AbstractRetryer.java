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

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.runtime.core.retry.Retryer;
import org.apache.eventmesh.runtime.core.timer.HashedWheelTimer;
import org.apache.eventmesh.runtime.core.timer.Timer;
import org.apache.eventmesh.runtime.core.timer.TimerTask;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractRetryer implements Retryer {

    private volatile Timer timer;

    private static final int MAX_PENDING_TIMEOUTS = 10000;

    @Override
    public void newTimeout(TimerTask timerTask, long delay, TimeUnit timeUnit) {
        log.debug("[HASHED-WHEEL-TIMER] executed! taskClass={}, nowTime={}",
            timeUnit.getClass().getName(), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        timer.newTimeout(timerTask, delay, timeUnit);
    }

    @Override
    public void start() {
        if (timer == null) {
            synchronized (this) {
                if (timer == null) {
                    timer = new HashedWheelTimer(
                        new EventMeshThreadFactory("failback-cluster-timer", true),
                        1,
                        TimeUnit.SECONDS, 512, MAX_PENDING_TIMEOUTS);
                }
            }
        }
        log.info("EventMesh retryer started......");
    }

    @Override
    public void shutdown() {
        timer.stop();
        log.info("EventMesh retryer shutdown......");
    }

    @Override
    public long getPendingTimeouts() {
        if (timer == null) {
            return 0;
        }
        return timer.pendingTimeouts();
    }

    @Override
    public void printState() {
        if (timer == null) {
            log.warn("No HashedWheelTimer is provided!");
            return;
        }
        HashedWheelTimer hashedWheelTimer = (HashedWheelTimer) timer;

        log.info("[Retry-HashedWheelTimer] state==================");
        log.info("Running :{}", !hashedWheelTimer.isStop());
        log.info("Pending Timeouts: {} | Cancelled Timeouts: {}", hashedWheelTimer.pendingTimeouts(), hashedWheelTimer.cancelledTimeouts());
        log.info("========================================");
    }
}
