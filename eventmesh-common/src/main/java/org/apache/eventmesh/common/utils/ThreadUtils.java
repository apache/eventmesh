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

package org.apache.eventmesh.common.utils;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ThreadUtils {

    private static volatile long currentPID = -1;

    public static void randomPause(long min, long max) {
        randomPause(min, max, TimeUnit.MILLISECONDS);
    }

    public static void randomPause(long min, long max, TimeUnit timeUnit) {
        // nextInt is normally exclusive of the top value, so add 1 to make it inclusive
        try {
            long timeout = ThreadLocalRandom.current().nextLong(min, max + 1);
            timeUnit.sleep(timeout);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt;
        }
    }

    public static void randomPause(long max) {
        randomPause(1, max);
    }

    @Deprecated
    public static void sleep(long timeout) {
        sleep(timeout, TimeUnit.MILLISECONDS);
    }

    public static void sleep(long timeout, TimeUnit timeUnit) {
        try {
            sleepWithThrowException(timeout, timeUnit);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt;
        }
    }

    public static void sleepWithThrowException(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (null == timeUnit) {
            return;
        }
        timeUnit.sleep(timeout);
    }

    /**
     * get current process id.
     *
     * @return process id
     */
    public static long getPID() {
        if (currentPID == -1) {
            synchronized (ThreadUtils.class) {
                if (currentPID == -1) {
                    currentPID = Long.parseLong(SystemUtils.getProcessId());
                }
            }
        }
        return currentPID;
    }
}
