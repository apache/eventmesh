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
import static org.mockito.Mockito.when;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import io.cloudevents.core.v1.CloudEventV1;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PublisherServiceBlockingStub.class, Response.class})
@PowerMockIgnore({"javax.management.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "org.w3c.*"})
public class CloudEventProducerTest {

    private CloudEventProducer cloudEventProducer;
    @Mock
    private PublisherServiceBlockingStub blockingStub;
    @Mock
    private Response mockResponse;

    @Before
    public void setUp() throws Exception {
        cloudEventProducer = new CloudEventProducer(EventMeshGrpcClientConfig.builder().build(), blockingStub);
        when(blockingStub.batchPublish(Mockito.isA(BatchMessage.class))).thenReturn(mockResponse);
        when(blockingStub.publish(Mockito.isA(SimpleMessage.class))).thenReturn(mockResponse);
    }

    @Test
    public void testEnhanceCloudEvent() {
        MockCloudEvent mockCloudEvent = new MockCloudEvent();
        Object enhanceCloudEvent;
        try {
            enhanceCloudEvent = Whitebox.invokeMethod(cloudEventProducer, "enhanceCloudEvent", mockCloudEvent,
                null);
        } catch (Exception e) {
            enhanceCloudEvent = null;
        }
        assertThat(enhanceCloudEvent).isNotNull().isInstanceOf(CloudEventV1.class)
            .hasFieldOrPropertyWithValue("id", mockCloudEvent.getId())
            .hasFieldOrPropertyWithValue("source", mockCloudEvent.getSource())
            .hasFieldOrPropertyWithValue("type", mockCloudEvent.getType())
            .hasFieldOrPropertyWithValue("dataschema", mockCloudEvent.getDataSchema())
            .hasFieldOrPropertyWithValue("subject", mockCloudEvent.getSubject())
            .hasFieldOrPropertyWithValue("data", mockCloudEvent.getData());
    }

    @Test
    public void testPublishMultiWithEmpty() {
        assertThat(cloudEventProducer.publish(Collections.emptyList())).isNull();
    }

    @Test
    public void testPublishMulti() {
        assertThat(cloudEventProducer.publish(Collections.singletonList(new MockCloudEvent()))).isEqualTo(mockResponse);
    }

    @Test
    public void testPublishSingle() {
        assertThat(cloudEventProducer.publish(new MockCloudEvent())).isEqualTo(mockResponse);
    }
}