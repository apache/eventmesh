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

package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.push.MessageHandler;
import org.apache.eventmesh.runtime.metrics.grpc.EventMeshGrpcMetricsManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class EventMeshConsumerOverloadTest {

    @Test
    void broadcastOverloadRequestsLocalRetryWithoutAcknowledging() throws Exception {
        EventMeshGrpcServer server = mock(EventMeshGrpcServer.class);
        when(server.getEventMeshGrpcMetricsManager()).thenReturn(mock(EventMeshGrpcMetricsManager.class));
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.handle(any())).thenReturn(false);
        Map<String, ConsumerGroupTopicConfig> topics = new ConcurrentHashMap<>();
        topics.put("topic", mock(ConsumerGroupTopicConfig.class));
        EventMeshConsumer consumer = mock(EventMeshConsumer.class, CALLS_REAL_METHODS);
        setField(consumer, "eventMeshGrpcServer", server);
        setField(consumer, "messageHandler", handler);
        setField(consumer, "consumerGroupTopicConfig", topics);
        setField(consumer, "consumerGroup", "group");
        Method createListener = EventMeshConsumer.class.getDeclaredMethod("createEventListener", SubscriptionMode.class);
        createListener.setAccessible(true);
        EventListener listener = (EventListener) createListener.invoke(consumer, SubscriptionMode.BROADCASTING);
        CloudEvent event = CloudEventBuilder.v1().withId("id").withSource(URI.create("urn:test"))
            .withType("test").withSubject("topic").build();
        EventMeshAsyncConsumeContext context = mock(EventMeshAsyncConsumeContext.class);
        when(context.supportsLocalRetry()).thenReturn(true);

        listener.consume(event, context);

        verify(context).commit(EventMeshAction.ReconsumeLater);
        verify(context, never()).commit(EventMeshAction.CommitMessage);
        verify(server, never()).getProducerManager();
    }

    @Test
    void broadcastWithoutActiveTopicRequestsLocalRetryWithoutAcknowledging() throws Exception {
        EventMeshGrpcServer server = mock(EventMeshGrpcServer.class);
        when(server.getEventMeshGrpcMetricsManager()).thenReturn(mock(EventMeshGrpcMetricsManager.class));
        EventMeshConsumer consumer = mock(EventMeshConsumer.class, CALLS_REAL_METHODS);
        setField(consumer, "eventMeshGrpcServer", server);
        setField(consumer, "consumerGroupTopicConfig", new ConcurrentHashMap<String, ConsumerGroupTopicConfig>());
        Method createListener = EventMeshConsumer.class.getDeclaredMethod("createEventListener", SubscriptionMode.class);
        createListener.setAccessible(true);
        EventListener listener = (EventListener) createListener.invoke(consumer, SubscriptionMode.BROADCASTING);
        CloudEvent event = CloudEventBuilder.v1().withId("id").withSource(URI.create("urn:test"))
            .withType("test").withSubject("topic").build();
        EventMeshAsyncConsumeContext context = mock(EventMeshAsyncConsumeContext.class);
        when(context.supportsLocalRetry()).thenReturn(true);

        listener.consume(event, context);

        verify(context).commit(EventMeshAction.ReconsumeLater);
        verify(context, never()).commit(EventMeshAction.CommitMessage);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = EventMeshConsumer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
