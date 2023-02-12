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

package org.apache.eventmesh.runtime.core.protocol.grpc.retry;

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcRetryer {

    private final Logger retryLogger = LoggerFactory.getLogger("retry");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final EventMeshGrpcConfiguration grpcConfiguration;

    public GrpcRetryer(EventMeshGrpcServer eventMeshGrpcServer) {
        this.grpcConfiguration = eventMeshGrpcServer.getEventMeshGrpcConfiguration();
    }

    private final DelayQueue<DelayRetryable> failed = new DelayQueue<DelayRetryable>();

    private ThreadPoolExecutor pool;

    private Thread dispatcher;

    public void pushRetry(DelayRetryable delayRetryable) {
        if (failed.size() >= grpcConfiguration.getEventMeshServerRetryBlockQueueSize()) {
            retryLogger.error("[RETRY-QUEUE] is full!");
            return;
        }
        failed.offer(delayRetryable);
    }

    public void init() {
        pool = new ThreadPoolExecutor(
            grpcConfiguration.getEventMeshServerRetryThreadNum(),
            grpcConfiguration.getEventMeshServerRetryThreadNum(), 60000, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(grpcConfiguration.getEventMeshServerRetryBlockQueueSize()),
            new EventMeshThreadFactory("grpc-retry", true, Thread.NORM_PRIORITY),
            new ThreadPoolExecutor.AbortPolicy());

        dispatcher = new Thread(() -> {
            try {
                DelayRetryable retryObj = null;
                while (!Thread.currentThread().isInterrupted()
                    && (retryObj = failed.take()) != null) {
                    final DelayRetryable delayRetryable = retryObj;
                    pool.execute(() -> {
                        try {
                            delayRetryable.retry();
                            if (retryLogger.isDebugEnabled()) {
                                retryLogger.debug("retryObj : {}", delayRetryable);
                            }
                        } catch (Exception e) {
                            retryLogger.error("grpc-retry-dispatcher error!", e);
                        }
                    });
                }
            } catch (Exception e) {
                retryLogger.error("grpc-retry-dispatcher error!", e);
            }
        }, "grpc-retry-dispatcher");
        dispatcher.setDaemon(true);
        logger.info("GrpcRetryer inited......");
    }

    public int size() {
        return failed.size();
    }

    public void shutdown() {
        dispatcher.interrupt();
        pool.shutdown();
        logger.info("GrpcRetryer shutdown......");
    }

    public void start() throws Exception {
        dispatcher.start();
        logger.info("GrpcRetryer started......");
    }
}
