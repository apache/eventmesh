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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.session.retry;

import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.util.EventMeshThreadFactoryImpl;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshTcpRetryer {

    public static Logger logger = LoggerFactory.getLogger(EventMeshTcpRetryer.class);

    private EventMeshTCPServer eventMeshTCPServer;

    private DelayQueue<RetryContext> retrys = new DelayQueue<RetryContext>();

    private ThreadPoolExecutor pool = new ThreadPoolExecutor(3,
            3,
            60000,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1000),
            new EventMeshThreadFactoryImpl("eventMesh-tcp-retry", true),
            new ThreadPoolExecutor.AbortPolicy());

    private Thread dispatcher;

    public EventMeshTcpRetryer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public void setEventMeshTCPServer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public void pushRetry(RetryContext retryContext) {
        if (retrys.size() >= eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpMsgRetryQueueSize) {
            logger.error("pushRetry fail,retrys is too much,allow max retryQueueSize:{}, retryTimes:{}, seq:{}, bizSeq:{}",
                    eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpMsgRetryQueueSize, retryContext.retryTimes,
                    retryContext.seq, EventMeshUtil.getMessageBizSeq(retryContext.event));
            return;
        }

        int maxRetryTimes = eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpMsgAsyncRetryTimes;
        if (retryContext instanceof DownStreamMsgContext) {
            DownStreamMsgContext downStreamMsgContext = (DownStreamMsgContext) retryContext;
            maxRetryTimes = SubscriptionType.SYNC.equals(downStreamMsgContext.subscriptionItem.getType())
                    ? eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpMsgSyncRetryTimes :
                    eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshTcpMsgAsyncRetryTimes;
        }

        if (retryContext.retryTimes >= maxRetryTimes) {
            logger.warn("pushRetry fail,retry over maxRetryTimes:{}, retryTimes:{}, seq:{}, bizSeq:{}", maxRetryTimes,
                    retryContext.retryTimes, retryContext.seq, EventMeshUtil.getMessageBizSeq(retryContext.event));
            return;
        }

        retrys.offer(retryContext);
        logger.info("pushRetry success,seq:{}, retryTimes:{}, bizSeq:{}", retryContext.seq, retryContext.retryTimes,
                EventMeshUtil.getMessageBizSeq(retryContext.event));
    }

    public void init() {
        dispatcher = new Thread(() -> {
            try {
                RetryContext retryContext;
                while ((retryContext = retrys.take()) != null) {
                    pool.execute(retryContext::retry);
                }
            } catch (Exception e) {
                logger.error("retry-dispatcher error!", e);
            }
        }, "retry-dispatcher");
        dispatcher.setDaemon(true);
        logger.info("EventMeshTcpRetryer inited......");
    }

    public void start() throws Exception {
        dispatcher.start();
        logger.info("EventMeshTcpRetryer started......");
    }

    public void shutdown() {
        pool.shutdown();
        logger.info("EventMeshTcpRetryer shutdown......");
    }

    public int getRetrySize() {
        return retrys.size();
    }

    public void printRetryThreadPoolState() {
        //ThreadPoolHelper.printState(pool);
    }
}
