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

package org.apache.eventmesh.client.grpc.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PublisherServiceBlockingStub.class, Response.class})
@PowerMockIgnore({"javax.management.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "org.w3c.*"})
public class EventMeshMessageProducerTest {

    private EventMeshMessageProducer eventMeshMessageProducer;

    @Mock
    private PublisherServiceBlockingStub blockingStub;

    @Mock
    private Response mockResponse;

    @Mock
    private SimpleMessage mockSimpleMessage;

    @Before
    public void setUp() throws Exception {
        eventMeshMessageProducer = new EventMeshMessageProducer(EventMeshGrpcClientConfig.builder().build(), blockingStub);
        when(blockingStub.batchPublish(Mockito.isA(BatchMessage.class))).thenReturn(mockResponse);
        when(blockingStub.publish(Mockito.isA(SimpleMessage.class))).thenReturn(mockResponse);
        when(blockingStub.requestReply(Mockito.isA(SimpleMessage.class))).thenReturn(mockSimpleMessage);
        doAnswer(invocation -> {
            SimpleMessage simpleMessage = invocation.getArgument(0);
            if (StringUtils.isEmpty(simpleMessage.getContent())) {
                return SimpleMessage.getDefaultInstance();
            }
            return SimpleMessage.newBuilder(simpleMessage).build();
        }).when(blockingStub).requestReply(any());
        doReturn(blockingStub).when(blockingStub).withDeadlineAfter(1000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testPublishSingle() {

        EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
            .topic("mxsm")
            .content("mxsm")
            .bizSeqNo("mxsm")
            .uniqueId("mxsm")
            .build();
        assertThat(eventMeshMessageProducer.publish(eventMeshMessage)).isEqualTo(mockResponse);
    }

    @Test
    public void testPublishMulti() {

        List<EventMeshMessage> messageArrayList = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                .topic("mxsm")
                .content("mxsm")
                .bizSeqNo("mxsm" + i)
                .uniqueId("mxsm" + i)
                .build();
            messageArrayList.add(eventMeshMessage);
        }
        assertThat(eventMeshMessageProducer.publish(messageArrayList)).isEqualTo(mockResponse);
        assertThat(eventMeshMessageProducer.publish(Collections.emptyList())).isNull();
    }

    @Test
    public void requestReply() {
        EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
            .topic("mxsm")
            .content("mxsm")
            .bizSeqNo("mxsm")
            .uniqueId("mxsm")
            .build();
        assertThat(eventMeshMessageProducer.requestReply(eventMeshMessage, 1000L)).isNotNull();
    }
}
