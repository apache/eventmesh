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

import org.apache.eventmesh.common.ThreadUtil;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractRemotingServer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public EventLoopGroup bossGroup;

    public EventLoopGroup ioGroup;

    public EventLoopGroup workerGroup;

    public int port;

    private EventLoopGroup initBossGroup(String threadPrefix) {
        bossGroup = new NioEventLoopGroup(1, new ThreadFactory() {
            AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, threadPrefix + "-boss-" + count.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });

        return bossGroup;
    }

    private EventLoopGroup initIOGroup(String threadPrefix) {
        ioGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, threadPrefix + "-io-" + count.incrementAndGet());
                return t;
            }
        });
        return ioGroup;
    }

    private EventLoopGroup initWokerGroup(String threadPrefix) {
        workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, threadPrefix + "-worker-" + count.incrementAndGet());
                return t;
            }
        });
        return workerGroup;
    }

    public void init(String threadPrefix) throws Exception {
        initBossGroup(threadPrefix);
        initIOGroup(threadPrefix);
        initWokerGroup(threadPrefix);
    }

    public void shutdown() throws Exception {
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
    }

    public void start() throws Exception {

    }
}
