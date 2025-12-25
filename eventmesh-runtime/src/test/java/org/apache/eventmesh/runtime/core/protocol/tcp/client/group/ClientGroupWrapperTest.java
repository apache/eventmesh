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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.function.api.Router;
import org.apache.eventmesh.function.filter.pattern.Pattern;
import org.apache.eventmesh.function.transformer.Transformer;
import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.boot.FilterEngine;
import org.apache.eventmesh.runtime.boot.RouterEngine;
import org.apache.eventmesh.runtime.boot.TransformerEngine;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.core.plugin.MQProducerWrapper;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.dispatch.DownstreamDispatchStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.retry.TcpRetryer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.send.UpStreamMsgContext;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMetricsManager;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

@ExtendWith(MockitoExtension.class)
public class ClientGroupWrapperTest {

    @Mock
    private EventMeshTCPServer eventMeshTCPServer;

    @Mock
    private EventMeshServer eventMeshServer;

    @Mock
    private EventMeshTCPConfiguration eventMeshTCPConfiguration;

    @Mock
    private TcpRetryer tcpRetryer;

    @Mock
    private EventMeshTcpMetricsManager eventMeshTcpMetricsManager;

    @Mock
    private DownstreamDispatchStrategy downstreamDispatchStrategy;

    @Mock
    private FilterEngine filterEngine;

    @Mock
    private TransformerEngine transformerEngine;

    @Mock
    private RouterEngine routerEngine;

    @Mock
    private MQProducerWrapper mqProducerWrapper;

    private ClientGroupWrapper clientGroupWrapper;

    @BeforeEach
    public void setUp() {
        lenient().when(eventMeshTCPServer.getEventMeshTCPConfiguration()).thenReturn(eventMeshTCPConfiguration);
        lenient().when(eventMeshTCPServer.getTcpRetryer()).thenReturn(tcpRetryer);
        lenient().when(eventMeshTCPServer.getEventMeshTcpMetricsManager()).thenReturn(eventMeshTcpMetricsManager);
        lenient().when(eventMeshTCPConfiguration.getEventMeshStoragePluginType()).thenReturn("standalone");
        lenient().when(eventMeshTCPServer.getEventMeshServer()).thenReturn(eventMeshServer);
        lenient().when(eventMeshServer.getFilterEngine()).thenReturn(filterEngine);
        lenient().when(eventMeshServer.getTransformerEngine()).thenReturn(transformerEngine);
        lenient().when(eventMeshServer.getRouterEngine()).thenReturn(routerEngine);

        clientGroupWrapper = spy(new ClientGroupWrapper("sysId", "group", eventMeshTCPServer, downstreamDispatchStrategy));
        // Reflection to set mqProducerWrapper if needed, or we can mock the one created internally if possible?
        // Since ClientGroupWrapper creates `new MQProducerWrapper`, we can't easily mock it unless we assume it works or use PowerMock.
        // But ClientGroupWrapper has a getter `getMqProducerWrapper()`, maybe we can set it via reflection or if there is a setter?
        // No setter.
        // We will assume `new MQProducerWrapper("standalone")` works fine (it uses SPI, might fail if no plugin).
        // To verify the pipeline, we mainly care about Engines interaction.
        
        // However, `send` calls `mqProducerWrapper.send`. If that fails, test fails.
        // For unit test, we might need to mock the internal mqProducerWrapper.
        // Since we are mocking `EventMeshTCPServer`, `ClientGroupWrapper` uses it to access engines.
        
        // Let's try to set the internal mqProducerWrapper field using reflection.
        try {
            java.lang.reflect.Field field = ClientGroupWrapper.class.getDeclaredField("mqProducerWrapper");
            field.setAccessible(true);
            field.set(clientGroupWrapper, mqProducerWrapper);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSendWithIngressPipeline() throws Exception {
        CloudEvent event = CloudEventBuilder.v1()
            .withId("id1")
            .withSource(java.net.URI.create("source"))
            .withType("type")
            .withSubject("topic")
            .withData("data".getBytes(StandardCharsets.UTF_8))
            .build();

        UpStreamMsgContext context = new UpStreamMsgContext(mock(Session.class), event, mock(Header.class), System.currentTimeMillis(), System.currentTimeMillis());
        SendCallback callback = mock(SendCallback.class);

        // 1. Mock Filter (Pass)
        Pattern pattern = mock(Pattern.class);
        when(filterEngine.getFilterPattern("group-topic")).thenReturn(pattern);
        when(pattern.filter(anyString())).thenReturn(true);

        // 2. Mock Transformer
        Transformer transformer = mock(Transformer.class);
        when(transformerEngine.getTransformer("group-topic")).thenReturn(transformer);
        when(transformer.transform(anyString())).thenReturn("transformedData");

        // 3. Mock Router
        Router router = mock(Router.class);
        when(routerEngine.getRouter("group-topic")).thenReturn(router);
        when(router.route(anyString())).thenReturn("newTopic");

        clientGroupWrapper.send(context, callback);

        // Verify Engines called
        verify(filterEngine).getFilterPattern("group-topic");
        verify(transformerEngine).getTransformer("group-topic");
        verify(routerEngine).getRouter("group-topic");

        // Verify Producer sent modified event
        // We capture the event passed to producer
        org.mockito.ArgumentCaptor<CloudEvent> captor = org.mockito.ArgumentCaptor.forClass(CloudEvent.class);
        verify(mqProducerWrapper).send(captor.capture(), any());
        
        CloudEvent sentEvent = captor.getValue();
        Assertions.assertEquals("newTopic", sentEvent.getSubject());
        Assertions.assertEquals("transformedData", new String(sentEvent.getData().toBytes(), StandardCharsets.UTF_8));
    }
    
    @Test
    public void testSendWithFilterDrop() throws Exception {
        CloudEvent event = CloudEventBuilder.v1()
            .withId("id1")
            .withSource(java.net.URI.create("source"))
            .withType("type")
            .withSubject("topic")
            .withData("data".getBytes(StandardCharsets.UTF_8))
            .build();

        UpStreamMsgContext context = new UpStreamMsgContext(mock(Session.class), event, mock(Header.class), System.currentTimeMillis(), System.currentTimeMillis());
        SendCallback callback = mock(SendCallback.class);

        // 1. Mock Filter (Reject)
        Pattern pattern = mock(Pattern.class);
        when(filterEngine.getFilterPattern("group-topic")).thenReturn(pattern);
        when(pattern.filter(anyString())).thenReturn(false);

        clientGroupWrapper.send(context, callback);

        // Verify Producer NOT called
        verify(mqProducerWrapper, org.mockito.Mockito.never()).send(any(), any());
        // Verify callback onSuccess (filtered treated as success in current logic)
        verify(callback).onSuccess(any());
    }
}
