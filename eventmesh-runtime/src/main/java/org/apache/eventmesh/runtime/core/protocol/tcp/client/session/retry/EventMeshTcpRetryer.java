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

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.AbstractRetryer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.core.retry.core.RetryContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.ThreadPoolHelper;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshTcpRetryer extends AbstractRetryer {

    private EventMeshTCPServer eventMeshTCPServer;

    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(3,
        3,
        60000,
        TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1000),
        new EventMeshThreadFactory("eventMesh-tcp-retry", true),
        new ThreadPoolExecutor.AbortPolicy());

    public EventMeshTcpRetryer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public void setEventMeshTCPServer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void pushRetry(RetryContext retryContext) {
        if (retrys.size() >= eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshServerRetryBlockQSize()) {
            log.error("pushRetry fail, retrys is too much,allow max retryQueueSize:{}, retryTimes:{}, seq:{}, bizSeq:{}",
                eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshServerRetryBlockQSize(), retryContext.retryTimes,
                retryContext.seq, EventMeshUtil.getMessageBizSeq(retryContext.event));
            return;
        }

        int maxRetryTimes = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpMsgAsyncRetryTimes();
        if (retryContext instanceof DownStreamMsgContext) {
            DownStreamMsgContext downStreamMsgContext = (DownStreamMsgContext) retryContext;
            maxRetryTimes = SubscriptionType.SYNC == downStreamMsgContext.getSubscriptionItem().getType()
                ? eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpMsgSyncRetryTimes() :
                eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpMsgAsyncRetryTimes();
        }

        if (retryContext.retryTimes >= maxRetryTimes) {
            log.warn("pushRetry fail,retry over maxRetryTimes:{}, retryTimes:{}, seq:{}, bizSeq:{}", maxRetryTimes,
                retryContext.retryTimes, retryContext.seq, EventMeshUtil.getMessageBizSeq(retryContext.event));
            return;
        }

        retrys.offer(retryContext);
        log.info("pushRetry success,seq:{}, retryTimes:{}, bizSeq:{}", retryContext.seq, retryContext.retryTimes,
            EventMeshUtil.getMessageBizSeq(retryContext.event));
    }

    @Override
    public void init() {
        initDispatcher();
    }

    public void printRetryThreadPoolState() {
        ThreadPoolHelper.printState(pool);
    }
}
