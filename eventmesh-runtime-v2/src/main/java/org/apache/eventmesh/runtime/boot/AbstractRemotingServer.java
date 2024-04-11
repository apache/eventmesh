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
import org.apache.eventmesh.common.utils.SystemUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import lombok.extern.slf4j.Slf4j;

/**
 * The most basic server
 */
@Slf4j
public abstract class AbstractRemotingServer implements RemotingServer {

    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_SLEEP_SECONDS = 30;

    private EventLoopGroup bossGroup;
    private EventLoopGroup ioGroup;
    private EventExecutorGroup workerGroup;
    protected ProducerManager producerManager;

    private int port;

    protected void buildBossGroup(final String threadPrefix) {
        if (useEpoll()) {
            bossGroup = new EpollEventLoopGroup(1, new EventMeshThreadFactory(threadPrefix + "NettyEpoll-Boss", true));
        } else {
            bossGroup = new NioEventLoopGroup(1, new EventMeshThreadFactory(threadPrefix + "NettyNio-Boss", true));
        }

    }

    private void buildIOGroup(final String threadPrefix) {
        if (useEpoll()) {
            ioGroup = new EpollEventLoopGroup(MAX_THREADS, new EventMeshThreadFactory(threadPrefix + "-NettyEpoll-IO"));
        } else {
            ioGroup = new NioEventLoopGroup(MAX_THREADS, new EventMeshThreadFactory(threadPrefix + "-NettyNio-IO"));
        }
    }

    private void buildWorkerGroup(final String threadPrefix) {
        workerGroup = new NioEventLoopGroup(MAX_THREADS, new EventMeshThreadFactory(threadPrefix + "-worker"));
    }

    protected void initProducerManager() throws Exception {
        producerManager = new ProducerManager(this);
        producerManager.init();
    }

    public ProducerManager getProducerManager() {
        return producerManager;
    }

    public void init(final String threadPrefix) throws Exception {
        buildBossGroup(threadPrefix);
        buildIOGroup(threadPrefix);
        buildWorkerGroup(threadPrefix);
    }

    public void start() throws Exception {
        producerManager.start();
    }

    public void shutdown() throws Exception {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            log.info("shutdown bossGroup");
        }
        if (Objects.isNull(producerManager)) {
            producerManager.shutdown();
        }
        ThreadUtils.randomPause(TimeUnit.SECONDS.toMillis(DEFAULT_SLEEP_SECONDS));

        if (ioGroup != null) {
            ioGroup.shutdownGracefully();
            log.info("shutdown ioGroup");
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();

            log.info("shutdown workerGroup");
        }
    }

    protected boolean useEpoll() {
        return SystemUtils.isLinuxPlatform() && Epoll.isAvailable();
    }

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public void setBossGroup(final EventLoopGroup bossGroup) {
        this.bossGroup = bossGroup;
    }

    public EventLoopGroup getIoGroup() {
        return ioGroup;
    }

    public void setIoGroup(final EventLoopGroup ioGroup) {
        this.ioGroup = ioGroup;
    }

    public EventExecutorGroup getWorkerGroup() {
        return workerGroup;
    }

    public void setWorkerGroup(final EventExecutorGroup workerGroup) {
        this.workerGroup = workerGroup;
    }

    public int getPort() {
        return port;
    }

    public void setPort(final int port) {
        this.port = port;
    }
}
