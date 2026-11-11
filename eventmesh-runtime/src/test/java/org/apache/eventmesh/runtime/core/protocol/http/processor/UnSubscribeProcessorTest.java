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

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.BaseResponseBody;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeRequestHeader;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.core.consumer.ClientInfo;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.consumergroup.event.ConsumerGroupStateEvent;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerGroupManager;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import com.google.common.eventbus.EventBus;

public class UnSubscribeProcessorTest {

    private static final String URL = "http://localhost:8080/events";

    private final EventMeshHTTPServer server = Mockito.mock(EventMeshHTTPServer.class, Mockito.RETURNS_DEEP_STUBS);

    private final SubscriptionManager subscriptions = new SubscriptionManager(false, null);

    private final EventBus eventBus = Mockito.mock(EventBus.class);

    @Test
    public void testPartialUnsubscribeNotifiesChangeWithRemainingTopics() throws Exception {
        initialize("orders", "payments");

        process("orders");

        ConsumerGroupStateEvent event = notification();
        Assertions.assertEquals(ConsumerGroupStateEvent.ConsumerGroupStateAction.CHANGE, event.getAction());
        Assertions.assertEquals(Collections.singleton("payments"), event.getConsumerGroupConfig().getConsumerGroupTopicConf().keySet());
    }

    @Test
    public void testFinalUnsubscribeNotifiesDelete() throws Exception {
        initialize("orders", "payments");

        process("orders", "payments");

        Assertions.assertEquals(ConsumerGroupStateEvent.ConsumerGroupStateAction.DELETE, notification().getAction());
        Assertions.assertFalse(subscriptions.getLocalConsumerGroupMapping().containsKey("group"));
    }

    @Test
    public void testUnknownTopicDoesNotDeleteOrRestartExistingGroup() throws Exception {
        initialize("orders");

        process("unknown");

        Mockito.verifyNoInteractions(eventBus);
        Assertions.assertTrue(subscriptions.getLocalConsumerGroupMapping().containsKey("group"));
    }

    private void initialize(String... topics) throws Exception {
        ClientInfo client = new ClientInfo();
        client.setPid("1");
        client.setIdc("idc");
        List<SubscriptionItem> items = Arrays.stream(topics)
            .map(topic -> new SubscriptionItem(topic, SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC))
            .collect(Collectors.toList());
        subscriptions.registerClient(client, "group", items, URL);
        subscriptions.updateSubscription(client, "group", URL, items);
        ConsumerGroupManager running = Mockito.mock(ConsumerGroupManager.class);
        Mockito.when(running.getConsumerGroupConfig())
            .thenReturn(EventMeshUtil.cloneObject(subscriptions.getLocalConsumerGroupMapping().get("group")));
        ConsumerManager consumers = new ConsumerManager(server);
        consumers.getClientTable().put("group", running);
        Mockito.when(server.getConsumerManager()).thenReturn(consumers);
        Mockito.when(server.getSubscriptionManager()).thenReturn(subscriptions);
        Mockito.when(server.getEventBus()).thenReturn(eventBus);
        Mockito.when(server.getEventMeshHttpConfiguration()).thenReturn(new EventMeshHTTPConfiguration());
    }

    private void process(String... topics) throws Exception {
        UnSubscribeRequestHeader header = new UnSubscribeRequestHeader();
        header.setIdc("idc");
        header.setSys("sys");
        header.setPid("1");
        UnSubscribeRequestBody body = new UnSubscribeRequestBody();
        body.setConsumerGroup("group");
        body.setUrl(URL);
        body.setTopics(Arrays.asList(topics));
        HttpCommand request = new HttpCommand("POST", "HTTP/1.1", RequestCode.UNSUBSCRIBE.getRequestCode().toString());
        request.setHeader(header);
        request.setBody(body);
        AsyncContext<HttpCommand> context = new AsyncContext<>(request, null, Mockito.mock(ThreadPoolExecutor.class));
        Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        ChannelHandlerContext channelContext = Mockito.mock(ChannelHandlerContext.class);
        Mockito.when(channelContext.channel()).thenReturn(channel);

        new UnSubscribeProcessor(server).processRequest(channelContext, context);

        Assertions.assertTrue(context.isComplete());
        Assertions.assertEquals(EventMeshRetCode.SUCCESS.getRetCode(), ((BaseResponseBody) context.getResponse().getBody()).getRetCode());
    }

    private ConsumerGroupStateEvent notification() {
        ArgumentCaptor<ConsumerGroupStateEvent> event = ArgumentCaptor.forClass(ConsumerGroupStateEvent.class);
        Mockito.verify(eventBus).post(event.capture());
        return event.getValue();
    }
}
