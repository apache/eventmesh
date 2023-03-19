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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.cloudevents.CloudEvent;

@RunWith(MockitoJUnitRunner.class)
public class StandaloneConsumerTest {

    @Mock
    private ConcurrentHashMap<String, Subscribe> subscribeTable;
    private StandaloneConsumer standaloneConsumer;
    private List<CloudEvent> cloudEvents;

    @Before
    public void setUp() {
        standaloneConsumer = new StandaloneConsumer(new Properties());
        cloudEvents = createCloudEvents();
    }

    @Test
    public void testIsStarted() {
        Assert.assertFalse(standaloneConsumer.isStarted());
    }

    @Test
    public void testIsClosed() {
        Assert.assertTrue(standaloneConsumer.isClosed());
    }

    @Test
    public void testStart() {
        standaloneConsumer.start();
        Assert.assertTrue(standaloneConsumer.isStarted());
    }

    @Test
    public void testShutdown() {
        standaloneConsumer.shutdown();
        Assert.assertTrue(standaloneConsumer.isClosed());
    }

    @Test
    public void testUpdateOffset() {
    }

    @Test
    public void subscribe() throws Exception {
    }

    @Test
    public void unsubscribe() {
    }

    @Test
    public void registerEventListener() {
    }
}
