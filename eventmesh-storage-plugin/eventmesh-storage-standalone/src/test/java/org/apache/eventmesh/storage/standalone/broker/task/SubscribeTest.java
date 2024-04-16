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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.storage.standalone.broker.StandaloneBroker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SubscribeTest {

    @Mock
    private StandaloneBroker standaloneBroker;
    @Mock
    private EventListener eventListener;
    private Subscribe subscribe;

    @Test
    public void testSubscribe() {
        subscribe = new Subscribe(TEST_TOPIC, standaloneBroker, eventListener);
        subscribe.subscribe();
        Mockito.verify(standaloneBroker).subscribed(anyString(), any(Subscribe.class));
    }

    @Test
    public void testShutdown() {
        subscribe = new Subscribe(TEST_TOPIC, standaloneBroker, eventListener);
        Assertions.assertTrue(subscribe.isRunning());
    }
}
