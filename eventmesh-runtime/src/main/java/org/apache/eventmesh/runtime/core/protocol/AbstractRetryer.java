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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractRetryer {

    protected final DelayQueue<DelayRetryable> retrys = new DelayQueue<>();

    protected ThreadPoolExecutor pool;

    protected Thread dispatcher;

    protected abstract void pushRetry(DelayRetryable delayRetryable);

    protected abstract void init();

    public int getRetrySize() {
        return retrys.size();
    }

    protected void initDispatcher(Thread dispatcher) {
        dispatcher = new Thread(() -> {
            try {
                DelayRetryable retryObj;
                while (!Thread.currentThread().isInterrupted() && (retryObj = retrys.take()) != null) {
                    final DelayRetryable delayRetryable = retryObj;
                    pool.execute(() -> {
                        try {
                            delayRetryable.retry();
                            if (log.isDebugEnabled()) {
                                log.debug("retryObj : {}", delayRetryable);
                            }
                        } catch (Exception e) {
                            log.error("retry-dispatcher error!", e);
                        }
                    });
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("retry-dispatcher error!", e);
            }
        }, "retry-dispatcher");
        dispatcher.setDaemon(true);
        log.info("EventMesh retryer inited......");
    }

    public void shutdown() {
        dispatcher.interrupt();
        pool.shutdown();
        log.info("EventMesh retryer shutdown......");
    }

    public void start() throws Exception {
        dispatcher.start();
        log.info("EventMesh retryer started......");
    }

    /**
     * Get fail-retry queue, this method is just used for metrics.
     */
    public DelayQueue<DelayRetryable> getRetryQueue() {
        return retrys;
    }
}
