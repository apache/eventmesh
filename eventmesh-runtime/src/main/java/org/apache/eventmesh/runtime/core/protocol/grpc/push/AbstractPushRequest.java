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

import com.google.common.collect.Sets;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.GrpcRetryer;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.RetryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractPushRequest extends RetryContext {

    private final Logger logger = LoggerFactory.getLogger(ConsumerGroupTopicConfig.class);

    protected EventMeshGrpcServer eventMeshGrpcServer;
    protected long createTime = System.currentTimeMillis();
    protected long lastPushTime = System.currentTimeMillis();

    protected EventMeshConsumer eventMeshConsumer;
    protected EventMeshGrpcConfiguration eventMeshGrpcConfiguration;
    protected GrpcRetryer grpcRetryer;

    protected Map<String, Set<AbstractPushRequest>> waitingRequests;

    protected HandleMsgContext handleMsgContext;
    protected CloudEvent event;
    protected String topic;
    protected String bizSeqNum;
    protected String uniqueId;
    protected int ttl;

    private final AtomicBoolean complete = new AtomicBoolean(Boolean.FALSE);

    public AbstractPushRequest(HandleMsgContext handleMsgContext, Map<String, Set<AbstractPushRequest>> waitingRequests) {
        this.eventMeshGrpcServer = handleMsgContext.getEventMeshGrpcServer();
        this.handleMsgContext = handleMsgContext;
        this.waitingRequests = waitingRequests;

        this.eventMeshGrpcConfiguration = handleMsgContext.getEventMeshGrpcServer().getEventMeshGrpcConfiguration();
        this.grpcRetryer = handleMsgContext.getEventMeshGrpcServer().getGrpcRetryer();

        this.event = CloudEventBuilder.from(handleMsgContext.getEvent())
            .withExtension(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP,
                String.valueOf(System.currentTimeMillis()))
            .build();

        this.topic = event.getSubject();

        Object objBizSeqNo = event.getExtension(Constants.PROPERTY_MESSAGE_SEARCH_KEYS);
        this.bizSeqNum = objBizSeqNo == null ? RandomStringUtils.generateNum(20) : objBizSeqNo.toString();

        Object objUniqueId = event.getExtension(Constants.RMB_UNIQ_ID);
        this.uniqueId = objUniqueId == null ? RandomStringUtils.generateNum(20) : objUniqueId.toString();

        String ttlStr = (String) event.getExtension(Constants.PROPERTY_MESSAGE_TIMEOUT);
        this.ttl = StringUtils.isNumeric(ttlStr) ? Integer.parseInt(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
    }

    public abstract void tryPushRequest();

    protected EventMeshMessage getEventMeshMessage(CloudEvent cloudEvent) {
        try {
            String protocolType = Objects.requireNonNull(cloudEvent.getExtension(Constants.PROTOCOL_TYPE)).toString();
            ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
            ProtocolTransportObject protocolTransportObject = protocolAdaptor.fromCloudEvent(cloudEvent);
            return ((EventMeshMessageWrapper) protocolTransportObject).getMessage();
        } catch (Exception e) {
            logger.error("Error in getting EventMeshMessage from CloudEvent", e);
            return null;
        }
    }

    @Override
    public boolean retry() {
        tryPushRequest();
        return true;
    }

    protected void delayRetry() {
        if (retryTimes < EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES) {
            retryTimes++;
            delay((long) retryTimes * EventMeshConstants.DEFAULT_PUSH_RETRY_TIME_DISTANCE_IN_MILLSECONDS);
            grpcRetryer.pushRetry(this);
        } else {
            complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
        }
    }

    protected boolean isComplete() {
        return complete.get();
    }

    protected void finish() {
        AbstractContext context = handleMsgContext.getContext();
        CloudEvent event = handleMsgContext.getEvent();
        SubscriptionMode subscriptionMode = handleMsgContext.getSubscriptionMode();
        if (eventMeshConsumer != null && context != null && event != null) {
            try {
                eventMeshConsumer.updateOffset(subscriptionMode, Collections.singletonList(event), context);
            } catch (Exception e) {
                logger.error("Error in updating offset in EventMeshConsumer", e);
            }
        }
    }

    protected void complete() {
        complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
    }

    protected void timeout() {
        if (!isComplete() && System.currentTimeMillis() - lastPushTime >= ttl) {
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
}