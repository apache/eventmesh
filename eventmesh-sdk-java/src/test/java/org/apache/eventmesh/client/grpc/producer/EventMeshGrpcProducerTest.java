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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.EventMeshMessage.EventMeshMessageBuilder;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PublisherServiceBlockingStub.class)
@PowerMockIgnore({"javax.management.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "org.w3c.*"})
public class EventMeshGrpcProducerTest {

    private EventMeshGrpcProducer producer;
    @Mock
    private CloudEventProducer cloudEventProducer;
    @Mock
    private PublisherServiceBlockingStub stub;

    @Before
    public void setUp() throws Exception {
        producer = new EventMeshGrpcProducer(EventMeshGrpcClientConfig.builder().build());
        producer.cloudEventProducer = cloudEventProducer;
        producer.publisherClient = stub;
        doThrow(RuntimeException.class).when(stub).publish(
            argThat(argument -> argument != null && StringUtils.equals(argument.getContent(),
                "mockExceptionContent")));
        doReturn(Response.getDefaultInstance()).when(stub).publish(
            argThat(argument -> argument != null && StringUtils.equals(argument.getContent(), "mockContent")));
        doReturn(Response.getDefaultInstance()).when(stub).batchPublish(
            argThat(argument -> argument != null && StringUtils.equals(argument.getTopic(), "mockTopic")));
        doAnswer(invocation -> {
            SimpleMessage simpleMessage = invocation.getArgument(0);
            if (StringUtils.isEmpty(simpleMessage.getContent())) {
                return SimpleMessage.getDefaultInstance();
            }
            return SimpleMessage.newBuilder(simpleMessage).build();
        }).when(stub).requestReply(any());
        doReturn(stub).when(stub).withDeadlineAfter(1000, TimeUnit.MILLISECONDS);
        when(cloudEventProducer.publish(anyList())).thenReturn(Response.getDefaultInstance());
    }

    @Test
    public void testPublishWithException() {
        assertThat(producer.publish(defaultEventMeshMessageBuilder().content("mockExceptionContent").build())).isNull();
    }

    @Test
    public void testPublishEventMeshMessage() {
        assertThat(producer.publish(defaultEventMeshMessageBuilder().build())).isEqualTo(Response.getDefaultInstance());
    }

    @Test
    public void testPublishEmptyList() {
        assertThat(producer.publish(Collections.emptyList())).isNull();
    }

    @Test
    public void testPublishGenericMessageList() {
        assertThat(producer.publish(Collections.singletonList(new MockCloudEvent()))).isEqualTo(
            Response.getDefaultInstance());
        EventMeshMessageBuilder eventMeshMessageBuilder = defaultEventMeshMessageBuilder();
        eventMeshMessageBuilder.prop(Collections.singletonMap(Constants.EVENTMESH_MESSAGE_CONST_TTL, "1000"));
        assertThat(producer.publish(Collections.singletonList(eventMeshMessageBuilder.build()))).isEqualTo(
            Response.getDefaultInstance());
    }

    @Test
    public void testRequestReply() {
        assertThat(producer.requestReply(defaultEventMeshMessageBuilder().content(StringUtils.EMPTY).build(),
            1000)).isNull();
        EventMeshMessage eventMeshMessage = defaultEventMeshMessageBuilder().build();
        assertThat(producer.requestReply(eventMeshMessage, 1000)).hasFieldOrPropertyWithValue("content",
            eventMeshMessage.getContent()).hasFieldOrPropertyWithValue("topic", eventMeshMessage.getTopic());
    }

    private EventMeshMessage.EventMeshMessageBuilder defaultEventMeshMessageBuilder() {
        return EventMeshMessage.builder().bizSeqNo("bizSeqNo").content("mockContent")
            .createTime(System.currentTimeMillis()).uniqueId("mockUniqueId").topic("mockTopic")
            .prop(Collections.emptyMap());
    }
}