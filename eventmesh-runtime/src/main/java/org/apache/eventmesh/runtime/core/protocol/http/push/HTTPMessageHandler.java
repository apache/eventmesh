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

package org.apache.eventmesh.runtime.core.protocol.http.push;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.HandleMsgContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.TraceUtils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.collections4.MapUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.trace.Span;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HTTPMessageHandler implements MessageHandler {

    private final transient EventMeshConsumer eventMeshConsumer;

    private static final ScheduledExecutorService SCHEDULER =
        ThreadPoolFactory.createSingleScheduledExecutor("eventMesh-pushMsgTimeout");

    private static final Integer CONSUMER_GROUP_WAITING_REQUEST_THRESHOLD = 10000;

    protected static final Map<String, Set<AbstractHTTPPushRequest>> waitingRequests = Maps.newConcurrentMap();

    private final transient ThreadPoolExecutor pushExecutor;

    private void checkTimeout() {
        waitingRequests.forEach((key, value) -> value.forEach(r -> {
            r.timeout();
            waitingRequests.get(r.handleMsgContext.getConsumerGroup()).remove(r);
        }));

    }

    public HTTPMessageHandler(EventMeshConsumer eventMeshConsumer) {
        this.eventMeshConsumer = eventMeshConsumer;
        this.pushExecutor = eventMeshConsumer.getEventMeshHTTPServer().getHttpThreadPoolGroup().getPushMsgExecutor();
        waitingRequests.put(this.eventMeshConsumer.getConsumerGroupConf().getConsumerGroup(), Sets.newConcurrentHashSet());
        SCHEDULER.scheduleAtFixedRate(this::checkTimeout, 0, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean handle(final HandleMsgContext handleMsgContext) {
        if (MapUtils.getObject(waitingRequests, handleMsgContext.getConsumerGroup(), Sets.newConcurrentHashSet())
            .size() > CONSUMER_GROUP_WAITING_REQUEST_THRESHOLD) {
            log.warn("waitingRequests is too many, so reject, this message will be send back to MQ, "
                + "consumerGroup:{}, threshold:{}",
                handleMsgContext.getConsumerGroup(), CONSUMER_GROUP_WAITING_REQUEST_THRESHOLD);
            return false;
        }

        try {
            pushExecutor.submit(() -> {
                String protocolVersion = Objects.requireNonNull(handleMsgContext.getEvent().getSpecVersion()).toString();

                Span span = TraceUtils.prepareClientSpan(EventMeshUtil.getCloudEventExtensionMap(protocolVersion,
                    handleMsgContext.getEvent()),
                    EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_CLIENT_SPAN, false);

                try {
                    new AsyncHTTPPushRequest(handleMsgContext, waitingRequests).tryHTTPRequest();
                } finally {
                    TraceUtils.finishSpan(span, handleMsgContext.getEvent());
                }

            });
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("pushMsgThreadPoolQueue is full, so reject, current task size {}",
                handleMsgContext.getEventMeshHTTPServer().getHttpThreadPoolGroup().getPushMsgExecutor().getQueue().size(), e);
            return false;
        }
    }
}
