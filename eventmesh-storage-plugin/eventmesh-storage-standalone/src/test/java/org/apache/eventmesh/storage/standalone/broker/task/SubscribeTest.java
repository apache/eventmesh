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

package org.apache.eventmesh.storage.standalone.broker.task;

import static org.apache.eventmesh.storage.standalone.TestUtils.TEST_TOPIC;
import static org.apache.eventmesh.storage.standalone.TestUtils.createDefaultCloudEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.storage.standalone.broker.StandaloneBroker;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import io.cloudevents.CloudEvent;

@RunWith(MockitoJUnitRunner.class)
public class SubscribeTest {

    @Mock
    private StandaloneBroker standaloneBroker;
    @Mock
    private EventListener eventListener;
    private Subscribe subscribe;

    @Test
    public void testSubscribe() {
        CloudEvent cloudEvent = createDefaultCloudEvent();
        Mockito.when(standaloneBroker.getMessage(anyString())).thenReturn(cloudEvent);
        Mockito.when(standaloneBroker.getMessage(anyString(), anyLong())).thenReturn(cloudEvent);
        subscribe = new Subscribe(TEST_TOPIC, standaloneBroker, eventListener);
        subscribe.subscribe();
        Mockito.verify(eventListener).consume(any(CloudEvent.class), any(EventMeshAsyncConsumeContext.class));
    }

    @Test
    public void testShutdown() {
        subscribe = new Subscribe(TEST_TOPIC, standaloneBroker, eventListener);
        Assert.assertTrue(subscribe.isRunning());
    }
}
