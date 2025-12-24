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

package org.apache.eventmesh.runtime.core.protocol.http.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.function.api.Router;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.runtime.boot.FilterEngine;
import org.apache.eventmesh.runtime.boot.HTTPTrace.TraceOperation;
import org.apache.eventmesh.runtime.boot.RouterEngine;
import org.apache.eventmesh.runtime.boot.TransformerEngine;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.retry.HttpRetryer;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.metrics.http.EventMeshHttpMetricsManager;
import org.apache.eventmesh.runtime.metrics.http.HttpMetrics;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.google.common.util.concurrent.RateLimiter;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SendAsyncEventProcessorTest {

    @Mock
    private EventMeshHTTPServer eventMeshHTTPServer;
    @Mock
    private EventMeshServer eventMeshServer;
    @Mock
    private EventMeshHTTPConfiguration eventMeshHttpConfiguration;
    @Mock
    private ProducerManager producerManager;
    @Mock
    private EventMeshProducer eventMeshProducer;
    @Mock
    private Acl acl;
    @Mock
    private FilterEngine filterEngine;
    @Mock
    private TransformerEngine transformerEngine;
    @Mock
    private RouterEngine routerEngine;
    @Mock
    private HandlerService.HandlerSpecific handlerSpecific;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Channel channel;
    @Mock
    private HttpRequest httpRequest;
    @Mock
    private HttpRetryer httpRetryer;
    @Mock
    private EventMeshHttpMetricsManager metricsManager;
    @Mock
    private HttpMetrics httpMetrics;
    @Mock
    private ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor;
    @Mock
    private TraceOperation traceOperation;

    private SendAsyncEventProcessor processor;

    @BeforeEach
    public void setUp() {
        when(eventMeshHTTPServer.getEventMeshServer()).thenReturn(eventMeshServer);
        when(eventMeshHTTPServer.getEventMeshHttpConfiguration()).thenReturn(eventMeshHttpConfiguration);
        when(eventMeshHttpConfiguration.getEventMeshEventSize()).thenReturn(1024 * 1024);
        
        when(eventMeshHTTPServer.getProducerManager()).thenReturn(producerManager);
        when(eventMeshHTTPServer.getAcl()).thenReturn(acl);
        when(eventMeshHTTPServer.getMsgRateLimiter()).thenReturn(RateLimiter.create(1000));
        when(eventMeshHTTPServer.getHttpRetryer()).thenReturn(httpRetryer);
        when(eventMeshHTTPServer.getEventMeshHttpMetricsManager()).thenReturn(metricsManager);
        when(metricsManager.getHttpMetrics()).thenReturn(httpMetrics);

        when(eventMeshServer.getFilterEngine()).thenReturn(filterEngine);
        when(eventMeshServer.getTransformerEngine()).thenReturn(transformerEngine);
        when(eventMeshServer.getRouterEngine()).thenReturn(routerEngine);

        processor = new SendAsyncEventProcessor(eventMeshHTTPServer);
    }

    @Test
    public void testHandler_V1_NormalFlow() throws Exception {
        // Mock Context
        AsyncContext<HttpEventWrapper> asyncContext = mock(AsyncContext.class);
        HttpEventWrapper wrapper = mock(HttpEventWrapper.class);
        when(handlerSpecific.getAsyncContext()).thenReturn(asyncContext);
        when(asyncContext.getRequest()).thenReturn(wrapper);
        when(handlerSpecific.getCtx()).thenReturn(ctx);
        when(ctx.channel()).thenReturn(channel);
        
        when(handlerSpecific.getTraceOperation()).thenReturn(traceOperation);

        // Mock Wrapper headers
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put(ProtocolKey.PROTOCOL_TYPE, "http");
        when(wrapper.getHeaderMap()).thenReturn(headerMap);
        when(wrapper.getSysHeaderMap()).thenReturn(new HashMap<>());
        when(wrapper.getRequestURI()).thenReturn("http://localhost/publish");

        // Mock Protocol Adaptor
        CloudEvent event = CloudEventBuilder.v1()
            .withId("id1").withSource(java.net.URI.create("testSource")).withType("testType")
            .withSubject("testTopic")
            .withExtension(ProtocolKey.ClientInstanceKey.IDC.getKey(), "idc")
            .withExtension(ProtocolKey.ClientInstanceKey.PID.getKey(), "123")
            .withExtension(ProtocolKey.ClientInstanceKey.SYS.getKey(), "sys")
            .withExtension(ProtocolKey.ClientInstanceKey.PRODUCERGROUP.getKey(), "testGroup")
            .withExtension(ProtocolKey.ClientInstanceKey.TOKEN.getKey(), "token")
            .withData("testData".getBytes(StandardCharsets.UTF_8))
            .build();

        try (MockedStatic<ProtocolPluginFactory> pluginFactoryMock = Mockito.mockStatic(ProtocolPluginFactory.class);
             MockedStatic<RemotingHelper> remotingHelperMock = Mockito.mockStatic(RemotingHelper.class)) {
            
            pluginFactoryMock.when(() -> ProtocolPluginFactory.getProtocolAdaptor("http")).thenReturn(protocolAdaptor);
            when(protocolAdaptor.toCloudEvent(wrapper)).thenReturn(event);
            
            remotingHelperMock.when(() -> RemotingHelper.parseChannelRemoteAddr(channel)).thenReturn("127.0.0.1");

            // Mock Producer
            when(producerManager.getEventMeshProducer("testGroup", "token")).thenReturn(eventMeshProducer);
            when(eventMeshProducer.isStarted()).thenReturn(true);

            // Execute
            processor.handler(handlerSpecific, httpRequest);

            // Verify
            // 1. Filter/Transformer/Router should be queried
            verify(filterEngine).getFilterPattern("testGroup-testTopic");
            verify(transformerEngine).getTransformer("testGroup-testTopic");
            verify(routerEngine).getRouter("testGroup-testTopic");

            // Verify NO error response
            verify(handlerSpecific, times(0)).sendErrorResponse(any(), any(), any(), any());

            // 2. Send should be called (V1 flow)
            verify(eventMeshProducer).send(any(SendMessageContext.class), any(SendCallback.class));
        }
    }

    @Test
    public void testHandler_V2_RouterFlow() throws Exception {
        // Similar setup, but Router returns a new topic
        AsyncContext<HttpEventWrapper> asyncContext = mock(AsyncContext.class);
        HttpEventWrapper wrapper = mock(HttpEventWrapper.class);
        when(handlerSpecific.getAsyncContext()).thenReturn(asyncContext);
        when(asyncContext.getRequest()).thenReturn(wrapper);
        when(handlerSpecific.getCtx()).thenReturn(ctx);
        when(ctx.channel()).thenReturn(channel);
        when(handlerSpecific.getTraceOperation()).thenReturn(traceOperation);

        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put(ProtocolKey.PROTOCOL_TYPE, "http");
        when(wrapper.getHeaderMap()).thenReturn(headerMap);
        when(wrapper.getSysHeaderMap()).thenReturn(new HashMap<>());
        when(wrapper.getRequestURI()).thenReturn("http://localhost/publish");

        CloudEvent event = CloudEventBuilder.v1()
            .withId("id1").withSource(java.net.URI.create("testSource")).withType("testType")
            .withSubject("oldTopic") // Original Topic
            .withExtension(ProtocolKey.ClientInstanceKey.IDC.getKey(), "idc")
            .withExtension(ProtocolKey.ClientInstanceKey.PID.getKey(), "123")
            .withExtension(ProtocolKey.ClientInstanceKey.SYS.getKey(), "sys")
            .withExtension(ProtocolKey.ClientInstanceKey.PRODUCERGROUP.getKey(), "testGroup")
            .withExtension(ProtocolKey.ClientInstanceKey.TOKEN.getKey(), "token")
            .withData("testData".getBytes(StandardCharsets.UTF_8))
            .build();

        try (MockedStatic<ProtocolPluginFactory> pluginFactoryMock = Mockito.mockStatic(ProtocolPluginFactory.class);
             MockedStatic<RemotingHelper> remotingHelperMock = Mockito.mockStatic(RemotingHelper.class)) {
            
            pluginFactoryMock.when(() -> ProtocolPluginFactory.getProtocolAdaptor("http")).thenReturn(protocolAdaptor);
            when(protocolAdaptor.toCloudEvent(wrapper)).thenReturn(event);
            remotingHelperMock.when(() -> RemotingHelper.parseChannelRemoteAddr(channel)).thenReturn("127.0.0.1");

            when(producerManager.getEventMeshProducer("testGroup", "token")).thenReturn(eventMeshProducer);
            when(eventMeshProducer.isStarted()).thenReturn(true);

            // Mock Router
            Router router = mock(Router.class);
            when(routerEngine.getRouter("testGroup-oldTopic")).thenReturn(router);
            when(router.route(anyString())).thenReturn("newTopic");

            // Execute
            processor.handler(handlerSpecific, httpRequest);

            // Verify
            verify(handlerSpecific, times(0)).sendErrorResponse(any(), any(), any(), any());

            // Verify send called
            verify(eventMeshProducer).send(any(SendMessageContext.class), any(SendCallback.class));
            // Verify router was called
            verify(router).route(anyString());
        }
    }
}