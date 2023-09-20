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

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;

import org.apache.commons.collections4.MapUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageHandler {

    private static final ScheduledExecutorService SCHEDULER = ThreadPoolFactory.createSingleScheduledExecutor("eventMesh-pushMsgTimeout-");

    private final ThreadPoolExecutor pushExecutor;

    private static final Integer CONSUMER_GROUP_WAITING_REQUEST_THRESHOLD = 10000;

    private static final Map<String, Set<AbstractPushRequest>> waitingRequests = Maps.newConcurrentMap();

    public MessageHandler(String consumerGroup, ThreadPoolExecutor pushMsgExecutor) {
        this.pushExecutor = pushMsgExecutor;
        waitingRequests.put(consumerGroup, Sets.newConcurrentHashSet());
        SCHEDULER.scheduleAtFixedRate(this::checkTimeout, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void checkTimeout() {
        waitingRequests.forEach((key, value) -> {
            for (AbstractPushRequest request : value) {
                request.timeout();
                waitingRequests.get(request.getHandleMsgContext().getConsumerGroup()).remove(request);
            }
        });
    }

    public boolean handle(HandleMsgContext handleMsgContext) {
        Set<AbstractPushRequest> waitingRequests4Group = MapUtils.getObject(waitingRequests,
            handleMsgContext.getConsumerGroup(), Sets.newConcurrentHashSet());
        if (waitingRequests4Group.size() > CONSUMER_GROUP_WAITING_REQUEST_THRESHOLD) {
            log.warn("waitingRequests is too many, so reject, this message will be send back to MQ, consumerGroup:{}, threshold:{}",
                handleMsgContext.getConsumerGroup(), CONSUMER_GROUP_WAITING_REQUEST_THRESHOLD);
            return false;
        }

        try {
            pushExecutor.submit(() -> {
                AbstractPushRequest pushRequest = createGrpcPushRequest(handleMsgContext);
                pushRequest.tryPushRequest();
            });
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private AbstractPushRequest createGrpcPushRequest(HandleMsgContext handleMsgContext) {
        GrpcType grpcType = handleMsgContext.getGrpcType();
        if (GrpcType.WEBHOOK == grpcType) {
            return new WebhookPushRequest(handleMsgContext, waitingRequests);
        } else {
            return new StreamPushRequest(handleMsgContext, waitingRequests);
        }
    }
}
