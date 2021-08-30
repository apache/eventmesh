/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.server.tcp;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.utils.ThreadUtil;
import org.apache.eventmesh.server.api.EventMeshServer;
import org.apache.eventmesh.server.api.exception.EventMeshServerException;
import org.apache.eventmesh.server.tcp.config.EventMeshTCPConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

public abstract class AbstractEventMeshTCPServer implements EventMeshServer {
    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public EventLoopGroup bossGroup;

    public EventLoopGroup ioGroup;

    public EventLoopGroup workerGroup;

    private ScheduledExecutorService scheduler;

    private ExecutorService taskHandleExecutorService;

    private ExecutorService broadcastMsgDownstreamExecutorService;

    public int port;

    public AbstractEventMeshTCPServer(int port) {
        this.port = port;
    }

    public void init() throws EventMeshServerException {
        bossGroup = new NioEventLoopGroup(
                1,
                ThreadUtil.createThreadFactory(true, "eventMesh-tcp-boss-")
        );
        ioGroup = new NioEventLoopGroup(
                Runtime.getRuntime().availableProcessors(),
                ThreadUtil.createThreadFactory(false, "eventMesh-tcp-io-")
        );
        workerGroup = new NioEventLoopGroup(
                Runtime.getRuntime().availableProcessors(),
                ThreadUtil.createThreadFactory(false, "eventMesh-tcp-worker-")
        );
        scheduler = ThreadPoolFactory.createScheduledExecutor(EventMeshTCPConfiguration.eventMeshTcpGlobalScheduler,
                ThreadUtil.createThreadFactory(true, "eventMesh-tcp-scheduler"));

        taskHandleExecutorService = ThreadPoolFactory.createThreadPoolExecutor(EventMeshTCPConfiguration.eventMeshTcpTaskHandleExecutorPoolSize,
                EventMeshTCPConfiguration.eventMeshTcpTaskHandleExecutorPoolSize, new LinkedBlockingQueue<>(10000),
                ThreadUtil.createThreadFactory(true, "eventMesh-tcp-task-handle"));

        broadcastMsgDownstreamExecutorService = ThreadPoolFactory.createThreadPoolExecutor(EventMeshTCPConfiguration.eventMeshTcpMsgDownStreamExecutorPoolSize,
                EventMeshTCPConfiguration.eventMeshTcpMsgDownStreamExecutorPoolSize, new LinkedBlockingQueue<>(10000),
                ThreadUtil.createThreadFactory(true, "eventMesh-tcp-msg-downstream"));
    }

    public void start() throws EventMeshServerException {

    }

    public void shutdown() throws EventMeshServerException {
        try {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
                logger.info("shutdown bossGroup");
            }

            ThreadUtil.randomSleep(30);

            if (ioGroup != null) {
                ioGroup.shutdownGracefully();
                logger.info("shutdown ioGroup");
            }

            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
                logger.info("shutdown workerGroup");
            }
            if (scheduler != null) {
                scheduler.shutdown();
            }
            if (taskHandleExecutorService != null) {
                taskHandleExecutorService.shutdown();
            }
            if (broadcastMsgDownstreamExecutorService != null) {
                broadcastMsgDownstreamExecutorService.shutdown();
            }
        } catch (Exception ex) {
            throw new EventMeshServerException(ex);
        }
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public ExecutorService getTaskHandleExecutorService() {
        return taskHandleExecutorService;
    }

    public ExecutorService getBroadcastMsgDownstreamExecutorService() {
        return broadcastMsgDownstreamExecutorService;
    }
}
