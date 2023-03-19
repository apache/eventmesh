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

package org.apache.eventmesh.storage.standalone.admin;

import static org.apache.eventmesh.storage.standalone.TestUtils.LENGTH;
import static org.apache.eventmesh.storage.standalone.TestUtils.OFF_SET;
import static org.apache.eventmesh.storage.standalone.TestUtils.TEST_TOPIC;
import static org.apache.eventmesh.storage.standalone.TestUtils.TOPIC_DO_NOT_EXISTS;
import static org.apache.eventmesh.storage.standalone.TestUtils.TOPIC_EXISTS;
import static org.apache.eventmesh.storage.standalone.TestUtils.createDefaultCloudEvent;
import static org.apache.eventmesh.storage.standalone.TestUtils.createDefaultMessageContainer;
import static org.apache.eventmesh.storage.standalone.TestUtils.createDefaultMessageEntity;

import org.apache.eventmesh.storage.standalone.broker.StandaloneBroker;
import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;

import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import io.cloudevents.CloudEvent;

@RunWith(MockitoJUnitRunner.class)
public class StandaloneAdminTest {

    @Mock
    private StandaloneBroker standaloneBroker;

    private StandaloneAdmin standaloneAdmin;

    @Before
    public void setUp() {
        initStaticInstance();
    }

    @Test
    public void testIsStarted() {
        standaloneAdmin.start();
        Assert.assertTrue(standaloneAdmin.isStarted());
    }

    @Test
    public void testIsClosed() {
        standaloneAdmin.shutdown();
        Assert.assertTrue(standaloneAdmin.isClosed());
    }

    @Test
    public void testGetTopic() throws Exception {
        Assert.assertNotNull(standaloneAdmin.getTopic());
        Assert.assertFalse(standaloneAdmin.getTopic().isEmpty());
    }

    @Test
    public void testCreateTopic() {
        standaloneAdmin.createTopic(TEST_TOPIC);
        Mockito.verify(standaloneBroker).createTopicIfAbsent(TEST_TOPIC);
    }

    @Test
    public void testDeleteTopic() {
        standaloneAdmin.deleteTopic(TEST_TOPIC);
        Mockito.verify(standaloneBroker).deleteTopicIfExist(TEST_TOPIC);
    }

    @Test
    public void testGetEvent() throws Exception {
        Mockito.when(standaloneBroker.checkTopicExist(TEST_TOPIC)).thenReturn(TOPIC_EXISTS);
        Mockito.when(standaloneBroker.getMessage(TEST_TOPIC, OFF_SET)).thenReturn(createDefaultCloudEvent());
        List<CloudEvent> events = standaloneAdmin.getEvent(TEST_TOPIC, OFF_SET, LENGTH);
        Assert.assertNotNull(events);
        Assert.assertFalse(events.isEmpty());
    }

    @Test
    public void testGetEvent_throwException() {
        Mockito.when(standaloneBroker.checkTopicExist(TEST_TOPIC)).thenReturn(TOPIC_DO_NOT_EXISTS);
        Exception exception = Assert.assertThrows(Exception.class, () -> standaloneAdmin.getEvent(TEST_TOPIC, OFF_SET, LENGTH));
        Assert.assertEquals("The topic name doesn't exist in the message queue", exception.getMessage());
    }

    @Test
    public void testPublish() throws Exception {
        CloudEvent cloudEvent = createDefaultCloudEvent();
        MessageEntity messageEntity = createDefaultMessageEntity();
        Mockito.when(standaloneBroker.putMessage(TEST_TOPIC, cloudEvent)).thenReturn(messageEntity);
        standaloneAdmin.publish(cloudEvent);
        Mockito.verify(standaloneBroker).putMessage(TEST_TOPIC, cloudEvent);
    }

    private void initStaticInstance() {
        try (MockedStatic<StandaloneBroker> standaloneBrokerMockedStatic = Mockito.mockStatic(StandaloneBroker.class)) {
            standaloneBrokerMockedStatic.when(StandaloneBroker::getInstance).thenReturn(standaloneBroker);
            Mockito.when(standaloneBroker.getMessageContainer()).thenReturn(createDefaultMessageContainer());
            standaloneAdmin = new StandaloneAdmin(new Properties());
        }
    }
}