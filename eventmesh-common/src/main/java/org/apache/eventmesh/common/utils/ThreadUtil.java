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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadUtil {

    private static long currentPID = -1;

    public static void randomSleep(int min, int max) throws Exception {
        // nextInt is normally exclusive of the top value, so add 1 to make it inclusive
        int random = ThreadLocalRandom.current().nextInt(min, max + 1);
        Thread.sleep(random);

    }

    public static void randomSleep(int max) throws Exception {
        randomSleep(1, max);
    }

    /**
     * get current process id only once.
     *
     * @return process id
     */
    public static long getPID() {
        if (currentPID >= 0) {
            return currentPID;
        }
        String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        if (processName != null && processName.length() > 0) {
            try {
                currentPID = Long.parseLong(processName.split("@")[0]);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public static ThreadFactory createThreadFactory(boolean isDaemon, String threadPrefix) {
        return new ThreadFactory() {
            private AtomicInteger threadNum = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, threadPrefix + threadNum.incrementAndGet());
                thread.setDaemon(isDaemon);
                return thread;
            }
        };
    }

    public static class EventMeshThreadFactoryImpl implements ThreadFactory {
        private final AtomicLong threadIndex = new AtomicLong(0);
        private final     String     threadNamePrefix;
        private Boolean isDaemonSpecified = null;

        public EventMeshThreadFactoryImpl(final String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        public EventMeshThreadFactoryImpl(final String threadNamePrefix, final boolean isDaemonSpecified) {
            this.threadNamePrefix = threadNamePrefix;
            this.isDaemonSpecified = isDaemonSpecified;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, threadNamePrefix + '-' + this.threadIndex.incrementAndGet());
            if (isDaemonSpecified != null) {
                t.setDaemon(isDaemonSpecified);
            }
            return t;
        }
    }
}
