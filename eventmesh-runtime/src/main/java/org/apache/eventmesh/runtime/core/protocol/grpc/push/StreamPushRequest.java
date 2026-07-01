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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.grpc.stub.StreamObserver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StreamPushRequest extends AbstractPushRequest {

    private final Map<String, List<EventEmitter<CloudEvent>>> idcEmitters;

    private final List<EventEmitter<CloudEvent>> totalEmitters;

    private final SubscriptionMode subscriptionMode;

    private final int startIdx;

    public StreamPushRequest(HandleMsgContext handleMsgContext, Map<String, Set<AbstractPushRequest>> waitingRequests) {
        super(handleMsgContext, waitingRequests);

        StreamTopicConfig topicConfig = (StreamTopicConfig) handleMsgContext.getConsumeTopicConfig();
        this.idcEmitters = topicConfig.getIdcEmitters();
        this.totalEmitters = topicConfig.getTotalEmitters();
        this.subscriptionMode = topicConfig.getSubscriptionMode();
        this.startIdx = RandomUtils.nextInt(0, totalEmitters.size());
    }

    @Override
    public void tryPushRequest() {
        if (eventMeshCloudEvent == null) {
            return;
        }

        List<EventEmitter<CloudEvent>> eventEmitters = selectEmitter();

        for (EventEmitter<CloudEvent> eventEmitter : eventEmitters) {
            this.lastPushTime = System.currentTimeMillis();

            eventMeshCloudEvent = CloudEvent.newBuilder(eventMeshCloudEvent)
                .putAttributes(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP,
                    CloudEventAttributeValue.newBuilder().setCeString(String.valueOf(lastPushTime)).build())
                .build();
            try {
                // catch the error and retry, don't use eventEmitter.onNext() to hide the error
                StreamObserver<CloudEvent> emitter = eventEmitter.getEmitter();
                synchronized (emitter) {
                    emitter.onNext(eventMeshCloudEvent);
                }

                long cost = System.currentTimeMillis() - lastPushTime;
                log.info("message|eventMesh2client|emitter|topic={}|bizSeqNo={}" + "|uniqueId={}|cost={}",
                    EventMeshCloudEventUtils.getSubject(eventMeshCloudEvent), EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent),
                    EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent), cost);
                complete();
            } catch (Throwable t) {
                long cost = System.currentTimeMillis() - lastPushTime;
                log.error("message|eventMesh2client|exception={} |emitter|topic={}|bizSeqNo={}" + "|uniqueId={}|cost={}",
                    t.getMessage(), EventMeshCloudEventUtils.getSubject(eventMeshCloudEvent), EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent),
                    EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent), cost, t);

                delayRetry();
            }
        }
    }

    private List<EventEmitter<CloudEvent>> selectEmitter() {
        List<EventEmitter<CloudEvent>> emitterList = MapUtils.getObject(idcEmitters,
            eventMeshGrpcConfiguration.getEventMeshIDC(), null);
        if (CollectionUtils.isNotEmpty(emitterList)) {
            return getEventEmitters(emitterList);
        }

        if (CollectionUtils.isNotEmpty(totalEmitters)) {
            return getEventEmitters(totalEmitters);
        }

        log.error("No event emitters from subscriber, no message returning.");
        return Collections.emptyList();
    }

    private List<EventEmitter<CloudEvent>> getEventEmitters(List<EventEmitter<CloudEvent>> emitterList) {
        switch (subscriptionMode) {
            case CLUSTERING:
                return Collections.singletonList(emitterList.get((startIdx + retryTimes) % emitterList.size()));
            case BROADCASTING:
                return emitterList;
            default:
                log.error("Invalid Subscription Mode, no message returning back to subscriber.");
                return Collections.emptyList();
        }
    }
}
