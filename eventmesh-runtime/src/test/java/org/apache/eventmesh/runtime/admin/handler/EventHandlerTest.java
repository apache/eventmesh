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

package org.apache.eventmesh.runtime.admin.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.runtime.admin.handler.v1.EventHandler;
import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.plugin.MQAdminWrapper;
import org.apache.eventmesh.runtime.mock.MockCloudEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import io.cloudevents.CloudEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpVersion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
public class EventHandlerTest {

    public static String storagePlugin = "standalone";

    EmbeddedChannel embeddedChannel;

    AdminHandlerManager adminHandlerManager;

    @Mock
    EventMeshServer eventMeshServer;

    @Mock
    EventMeshTCPServer eventMeshTCPServer;

    @Spy
    EventHandler eventHandler = new EventHandler(storagePlugin);

    @Mock
    MQAdminWrapper mockAdmin;

    List<CloudEvent> result = new ArrayList<>();

    Method initHandler;

    @BeforeEach
    public void init() throws Exception {
        result.add(new MockCloudEvent());
        when(eventMeshServer.getEventMeshTCPServer()).thenReturn(eventMeshTCPServer);
        adminHandlerManager = new AdminHandlerManager(eventMeshServer);
        Field admin = EventHandler.class.getDeclaredField("admin");
        admin.setAccessible(true);
        admin.set(eventHandler, mockAdmin);
        embeddedChannel = new EmbeddedChannel(
            new HttpRequestDecoder(),
            new HttpResponseEncoder(),
            new HttpObjectAggregator(Integer.MAX_VALUE),
            new SimpleChannelInboundHandler<HttpRequest>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
                    String uriStr = msg.uri();
                    URI uri = URI.create(uriStr);
                    adminHandlerManager.getHttpHandler(uri.getPath()).get().handle(msg, ctx);
                }
            });
        initHandler = AdminHandlerManager.class.getDeclaredMethod("initHandler", HttpHandler.class);
        initHandler.setAccessible(true);
        initHandler.invoke(adminHandlerManager, eventHandler);
    }

    @Test
    public void testGet() throws Exception {
        when(mockAdmin.getEvent(anyString(), anyInt(), anyInt())).thenReturn(result);
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, "/event?topicName=123&offset=0&length=1");
        embeddedChannel.writeInbound(httpRequest);
        boolean finish = embeddedChannel.finish();
        assertTrue(finish);
        ByteBuf byteBuf = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
        while ((byteBuf = embeddedChannel.readOutbound()) != null) {
            byte[] data = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(data);
            byteArrayOutputStream.write(data);
        }
        ;
        String response = new String(byteArrayOutputStream.toByteArray(), "UTF-8");
        String responseBody = response.split("\\r?\\n\\r?\\n")[1];
        JsonNode jsonNode = new ObjectMapper().readTree(responseBody);
        assertTrue(jsonNode.get(0).asText().contains("mockData"));
    }

    @Test
    public void testPost() throws IOException {
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.POST, "/event", Unpooled.copiedBuffer(
            ("specversion=1.0&id=cd7c0d63-6c7c-4300-9f4e-ceb51f46b1b1&source"
                + "=/&type=cloudevents&datacontenttype=application/cloudevents+json&subject=test&ttl=4000").getBytes()));
        embeddedChannel.writeInbound(httpRequest);
        ByteBuf byteBuf = embeddedChannel.readOutbound();
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        String response = new String(data, "UTF-8");
        String[] requestMessage = response.split("\r\n");
        assertEquals("HTTP/1.1 200 OK", requestMessage[0].toString());
    }
}
