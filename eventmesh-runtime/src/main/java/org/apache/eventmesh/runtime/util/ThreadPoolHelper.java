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

        log.info("The ThreadPool's state==================");
        log.info("Shutdown | Terminating | Terminated: {} | {} | {}", threadPool.isShutdown(), threadPool.isTerminating(), threadPool.isTerminated());
        log.info("Active Threads: " + threadPool.getActiveCount());
        log.info("Completed Tasks / Tasks: {} / {}", threadPool.getCompletedTaskCount(), threadPool.getTaskCount());
        log.info("Queue Size: " + threadPool.getQueue().size());
        log.info("Core Pool Size: " + threadPool.getCorePoolSize());
        log.info("Maximum Pool Size: " + threadPool.getMaximumPoolSize());
        log.info("Keep Alive Time(ms): " + threadPool.getKeepAliveTime(TimeUnit.MILLISECONDS));
        log.info("The rejection policy: " + threadPool.getRejectedExecutionHandler().getClass().getSimpleName());
        log.info("========================================");
    }

}
