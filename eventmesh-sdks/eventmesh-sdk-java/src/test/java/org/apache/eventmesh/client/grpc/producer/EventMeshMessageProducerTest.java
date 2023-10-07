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
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.Response;

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
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class EventMeshMessageProducerTest {

    private EventMeshMessageProducer eventMeshMessageProducer;

    @Mock
    private PublisherServiceBlockingStub blockingStub;

    @Mock
    private CloudEvent mockCloudEvent;

    @Before
    public void setUp() throws Exception {
        eventMeshMessageProducer = new EventMeshMessageProducer(EventMeshGrpcClientConfig.builder().build(), blockingStub);
        when(blockingStub.batchPublish(Mockito.isA(CloudEventBatch.class))).thenReturn(mockCloudEvent);
        when(blockingStub.publish(Mockito.isA(CloudEvent.class))).thenReturn(mockCloudEvent);
        when(blockingStub.requestReply(Mockito.isA(CloudEvent.class))).thenReturn(mockCloudEvent);
        doAnswer(invocation -> {
            CloudEvent cloudEvent = invocation.getArgument(0);
            if (StringUtils.isEmpty(EventMeshCloudEventUtils.getDataContent(cloudEvent))) {
                return CloudEvent.getDefaultInstance();
            }
            return CloudEvent.newBuilder(cloudEvent).build();
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
        assertThat(eventMeshMessageProducer.publish(eventMeshMessage)).isEqualTo(Response.builder().build());
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
        assertThat(eventMeshMessageProducer.publish(messageArrayList)).isEqualTo(Response.builder().build());
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
