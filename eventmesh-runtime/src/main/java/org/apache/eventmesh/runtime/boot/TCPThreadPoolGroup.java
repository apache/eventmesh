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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

public class TCPThreadPoolGroup implements ThreadPoolGroup {

    private final EventMeshTCPConfiguration eventMeshTCPConfiguration;
    private ScheduledExecutorService scheduler;
    private ThreadPoolExecutor taskHandleExecutorService;
    private ThreadPoolExecutor broadcastMsgDownstreamExecutorService;

    public TCPThreadPoolGroup(EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
    }

    @Override
    public void initThreadPool() {

        scheduler = ThreadPoolFactory.createScheduledExecutor(eventMeshTCPConfiguration.getEventMeshTcpGlobalScheduler(),
            new EventMeshThreadFactory("eventMesh-tcp-scheduler", true));

        taskHandleExecutorService = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshTCPConfiguration.getEventMeshTcpTaskHandleExecutorPoolSize(),
            eventMeshTCPConfiguration.getEventMeshTcpTaskHandleExecutorPoolSize(),
            new LinkedBlockingQueue<>(10_000),
            new EventMeshThreadFactory("eventMesh-tcp-task-handle", true));

        broadcastMsgDownstreamExecutorService = ThreadPoolFactory.createThreadPoolExecutor(
            eventMeshTCPConfiguration.getEventMeshTcpMsgDownStreamExecutorPoolSize(),
            eventMeshTCPConfiguration.getEventMeshTcpMsgDownStreamExecutorPoolSize(),
            new LinkedBlockingQueue<>(10_000),
            new EventMeshThreadFactory("eventMesh-tcp-msg-downstream", true));
    }

    @Override
    public void shutdownThreadPool() {
        scheduler.shutdown();
        taskHandleExecutorService.shutdown();
        broadcastMsgDownstreamExecutorService.shutdown();
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public ThreadPoolExecutor getTaskHandleExecutorService() {
        return taskHandleExecutorService;
    }

    public ThreadPoolExecutor getBroadcastMsgDownstreamExecutorService() {
        return broadcastMsgDownstreamExecutorService;
    }
}