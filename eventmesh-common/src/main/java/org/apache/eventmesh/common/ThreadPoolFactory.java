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

package org.apache.eventmesh.common;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class ThreadPoolFactory {

    public static ThreadPoolExecutor createThreadPoolExecutor(int core, int max, final String threadName) {
        return createThreadPoolExecutor(core, max, threadName, true);
    }

    public static ThreadPoolExecutor createThreadPoolExecutor(int core, int max, final String threadName,
                                                              final boolean isDaemon) {
        return createThreadPoolExecutor(core, max, new LinkedBlockingQueue<>(1000), threadName, isDaemon);
    }

    public static ThreadPoolExecutor createThreadPoolExecutor(int core, int max, BlockingQueue<Runnable> blockingQueue,
                                                              final String threadName, final boolean isDaemon) {
        return new ThreadPoolExecutor(core, max, 10 * 1000, TimeUnit.MILLISECONDS, blockingQueue,
                new ThreadFactoryBuilder().setNameFormat(threadName).setDaemon(isDaemon).build()
        );
    }

    public static ThreadPoolExecutor createThreadPoolExecutor(int core, int max, ThreadFactory threadFactory) {
        return createThreadPoolExecutor(core, max, new LinkedBlockingQueue<>(1000), threadFactory);
    }

    public static ThreadPoolExecutor createThreadPoolExecutor(int core, int max, BlockingQueue<Runnable> blockingQueue,
                                                              ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(core, max, 10 * 1000, TimeUnit.MILLISECONDS, blockingQueue, threadFactory);
    }

    public static ScheduledExecutorService createSingleScheduledExecutor(final String threadName) {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private AtomicInteger ai = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, threadName + ai.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public static ScheduledExecutorService createScheduledExecutor(int core, final String threadName) {
        return createScheduledExecutor(core, threadName, true);
    }

    public static ScheduledExecutorService createScheduledExecutor(int core, final String threadName,
                                                                   final boolean isDaemon) {
        return Executors.newScheduledThreadPool(core, new ThreadFactory() {
            private AtomicInteger ai = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, threadName + ai.incrementAndGet());
                thread.setDaemon(isDaemon);
                return thread;
            }
        });
    }

    public static ScheduledExecutorService createScheduledExecutor(int core, ThreadFactory threadFactory) {
        return Executors.newScheduledThreadPool(core, threadFactory);
    }
}
