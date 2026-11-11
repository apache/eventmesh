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

package org.apache.eventmesh.runtime.core.protocol.http.consumer;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.http.push.HTTPMessageHandler;
import org.apache.eventmesh.runtime.util.TraceUtils;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.net.URI;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class EventMeshConsumerTest {

    @Test
    public void testClusteringCallbackKeepsOneConfigurationDuringRefresh() throws Exception {
        assertConsistentSnapshot(SubscriptionMode.CLUSTERING);
    }

    @Test
    public void testBroadcastCallbackKeepsOneConfigurationDuringRefresh() throws Exception {
        assertConsistentSnapshot(SubscriptionMode.BROADCASTING);
    }

    @Test
    public void testClusteringRemovedTopicCommitsWhenSendBackFails() throws Exception {
        assertRemovedTopicCompletes(SubscriptionMode.CLUSTERING);
    }

    @Test
    public void testBroadcastRemovedTopicCommitsWhenSendBackFails() throws Exception {
        assertRemovedTopicCompletes(SubscriptionMode.BROADCASTING);
    }

    private void assertConsistentSnapshot(SubscriptionMode mode) throws Exception {
        try (MockedConstruction<MQConsumerWrapper> wrappers = Mockito.mockConstruction(MQConsumerWrapper.class);
            MockedConstruction<HTTPMessageHandler> handlers = Mockito.mockConstruction(HTTPMessageHandler.class);
            MockedStatic<TraceUtils> traces = Mockito.mockStatic(TraceUtils.class);
            MockedStatic<EventMeshExtensionFactory> extensions = Mockito.mockStatic(EventMeshExtensionFactory.class)) {
            final ConsumerGroupConf initial = Mockito.spy(new ConsumerGroupConf("group"));
            final ConsumerGroupConf replacement = new ConsumerGroupConf("group");
            final ConsumerGroupTopicConf topic = new ConsumerGroupTopicConf();
            topic.setSubscriptionItem(new SubscriptionItem("orders", mode, SubscriptionType.ASYNC));
            final EventMeshConsumer consumer = new EventMeshConsumer(server(), initial);
            consumer.init();
            Mockito.doAnswer(invocation -> {
                consumer.setConsumerGroupConf(replacement);
                return Collections.singletonMap("orders", topic);
            }).when(initial).getConsumerGroupTopicConf();
            final HTTPMessageHandler handler = handlers.constructed().get(0);
            Mockito.when(handler.handle(Mockito.any())).thenReturn(true);
            final EventMeshAsyncConsumeContext context = Mockito.mock(EventMeshAsyncConsumeContext.class);

            listener(wrappers, mode).consume(event(), context);

            final ArgumentCaptor<HandleMsgContext> handled = ArgumentCaptor.forClass(HandleMsgContext.class);
            Mockito.verify(handler).handle(handled.capture());
            Assertions.assertSame(replacement, consumer.getConsumerGroupConf());
            Assertions.assertSame(initial, handled.getValue().getConsumerGroupConfig());
            Assertions.assertSame(topic, handled.getValue().getConsumeTopicConfig());
            Assertions.assertSame(topic.getSubscriptionItem(), handled.getValue().getSubscriptionItem());
            Mockito.verify(context).commit(EventMeshAction.ManualAck);
        }
    }

    private void assertRemovedTopicCompletes(SubscriptionMode mode) throws Exception {
        try (MockedConstruction<MQConsumerWrapper> wrappers = Mockito.mockConstruction(MQConsumerWrapper.class);
            MockedConstruction<HTTPMessageHandler> handlers = Mockito.mockConstruction(HTTPMessageHandler.class);
            MockedStatic<TraceUtils> traces = Mockito.mockStatic(TraceUtils.class);
            MockedStatic<EventMeshExtensionFactory> extensions = Mockito.mockStatic(EventMeshExtensionFactory.class)) {
            final EventMeshConsumer consumer = Mockito.spy(new EventMeshConsumer(server(), new ConsumerGroupConf("group")));
            consumer.init();
            Mockito.doThrow(new IllegalStateException("send-back unavailable"))
                .when(consumer).sendMessageBack(Mockito.any(), Mockito.anyString(), Mockito.anyString());
            final EventMeshAsyncConsumeContext context = Mockito.mock(EventMeshAsyncConsumeContext.class);

            listener(wrappers, mode).consume(event(), context);

            Mockito.verify(consumer).sendMessageBack(Mockito.any(), Mockito.anyString(), Mockito.anyString());
            Mockito.verify(context).commit(EventMeshAction.CommitMessage);
            Mockito.verifyNoInteractions(handlers.constructed().get(0));
        }
    }

    private EventListener listener(MockedConstruction<MQConsumerWrapper> wrappers, SubscriptionMode mode) {
        final ArgumentCaptor<EventListener> listener = ArgumentCaptor.forClass(EventListener.class);
        Mockito.verify(wrappers.constructed().get(mode == SubscriptionMode.CLUSTERING ? 0 : 1)).registerEventListener(listener.capture());
        return listener.getValue();
    }

    private EventMeshHTTPServer server() {
        final EventMeshHTTPConfiguration configuration = new EventMeshHTTPConfiguration();
        configuration.setEventMeshServerIp("127.0.0.1");
        final EventMeshHTTPServer server = Mockito.mock(EventMeshHTTPServer.class);
        Mockito.when(server.getEventMeshHttpConfiguration()).thenReturn(configuration);
        return server;
    }

    private CloudEvent event() {
        return CloudEventBuilder.v1()
            .withId("id")
            .withSource(URI.create("urn:test"))
            .withType("test")
            .withSubject("orders")
            .withExtension("bizseqno", "biz")
            .withExtension("uniqueid", "unique")
            .build();
    }
}
