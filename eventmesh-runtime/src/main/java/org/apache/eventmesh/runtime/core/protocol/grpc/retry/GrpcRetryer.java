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
import org.apache.eventmesh.runtime.core.protocol.AbstractRetryer;
import org.apache.eventmesh.runtime.core.protocol.DelayRetryable;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GrpcRetryer extends AbstractRetryer {

    private final EventMeshGrpcConfiguration grpcConfiguration;

    public GrpcRetryer(EventMeshGrpcServer eventMeshGrpcServer) {
        this.grpcConfiguration = eventMeshGrpcServer.getEventMeshGrpcConfiguration();
    }

    @Override
    public void pushRetry(DelayRetryable delayRetryable) {
        if (retrys.size() >= grpcConfiguration.getEventMeshServerRetryBlockQueueSize()) {
            log.error("[RETRY-QUEUE] is full!");
            return;
        }
        retrys.offer(delayRetryable);
    }

    @Override
    public void init() {
        pool = new ThreadPoolExecutor(
            grpcConfiguration.getEventMeshServerRetryThreadNum(),
            grpcConfiguration.getEventMeshServerRetryThreadNum(), 60000, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(grpcConfiguration.getEventMeshServerRetryBlockQueueSize()),
            new EventMeshThreadFactory("grpc-retry", true, Thread.NORM_PRIORITY),
            new ThreadPoolExecutor.AbortPolicy());

        initDispatcher();
    }

}
