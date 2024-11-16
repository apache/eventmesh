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

package org.apache.eventmesh.storage.standalone.producer;

import static org.apache.eventmesh.storage.standalone.TestUtils.TEST_TOPIC;
import static org.apache.eventmesh.storage.standalone.TestUtils.createSubscribe;

import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.storage.standalone.TestUtils;
import org.apache.eventmesh.storage.standalone.broker.StandaloneBroker;
import org.apache.eventmesh.storage.standalone.broker.task.Subscribe;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;



public class StandaloneProducerTest {

    private StandaloneProducer standaloneProducer;


    @BeforeEach
    public void setUp() {
        standaloneProducer = new StandaloneProducer(new Properties());
    }

    @Test
    public void testIsStarted() {
        Assertions.assertFalse(standaloneProducer.isStarted());
    }

    @Test
    public void testIsClosed() {
        Assertions.assertTrue(standaloneProducer.isClosed());
    }

    @Test
    public void testStart() {
        standaloneProducer.start();
        Assertions.assertTrue(standaloneProducer.isStarted());
    }

    @Test
    public void testShutdown() {
        standaloneProducer.shutdown();
        Assertions.assertTrue(standaloneProducer.isClosed());
    }

    @Test
    public void testPublish() {
        StandaloneBroker standaloneBroker = StandaloneBroker.getInstance();
        standaloneBroker.createTopicIfAbsent(TEST_TOPIC);
        CloudEvent cloudEvent = TestUtils.createDefaultCloudEvent();
        Subscribe subscribe = createSubscribe(standaloneBroker);
        subscribe.subscribe();
        SendResult sendResult = standaloneProducer.publish(cloudEvent);
        Assertions.assertNotNull(sendResult);
    }
}
