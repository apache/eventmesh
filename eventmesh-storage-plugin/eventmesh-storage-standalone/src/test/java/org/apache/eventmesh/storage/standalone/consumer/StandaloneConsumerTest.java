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

package org.apache.eventmesh.storage.standalone.consumer;

import static org.apache.eventmesh.storage.standalone.TestUtils.createCloudEvents;

import org.apache.eventmesh.storage.standalone.broker.task.Subscribe;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.cloudevents.CloudEvent;

@ExtendWith(MockitoExtension.class)
public class StandaloneConsumerTest {

    @Mock
    private ConcurrentHashMap<String, Subscribe> subscribeTable;
    private StandaloneConsumer standaloneConsumer;
    private List<CloudEvent> cloudEvents;

    @BeforeEach
    public void setUp() {
        standaloneConsumer = new StandaloneConsumer(new Properties());
        cloudEvents = createCloudEvents();
    }

    @Test
    public void testIsStarted() {
        Assertions.assertFalse(standaloneConsumer.isStarted());
    }

    @Test
    public void testIsClosed() {
        Assertions.assertTrue(standaloneConsumer.isClosed());
    }

    @Test
    public void testStart() {
        standaloneConsumer.start();
        Assertions.assertTrue(standaloneConsumer.isStarted());
    }

    @Test
    public void testShutdown() {
        standaloneConsumer.shutdown();
        Assertions.assertTrue(standaloneConsumer.isClosed());
    }
}
