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

package org.apache.eventmesh.admin.server.web.db;

import org.apache.eventmesh.common.EventMeshThreadFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DBThreadPool {

    private final ThreadPoolExecutor executor =
        new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2, 0L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), new EventMeshThreadFactory("admin-server-db"),
            new ThreadPoolExecutor.DiscardOldestPolicy());


    private final ScheduledThreadPoolExecutor checkScheduledExecutor =
            new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), new EventMeshThreadFactory("admin-server-check-scheduled"),
                    new ThreadPoolExecutor.DiscardOldestPolicy());

    @PreDestroy
    private void destroy() {
        if (!executor.isShutdown()) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.info("wait handler thread pool shutdown timeout, it will shutdown immediately");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("wait handler thread pool shutdown fail");
            }
        }

        if (!checkScheduledExecutor.isShutdown()) {
            try {
                checkScheduledExecutor.shutdown();
                if (!checkScheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.info("wait scheduled thread pool shutdown timeout, it will shutdown immediately");
                    checkScheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("wait scheduled thread pool shutdown fail");
            }
        }
    }

    public ThreadPoolExecutor getExecutors() {
        return executor;
    }

    public ScheduledThreadPoolExecutor getCheckExecutor() {
        return checkScheduledExecutor;
    }
}
