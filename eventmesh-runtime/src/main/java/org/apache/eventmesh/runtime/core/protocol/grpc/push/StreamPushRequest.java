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

import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.StreamTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StreamPushRequest extends AbstractPushRequest {

    private final StreamTopicConfig topicConfig;

    private final SubscriptionMode subscriptionMode;

    private final int startIdx;

    private final List<BroadcastTarget> pendingBroadcastTargets = new ArrayList<>();

    private boolean broadcastInitialized;

    private boolean nackPending;

    public StreamPushRequest(HandleMsgContext handleMsgContext, Map<String, Set<AbstractPushRequest>> waitingRequests) {
        super(handleMsgContext, waitingRequests);

        this.topicConfig = (StreamTopicConfig) handleMsgContext.getConsumeTopicConfig();
        this.subscriptionMode = topicConfig.getSubscriptionMode();
        this.startIdx = RandomUtils.nextInt(0, Integer.MAX_VALUE);
    }

    @Override
    public synchronized void tryPushRequest() {
        if (isComplete()) {
            return;
        }
        if (nackPending) {
            tryNack();
            return;
        }
        if (eventMeshCloudEvent == null) {
            if (supportsAsyncNack()) {
                onRetriesExhausted();
            }
            return;
        }

        if (subscriptionMode == SubscriptionMode.BROADCASTING) {
            tryPushBroadcast();
            return;
        }

        List<EventEmitter<CloudEvent>> eventEmitters = selectEmitter();

        if (eventEmitters.isEmpty()) {
            if (supportsAsyncNack()) {
                delayRetry();
            }
            return;
        }

        boolean retryNeeded = false;
        for (EventEmitter<CloudEvent> eventEmitter : eventEmitters) {
            if (eventEmitter.isClosed() || !trySend(eventEmitter)) {
                retryNeeded = true;
            }
        }
        if (retryNeeded) {
            delayRetry();
        } else {
            complete();
        }
    }

    private void tryPushBroadcast() {
        boolean firstAttempt = !broadcastInitialized;
        if (!broadcastInitialized) {
            List<EventEmitter<CloudEvent>> selected = selectEmitter();
            if (selected.isEmpty()) {
                return;
            }
            for (EventEmitter<CloudEvent> emitter : selected) {
                pendingBroadcastTargets.add(new BroadcastTarget(topicConfig.getEmitterKey(emitter), emitter));
            }
            broadcastInitialized = true;
        }

        if (!firstAttempt && topicConfig.getSize() == 0) {
            // Removing the last subscriber restarts the broker consumer; its pending offset is replayed on subscription.
            completeWithoutOffsetUpdate();
            return;
        }

        Iterator<BroadcastTarget> iterator = pendingBroadcastTargets.iterator();
        while (iterator.hasNext()) {
            BroadcastTarget target = iterator.next();
            EventEmitter<CloudEvent> emitter = target.originalEmitter;
            if (!firstAttempt && target.key != null && topicConfig.getEmitterByKey(target.key) != emitter) {
                // Neither a matching IP/PID nor a new PID proves that this is the same stream.
                // Replay from the broker rather than acknowledging delivery to a different client.
                if (!recoverBroadcast()) {
                    delayRetry();
                }
                return;
            }
            if (emitter != null && !emitter.isClosed() && trySend(emitter)) {
                iterator.remove();
            }
        }
        if (pendingBroadcastTargets.isEmpty()) {
            complete();
        } else {
            delayRetry();
        }
    }

    private boolean trySend(EventEmitter<CloudEvent> eventEmitter) {
        this.lastPushTime = System.currentTimeMillis();
        eventMeshCloudEvent = CloudEvent.newBuilder(eventMeshCloudEvent)
            .putAttributes(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP,
                CloudEventAttributeValue.newBuilder().setCeString(String.valueOf(lastPushTime)).build())
            .build();
        try {
            if (!eventEmitter.tryOnNext(eventMeshCloudEvent)) {
                return false;
            }
            long cost = System.currentTimeMillis() - lastPushTime;
            log.info("message|eventMesh2client|emitter|topic={}|bizSeqNo={}" + "|uniqueId={}|cost={}",
                EventMeshCloudEventUtils.getSubject(eventMeshCloudEvent), EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent),
                EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent), cost);
            return true;
        } catch (Throwable t) {
            eventEmitter.onCompleted();
            try {
                eventMeshGrpcServer.getConsumerManager().deregisterClientsByEmitter(eventEmitter);
            } catch (RuntimeException e) {
                log.error("Failed to deregister broken gRPC stream", e);
            }
            long cost = System.currentTimeMillis() - lastPushTime;
            log.error("message|eventMesh2client|exception={} |emitter|topic={}|bizSeqNo={}" + "|uniqueId={}|cost={}",
                t.getMessage(), EventMeshCloudEventUtils.getSubject(eventMeshCloudEvent), EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent),
                EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent), cost, t);
            return false;
        }
    }

    private List<EventEmitter<CloudEvent>> selectEmitter() {
        List<EventEmitter<CloudEvent>> emitterList = MapUtils.getObject(topicConfig.getIdcEmitters(),
            eventMeshGrpcConfiguration.getEventMeshIDC(), null);
        if (CollectionUtils.isNotEmpty(emitterList)) {
            List<EventEmitter<CloudEvent>> activeLocalEmitters = getEventEmitters(emitterList);
            if (CollectionUtils.isNotEmpty(activeLocalEmitters)) {
                return activeLocalEmitters;
            }
        }

        if (CollectionUtils.isNotEmpty(topicConfig.getTotalEmitters())) {
            return getEventEmitters(topicConfig.getTotalEmitters());
        }

        log.error("No event emitters from subscriber, no message returning.");
        return Collections.emptyList();
    }

    private List<EventEmitter<CloudEvent>> getEventEmitters(List<EventEmitter<CloudEvent>> emitterList) {
        if (subscriptionMode == SubscriptionMode.BROADCASTING) {
            // Include streams closed during selection so their delivery remains pending.
            return new ArrayList<>(emitterList);
        }
        List<EventEmitter<CloudEvent>> activeEmitters = new ArrayList<>();
        for (EventEmitter<CloudEvent> emitter : emitterList) {
            if (!emitter.isClosed()) {
                activeEmitters.add(emitter);
            }
        }
        if (activeEmitters.isEmpty()) {
            return Collections.emptyList();
        }
        switch (subscriptionMode) {
            case CLUSTERING:
                int index = (startIdx % activeEmitters.size() + retryTimes) % activeEmitters.size();
                return Collections.singletonList(activeEmitters.get(index));
            default:
                log.error("Invalid Subscription Mode, no message returning back to subscriber.");
                return Collections.emptyList();
        }
    }

    private boolean supportsAsyncNack() {
        return subscriptionMode == SubscriptionMode.CLUSTERING
            && handleMsgContext.getAsyncConsumeContext().supportsAsyncNack();
    }

    @Override
    protected void onRetriesExhausted() {
        if (subscriptionMode == SubscriptionMode.BROADCASTING && !pendingBroadcastTargets.isEmpty()) {
            retryTimes = 0;
            delayRetry();
            return;
        }
        if (!supportsAsyncNack()) {
            super.onRetriesExhausted();
            return;
        }
        nackPending = true;
        tryNack();
    }

    @Override
    protected void onRetryScheduleFailure(RuntimeException e) {
        if (subscriptionMode == SubscriptionMode.BROADCASTING && !pendingBroadcastTargets.isEmpty()) {
            log.error("Could not schedule pending broadcast delivery on the gRPC retry timer", e);
            recoverBroadcast();
            return;
        }
        if (!supportsAsyncNack()) {
            throw e;
        }
        log.error("Could not schedule gRPC stream push retry; redelivering message now", e);
        onRetriesExhausted();
    }

    private boolean recoverBroadcast() {
        if (eventMeshGrpcServer.getConsumerManager().recoverUnacknowledgedMessages(handleMsgContext.getConsumerGroup())) {
            completeWithoutOffsetUpdate();
            return true;
        }
        log.error("Could not schedule broadcast consumer recovery; original message remains unacknowledged");
        return false;
    }

    private void tryNack() {
        try {
            if (handleMsgContext.getAsyncConsumeContext().nack()) {
                completeWithoutOffsetUpdate();
                return;
            }
        } catch (RuntimeException e) {
            log.error("Failed to redeliver gRPC stream message", e);
        }
        try {
            grpcRetryer.newTimeout(this, EventMeshConstants.DEFAULT_PUSH_RETRY_TIME_DISTANCE_IN_MILLSECONDS,
                TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            log.error("Could not schedule redelivery; original message offset has not been advanced", e);
            if (eventMeshGrpcServer.getConsumerManager().recoverUnacknowledgedMessages(handleMsgContext.getConsumerGroup())) {
                completeWithoutOffsetUpdate();
            }
        }
    }

    private static final class BroadcastTarget {

        private final String key;

        private final EventEmitter<CloudEvent> originalEmitter;

        private BroadcastTarget(String key, EventEmitter<CloudEvent> originalEmitter) {
            this.key = key;
            this.originalEmitter = originalEmitter;
        }
    }
}
