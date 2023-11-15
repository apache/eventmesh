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
import org.apache.eventmesh.retry.api.AbstractRetryer;
import org.apache.eventmesh.retry.api.timer.TimerTask;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.RetryContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpRetryer extends AbstractRetryer {

    private EventMeshTCPServer eventMeshTCPServer;

    public TcpRetryer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public void setEventMeshTCPServer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void newTimeout(TimerTask timerTask, long delay, TimeUnit timeUnit) {
        RetryContext retryContext = (RetryContext) timerTask;

        int maxRetryTimes = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpMsgAsyncRetryTimes();
        if (retryContext instanceof DownStreamMsgContext) {
            DownStreamMsgContext downStreamMsgContext = (DownStreamMsgContext) retryContext;
            maxRetryTimes = SubscriptionType.SYNC == downStreamMsgContext.getSubscriptionItem().getType()
                ? eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpMsgSyncRetryTimes()
                : eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpMsgAsyncRetryTimes();
        }

        if (retryContext.retryTimes >= maxRetryTimes) {
            log.warn("pushRetry fail,retry over maxRetryTimes:{}, retryTimes:{}, seq:{}, bizSeq:{}", maxRetryTimes,
                retryContext.retryTimes, retryContext.seq, EventMeshUtil.getMessageBizSeq(retryContext.event));
            return;
        }

        super.newTimeout(timerTask, delay, timeUnit);

        log.info("pushRetry success,seq:{}, retryTimes:{}, bizSeq:{}", retryContext.seq, retryContext.retryTimes,
            EventMeshUtil.getMessageBizSeq(retryContext.event));

    }
}
