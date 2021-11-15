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

package org.apache.eventmesh.client.http;

import org.apache.eventmesh.client.http.consumer.listener.LiteMessageListener;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;

public class RemotingServer {

    public static final Logger logger = LoggerFactory.getLogger(RemotingServer.class);

    public static final AtomicBoolean started = new AtomicBoolean(Boolean.FALSE);

    public static final AtomicBoolean inited = new AtomicBoolean(Boolean.FALSE);

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(false);

    private ThreadPoolExecutor consumeExecutor;

    private LiteMessageListener messageListener;

    public RemotingServer() {
    }

    public RemotingServer(ThreadPoolExecutor consumeExecutor) {
        this.consumeExecutor = consumeExecutor;
    }

    public void setConsumeExecutor(ThreadPoolExecutor consumeExecutor) {
        this.consumeExecutor = consumeExecutor;
    }

    // TODO: Let different topics have different listeners
    public void registerMessageListener(LiteMessageListener eventMeshMessageListener) {
        this.messageListener = eventMeshMessageListener;
    }

    private EventLoopGroup initBossGroup() {
        bossGroup = new NioEventLoopGroup(1, new ThreadFactory() {
            AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "endPointBoss-" + count.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });

        return bossGroup;
    }

    private EventLoopGroup initWorkerGroup() {
        workerGroup = new NioEventLoopGroup(2, new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("endpointWorker-")
            .build()
        );
        return workerGroup;
    }

    public void init() throws Exception {
        initBossGroup();
        initWorkerGroup();
        inited.compareAndSet(false, true);
    }

}
