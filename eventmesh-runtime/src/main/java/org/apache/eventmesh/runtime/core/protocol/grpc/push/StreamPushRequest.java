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

import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;

public class StreamPushRequest extends AbstractPushRequest {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    private final Map<String, List<EventEmitter<SimpleMessage>>> idcEmitters;

    private final List<EventEmitter<SimpleMessage>> totalEmitters;

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
        if (simpleMessage == null) {
            return;
        }

        List<EventEmitter<SimpleMessage>> eventEmitters = selectEmitter();

        for (EventEmitter<SimpleMessage> eventEmitter : eventEmitters) {
            this.lastPushTime = System.currentTimeMillis();

            simpleMessage = SimpleMessage.newBuilder(simpleMessage)
                .putProperties(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP, String.valueOf(lastPushTime))
                .build();
            try {
                // catch the error and retry, don't use eventEmitter.onNext() to hide the error
                StreamObserver<SimpleMessage> emitter = eventEmitter.getEmitter();
                synchronized (emitter) {
                    emitter.onNext(simpleMessage);
                }

                long cost = System.currentTimeMillis() - lastPushTime;
                messageLogger.info(
                    "message|eventMesh2client|emitter|topic={}|bizSeqNo={}"
                        + "|uniqueId={}|cost={}", simpleMessage.getTopic(),
                    simpleMessage.getSeqNum(), simpleMessage.getUniqueId(), cost);
                complete();
            } catch (Throwable t) {
                long cost = System.currentTimeMillis() - lastPushTime;
                messageLogger.error(
                    "message|eventMesh2client|exception={} |emitter|topic={}|bizSeqNo={}"
                        + "|uniqueId={}|cost={}", t.getMessage(), simpleMessage.getTopic(),
                    simpleMessage.getSeqNum(), simpleMessage.getUniqueId(), cost, t);

                delayRetry();
            }
        }
    }

    private List<EventEmitter<SimpleMessage>> selectEmitter() {
        List<EventEmitter<SimpleMessage>> emitterList = MapUtils.getObject(idcEmitters,
            eventMeshGrpcConfiguration.eventMeshIDC, null);
        if (CollectionUtils.isNotEmpty(emitterList)) {
            if (subscriptionMode.equals(SubscriptionMode.CLUSTERING)) {
                return Collections.singletonList(emitterList.get((startIdx + retryTimes) % emitterList.size()));
            } else if (subscriptionMode.equals(SubscriptionMode.BROADCASTING)) {
                return emitterList;
            } else {
                messageLogger.error("Invalid Subscription Mode, no message returning back to subscriber.");
                return Collections.emptyList();
            }
        }
        if (CollectionUtils.isNotEmpty(totalEmitters)) {
            if (subscriptionMode.equals(SubscriptionMode.CLUSTERING)) {
                return Collections.singletonList(totalEmitters.get((startIdx + retryTimes) % totalEmitters.size()));
            } else if (subscriptionMode.equals(SubscriptionMode.BROADCASTING)) {
                return totalEmitters;
            } else {
                messageLogger.error("Invalid Subscription Mode, no message returning back to subscriber.");
                return Collections.emptyList();
            }
        }
        messageLogger.error("No event emitters from subscriber, no message returning.");
        return Collections.emptyList();
    }
}
