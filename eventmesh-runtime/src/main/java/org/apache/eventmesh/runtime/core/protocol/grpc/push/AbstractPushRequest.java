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

package org.apache.eventmesh.runtime.core.protocol.grpc.push;

import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.GrpcRetryer;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.RetryContext;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractPushRequest extends RetryContext {

    protected EventMeshGrpcServer eventMeshGrpcServer;
    protected long createTime = System.currentTimeMillis();
    protected long lastPushTime = System.currentTimeMillis();

    protected EventMeshGrpcConfiguration eventMeshGrpcConfiguration;
    protected GrpcRetryer retryer;
    protected int ttl;
    protected HandleMsgContext handleMsgContext;
    private AtomicBoolean complete = new AtomicBoolean(Boolean.FALSE);

    public AbstractPushRequest(HandleMsgContext handleMsgContext) {
        this.eventMeshGrpcServer = handleMsgContext.getEventMeshGrpcServer();
        this.handleMsgContext = handleMsgContext;

        this.eventMeshGrpcConfiguration = handleMsgContext.getEventMeshGrpcServer().getEventMeshGrpcConfiguration();
        this.retryer = handleMsgContext.getEventMeshGrpcServer().getGrpcRetryer();
        this.ttl = handleMsgContext.getTtl();
    }

    public abstract void tryPushRequest();

    public void delayRetry() {
        if (retryTimes < EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES) {
            retryTimes++;
            delay(retryTimes * EventMeshConstants.DEFAULT_PUSH_RETRY_TIME_DISTANCE_IN_MILLSECONDS);
            retryer.pushRetry(this);
        } else {
            complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
        }
    }

    public boolean isComplete() {
        return complete.get();
    }

    public void complete() {
        complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
    }

    public void timeout() {
        if (!isComplete() && System.currentTimeMillis() - lastPushTime >= ttl) {
            delayRetry();
        }
    }

    public HandleMsgContext getHandleMsgContext() {
        return handleMsgContext;
    }
}