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
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.Response;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
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
@PrepareForTest(PublisherServiceBlockingStub.class)
@PowerMockIgnore({"javax.management.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "org.w3c.*"})
public class EventMeshGrpcProducerTest {

    private EventMeshGrpcProducer producer;
    @Mock
    private CloudEventProducer cloudEventProducer;
    @Mock
    private PublisherServiceBlockingStub stub;

    @Mock
    private EventMeshMessageProducer eventMeshMessageProducer;

    @Before
    public void setUp() throws Exception {
        producer = new EventMeshGrpcProducer(EventMeshGrpcClientConfig.builder().build());
        producer.setCloudEventProducer(cloudEventProducer);
        producer.setEventMeshMessageProducer(eventMeshMessageProducer);
        producer.setPublisherClient(stub);

        doThrow(RuntimeException.class).when(stub).publish(
            argThat(argument -> argument != null && StringUtils.equals(EventMeshCloudEventUtils.getDataContent(argument),
                "mockExceptionContent")));
        doReturn(CloudEvent.getDefaultInstance()).when(stub).publish(
            argThat(argument -> argument != null && StringUtils.equals(EventMeshCloudEventUtils.getDataContent(argument), "mockContent")));
        doReturn(CloudEvent.getDefaultInstance()).when(stub).batchPublish(
            argThat(argument -> argument != null && StringUtils.equals(EventMeshCloudEventUtils.getSubject(argument.getEvents(0)), "mockTopic")));
        doReturn(stub).when(stub).withDeadlineAfter(1000L, TimeUnit.MILLISECONDS);
        when(cloudEventProducer.publish(anyList())).thenReturn(Response.builder().build());
        when(cloudEventProducer.publish(Mockito.isA(io.cloudevents.CloudEvent.class))).thenReturn(Response.builder().build());
        when(eventMeshMessageProducer.publish(anyList())).thenReturn(Response.builder().build());
        when(eventMeshMessageProducer.publish(Mockito.isA(EventMeshMessage.class))).thenReturn(Response.builder().build());
        doAnswer(invocation -> {
            EventMeshMessage eventMeshMessage = invocation.getArgument(0);
            if (StringUtils.isEmpty(eventMeshMessage.getContent())) {
                return null;
            }
            return eventMeshMessage;
        }).when(eventMeshMessageProducer).requestReply(any(), Mockito.anyLong());

    }

    @Test
    public void testPublishWithException() {
        try {
            producer.publish(defaultEventMeshMessageBuilder().content("mockExceptionContent").build());
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    public void testPublishEventMeshMessage() {
        producer.publish(defaultEventMeshMessageBuilder().build());
    }

    @Test
    public void testPublishEmptyList() {
        producer.publish(Collections.emptyList());
    }

    @Test
    public void testPublishGenericMessageList() {
        producer.publish(Collections.singletonList(new MockCloudEvent()));
        EventMeshMessageBuilder eventMeshMessageBuilder = defaultEventMeshMessageBuilder();
        eventMeshMessageBuilder.prop(Collections.singletonMap(Constants.EVENTMESH_MESSAGE_CONST_TTL, "1000"));
        producer.publish(Collections.singletonList(eventMeshMessageBuilder.build()));
    }

    @Test
    public void testRequestReply() {
        assertThat(producer.request(defaultEventMeshMessageBuilder().content(StringUtils.EMPTY).build(),
            1000L)).isNull();
        EventMeshMessage eventMeshMessage = defaultEventMeshMessageBuilder().build();
        assertThat(producer.request(eventMeshMessage, 1000L)).hasFieldOrPropertyWithValue("content",
            eventMeshMessage.getContent()).hasFieldOrPropertyWithValue("topic", eventMeshMessage.getTopic());
    }

    private EventMeshMessage.EventMeshMessageBuilder defaultEventMeshMessageBuilder() {
        return EventMeshMessage.builder().bizSeqNo("bizSeqNo").content("mockContent")
            .createTime(System.currentTimeMillis()).uniqueId("mockUniqueId").topic("mockTopic")
            .prop(Collections.emptyMap());
    }
}
