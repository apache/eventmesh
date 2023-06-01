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

package org.apache.eventmesh.runtime.util;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadPoolHelper {

    public static void printState(ThreadPoolExecutor threadPool) {
        if (threadPool == null) {
            log.warn("No thread pool is provided!");
            return;
        }

        boolean shutdown = threadPool.isShutdown();
        boolean terminating = threadPool.isTerminating();
        boolean terminated = threadPool.isTerminated();
        int activeCount = threadPool.getActiveCount();
        long completedTaskCount = threadPool.getCompletedTaskCount();
        long taskCount = threadPool.getTaskCount();
        int queueSize = threadPool.getQueue().size();
        int corePoolSize = threadPool.getCorePoolSize();
        int maximumPoolSize = threadPool.getMaximumPoolSize();
        long keepAliveTime = threadPool.getKeepAliveTime(TimeUnit.MILLISECONDS);
        String rejectPolicy = threadPool.getRejectedExecutionHandler().getClass().getSimpleName();
        if (log.isInfoEnabled()) {
            log.info("The ThreadPool's state==================");
            log.info("Shutdown | Terminating | Terminated: {} | {} | {}" + shutdown, terminating, terminated);
            log.info("Active Threads: " + activeCount);
            log.info("Completed Tasks / Tasks: {} / {}" + completedTaskCount, taskCount);
            log.info("Queue Size: " + queueSize);
            log.info("Core Pool Size: " + corePoolSize);
            log.info("Maximum Pool Size: " + maximumPoolSize);
            log.info("Keep Alive Time(ms): " + keepAliveTime);
            log.info("The rejection policy: " + rejectPolicy);
            log.info("========================================");
        } else {
            System.out.print("The ThreadPool's state==================\n");
            System.out.print("Shutdown | Terminating | Terminated: " + shutdown + " | " + terminating + " | " + terminated + "\n");
            System.out.print("Active Threads: " + activeCount + "\n");
            System.out.print("Completed Tasks / Tasks: " + completedTaskCount + " / " + taskCount + "\n");
            System.out.print("Queue Size: " + queueSize + "\n");
            System.out.print("Core Pool Size: " + corePoolSize + "\n");
            System.out.print("Maximum Pool Size: " + maximumPoolSize + "\n");
            System.out.print("Keep Alive Time(ms): " + keepAliveTime + "\n");
            System.out.print("The rejection policy: " + rejectPolicy + "\n");
            System.out.print("========================================\n");
        }
    }

}
