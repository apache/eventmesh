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

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventWrapper;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.RetryContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.GrpcRetryer;
import org.apache.eventmesh.runtime.core.timer.Timeout;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.Sets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractPushRequest extends RetryContext {

    protected EventMeshGrpcServer eventMeshGrpcServer;
    protected long createTime = System.currentTimeMillis();
    protected long lastPushTime = System.currentTimeMillis();

    protected EventMeshConsumer eventMeshConsumer;
    protected EventMeshGrpcConfiguration eventMeshGrpcConfiguration;
    protected GrpcRetryer grpcRetryer;

    protected Map<String, Set<AbstractPushRequest>> waitingRequests;

    protected HandleMsgContext handleMsgContext;
    // protected CloudEvent event;
    protected CloudEvent eventMeshCloudEvent;

    private final AtomicBoolean complete = new AtomicBoolean(Boolean.FALSE);

    public AbstractPushRequest(HandleMsgContext handleMsgContext, Map<String, Set<AbstractPushRequest>> waitingRequests) {
        this.eventMeshGrpcServer = handleMsgContext.getEventMeshGrpcServer();
        this.handleMsgContext = handleMsgContext;
        this.waitingRequests = waitingRequests;

        this.eventMeshConsumer = handleMsgContext.getEventMeshConsumer();
        this.eventMeshGrpcConfiguration = handleMsgContext.getEventMeshGrpcServer().getEventMeshGrpcConfiguration();
        this.grpcRetryer = handleMsgContext.getEventMeshGrpcServer().getGrpcRetryer();
        io.cloudevents.CloudEvent event = handleMsgContext.getEvent();
        this.eventMeshCloudEvent = getEventMeshCloudEvent(event);
    }

    public abstract void tryPushRequest();

    private CloudEvent getEventMeshCloudEvent(io.cloudevents.CloudEvent cloudEvent) {
        try {
            String protocolType = Objects.requireNonNull(cloudEvent.getExtension(Constants.PROTOCOL_TYPE)).toString();
            ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
            ProtocolTransportObject protocolTransportObject = protocolAdaptor.fromCloudEvent(cloudEvent);
            return ((EventMeshCloudEventWrapper) protocolTransportObject).getMessage();
        } catch (Exception e) {
            log.error("Error in getting EventMeshMessage from CloudEvent", e);
            return null;
        }
    }

    private io.cloudevents.CloudEvent getCloudEvent(CloudEvent cloudEvent) {
        try {
            String protocolType = Objects.requireNonNull(EventMeshCloudEventUtils.getProtocolType(cloudEvent));
            ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
            return protocolAdaptor.toCloudEvent(new EventMeshCloudEventWrapper(cloudEvent));
        } catch (Exception e) {
            log.error("Error in getting CloudEvent from EventMeshMessage", e);
            return null;
        }
    }

    protected void delayRetry() {
        if (retryTimes < EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES) {
            retryTimes++;
            grpcRetryer.newTimeout(this, EventMeshConstants.DEFAULT_PUSH_RETRY_TIME_DISTANCE_IN_MILLSECONDS, TimeUnit.MILLISECONDS);
        } else {
            complete();
        }
    }

    protected boolean isComplete() {
        return complete.get();
    }

    private void finish() {
        AbstractContext context = handleMsgContext.getContext();
        SubscriptionMode subscriptionMode = handleMsgContext.getSubscriptionMode();
        io.cloudevents.CloudEvent event = getCloudEvent(eventMeshCloudEvent);
        if (eventMeshConsumer != null && context != null && event != null) {
            try {
                eventMeshConsumer.updateOffset(subscriptionMode, Collections.singletonList(event), context);
            } catch (Exception e) {
                log.error("Error in updating offset in EventMeshConsumer", e);
            }
        }
    }

    protected void complete() {
        complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
        finish();
    }

    protected void timeout() {
        if (!isComplete() && System.currentTimeMillis() - lastPushTime >= Long.parseLong(EventMeshCloudEventUtils.getTtl(eventMeshCloudEvent))) {
            delayRetry();
        }
    }

    public HandleMsgContext getHandleMsgContext() {
        return handleMsgContext;
    }

    protected void addToWaitingMap(WebhookPushRequest request) {
        if (waitingRequests.containsKey(handleMsgContext.getConsumerGroup())) {
            waitingRequests.get(handleMsgContext.getConsumerGroup()).add(request);
            return;
        }
        waitingRequests.put(handleMsgContext.getConsumerGroup(), Sets.newConcurrentHashSet());
        waitingRequests.get(handleMsgContext.getConsumerGroup()).add(request);
    }

    protected void removeWaitingMap(WebhookPushRequest request) {
        if (waitingRequests.containsKey(handleMsgContext.getConsumerGroup())) {
            waitingRequests.get(handleMsgContext.getConsumerGroup()).remove(request);
        }
    }

    @Override
    public void doRun(Timeout timeout) throws Exception {
        tryPushRequest();
    }
}
