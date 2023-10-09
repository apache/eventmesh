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
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.common.Response;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CloudEventProducerTest {

    private CloudEventProducer cloudEventProducer;
    @Mock
    private PublisherServiceBlockingStub blockingStub;

    @Mock
    private CloudEvent mockCloudEvent;

    @BeforeEach
    public void setUp() throws Exception {
        cloudEventProducer = new CloudEventProducer(EventMeshGrpcClientConfig.builder().build(), blockingStub);
        when(blockingStub.batchPublish(Mockito.isA(CloudEventBatch.class))).thenReturn(mockCloudEvent);
        when(blockingStub.publish(Mockito.isA(CloudEvent.class))).thenReturn(mockCloudEvent);
    }

    @Test
    public void testPublishMultiWithEmpty() {
        assertThat(cloudEventProducer.publish(Collections.emptyList())).isNull();
    }

    @Test
    public void testPublishMulti() {
        assertThat(cloudEventProducer.publish(Collections.singletonList(new MockCloudEvent()))).isEqualTo(Response.builder().build());
    }

    @Test
    public void testPublishSingle() {
        assertThat(cloudEventProducer.publish(new MockCloudEvent())).isEqualTo(Response.builder().build());
    }
}
