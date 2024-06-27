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

import static org.apache.eventmesh.storage.standalone.TestUtils.TEST_TOPIC;
import static org.apache.eventmesh.storage.standalone.TestUtils.createDefaultCloudEvent;
import static org.apache.eventmesh.storage.standalone.TestUtils.createDefaultMessageContainer;
import static org.apache.eventmesh.storage.standalone.TestUtils.createDefaultMessageEntity;

import org.apache.eventmesh.storage.standalone.broker.Channel;
import org.apache.eventmesh.storage.standalone.broker.StandaloneBroker;
import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;
import org.apache.eventmesh.storage.standalone.broker.model.TopicMetadata;
import org.apache.eventmesh.storage.standalone.broker.task.Subscribe;

import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.cloudevents.CloudEvent;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class StandaloneAdminTest {

    @Mock
    private StandaloneBroker standaloneBroker;

    private StandaloneAdmin standaloneAdmin;


    @BeforeEach
    public void setUp() {
        initStaticInstance();
    }

    @Test
    public void testIsStarted() {
        standaloneAdmin.start();
        Assertions.assertTrue(standaloneAdmin.isStarted());
    }

    @Test
    public void testIsClosed() {
        standaloneAdmin.shutdown();
        Assertions.assertTrue(standaloneAdmin.isClosed());
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
            Pair<ConcurrentHashMap<TopicMetadata, Channel>, ConcurrentHashMap<TopicMetadata, Subscribe>> pair =
                createDefaultMessageContainer(standaloneBroker);
            Mockito.when(standaloneBroker.getSubscribeContainer()).thenReturn(pair.getRight());
            Mockito.when(standaloneBroker.getMessageContainer()).thenReturn(pair.getLeft());

            standaloneAdmin = new StandaloneAdmin();
        }
    }
}
