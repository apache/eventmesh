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

package org.apache.eventmesh.runtime.core.protocol.http.retry;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRetryer {

    private Logger retryLogger = LoggerFactory.getLogger("retry");

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private EventMeshHTTPServer eventMeshHTTPServer;

    public HttpRetryer(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    private DelayQueue<DelayRetryable> failed = new DelayQueue<DelayRetryable>();

    private ThreadPoolExecutor pool;

    private Thread dispatcher;

    public void pushRetry(DelayRetryable delayRetryable) {
        if (failed.size() >= eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerRetryBlockQSize) {
            retryLogger.error("[RETRY-QUEUE] is full!");
            return;
        }
        failed.offer(delayRetryable);
    }

    public void init() {
        pool = new ThreadPoolExecutor(eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerRetryThreadNum,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerRetryThreadNum,
                60000,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerRetryBlockQSize),
                new ThreadFactory() {
                    private AtomicInteger count = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "http-retry-" + count.incrementAndGet());
                        thread.setPriority(Thread.NORM_PRIORITY);
                        thread.setDaemon(true);
                        return thread;
                    }
                }, new ThreadPoolExecutor.AbortPolicy());

        dispatcher = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DelayRetryable retryObj = null;
                    while (!Thread.currentThread().isInterrupted() && (retryObj = failed.take()) != null) {
                        final DelayRetryable delayRetryable = retryObj;
                        pool.execute(() -> {
                            try {
                                delayRetryable.retry();
                                if (retryLogger.isDebugEnabled()) {
                                    retryLogger.debug("retryObj : {}", delayRetryable);
                                }
                            } catch (Exception e) {
                                retryLogger.error("http-retry-dispatcher error!", e);
                            }
                        });
                    }
                } catch (Exception e) {
                    retryLogger.error("http-retry-dispatcher error!", e);
                }
            }
        }, "http-retry-dispatcher");
        dispatcher.setDaemon(true);
        logger.info("HttpRetryer inited......");
    }

    public int size() {
        return failed.size();
    }

    public void shutdown() {
        dispatcher.interrupt();
        pool.shutdown();
        logger.info("HttpRetryer shutdown......");
    }

    public void start() throws Exception {
        dispatcher.start();
        logger.info("HttpRetryer started......");
    }
}
