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

package org.apache.eventmesh.runtime.core.retry.core;

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.enums.ProtocolType;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.eventmesh.runtime.core.retry.Attempt;
import org.apache.eventmesh.runtime.core.retry.exception.EventMeshRetryException;
import org.apache.eventmesh.runtime.core.retry.Retryer;
import org.apache.eventmesh.runtime.core.retry.strategies.StopStrategy;
import org.apache.eventmesh.runtime.core.retry.strategies.StorageStrategies;
import org.apache.eventmesh.runtime.core.retry.strategies.StorageStrategy;
import org.apache.eventmesh.runtime.core.retry.strategies.WaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryTaskManager implements Retryer {

    private final Logger retryLogger = LoggerFactory.getLogger("RetryTaskManager");

    private final CommonConfiguration configuration;

    public RetryTaskManager(CommonConfiguration configuration) {
        this.configuration = configuration;
    }

    private final BlockingQueue<RetryContext> failed = new LinkedBlockingDeque<>(20000);

    private ThreadPoolExecutor pool;

    private Thread dispatcher;

    private ProtocolType protocolType;

    @Override
    public void commit(RetryContext retryContext) {
        if (failed.size() >= configuration.getEventMeshServerRetryBlockQSize()) {
            retryLogger.error("[RETRY-QUEUE] is full!");
            return;
        }
        failed.offer(retryContext);
    }

    @Override
    public void init() {
        pool = new ThreadPoolExecutor(configuration.getEventMeshServerRetryThreadNum(),
            configuration.getEventMeshServerRetryThreadNum(),
            60000,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(
            configuration.getEventMeshServerRetryBlockQSize()),
            new EventMeshThreadFactory("RetryTaskManager", true, Thread.NORM_PRIORITY),
            new ThreadPoolExecutor.AbortPolicy());

        dispatcher = new Thread(() -> {
            try {
                RetryContext retryObj;
                while (!Thread.currentThread().isInterrupted() && (retryObj = failed.take()) != null) {
                    if (retryLogger.isDebugEnabled()) {
                        retryLogger.debug("retryObj : {}", retryObj);
                    }
                    RetryContext retryContext = retryObj;
                    StopStrategy stopStrategy = retryContext.getStopStrategy();
                    WaitStrategy waitStrategy = retryContext.getWaitStrategy();
                    StorageStrategy storageStrategy = Optional.ofNullable(retryContext.getStorageStrategy())
                        .orElse(configuration.isEventMeshServerRetryStorageEnabled() ? StorageStrategies.storeInStorage() :
                            StorageStrategies.storeInMemory());
                    pool.execute(() -> {
                        try {
                            long startTime = System.nanoTime();
                            for (int attemptNumber = 1; ; attemptNumber++) {
                                Attempt<Void> attempt = new ResultAttempt<Void>(null, attemptNumber,
                                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
                                retryContext.retry();
                                retryContext.setExecuteTime(System.currentTimeMillis());
                                retryContext.setAttempt(attempt);
                                if (stopStrategy.shouldStop(attempt)) {
                                    break;
                                } else {
                                    long sleepTime = waitStrategy.computeSleepTime(attempt);
                                    try {
                                        Thread.sleep(sleepTime);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new EventMeshRetryException(attemptNumber, attempt);
                                    }
                                }
                            }
                            retryContext.complete();
                        } catch (Exception e) {
                            retryLogger.error("[{}]RetryTaskManager-dispatcher error!", protocolType, e);
                        }
                    });
                }
            } catch (Exception e) {
                retryLogger.error("[{}]RetryTaskManager-dispatcher error!", protocolType, e);
            }
        }, "RetryTaskManager-dispatcher-" + protocolType);
        dispatcher.setDaemon(true);
        log.info("[{}]RetryTaskManager init successfully......", protocolType);
    }


    static final class ResultAttempt<R> implements Attempt<R> {
        private final R result;
        private final int attemptNumber;
        private final long delaySinceFirstAttempt;

        public ResultAttempt(R result, int attemptNumber, long delaySinceFirstAttempt) {
            this.result = result;
            this.attemptNumber = attemptNumber;
            this.delaySinceFirstAttempt = delaySinceFirstAttempt;
        }

        @Override
        public R get() {
            return result;
        }

        @Override
        public boolean hasResult() {
            return true;
        }

        @Override
        public boolean hasException() {
            return false;
        }

        @Override
        public R getResult() throws IllegalStateException {
            return result;
        }

        @Override
        public Throwable getExceptionCause() throws IllegalStateException {
            throw new IllegalStateException("The attempt resulted in a result, not in an exception");
        }

        @Override
        public int getAttemptNumber() {
            return attemptNumber;
        }

        @Override
        public long getDelaySinceFirstAttempt() {
            return delaySinceFirstAttempt;
        }
    }

    @Override
    public void shutdown() {
        dispatcher.interrupt();
        pool.shutdown();
        log.info("[{}]RetryTaskManager shutdown......", protocolType);
    }

    @Override
    public void start() {
        dispatcher.start();
        log.info("[{}]RetryTaskManager started......", protocolType);
    }
}
