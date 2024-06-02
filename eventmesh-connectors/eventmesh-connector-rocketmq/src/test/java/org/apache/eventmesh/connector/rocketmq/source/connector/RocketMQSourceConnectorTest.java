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

package org.apache.eventmesh.connector.rocketmq.source.connector;

import org.apache.eventmesh.common.config.connector.mq.rocketmq.RocketMQSourceConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RocketMQSourceConnectorTest {

    @InjectMocks
    private RocketMQSourceConnector sourceConnector;

    @Mock
    private DefaultLitePullConsumer consumer;

    private RocketMQSourceConfig sourceConfig;

    private static final String EXPECTED_MESSAGE = "testMessage";

    @BeforeEach
    public void setUp() throws Exception {
        sourceConfig = (RocketMQSourceConfig) ConfigUtil.parse(sourceConnector.configClass());
        Mockito.doReturn(generateMockedMessages()).when(consumer).poll();
        Field field = ReflectionSupport.findFields(sourceConnector.getClass(),
            (f) -> f.getName().equals("consumer"), HierarchyTraversalMode.BOTTOM_UP).get(0);
        field.setAccessible(true);
        field.set(sourceConnector, consumer);
    }

    @Test
    public void testRocketMQSourceConnectorPoll() {
        List<ConnectRecord> poll = sourceConnector.poll();
        poll.forEach(connectRecord -> {
            Assertions.assertNotNull(connectRecord);
            Assertions.assertEquals(EXPECTED_MESSAGE, connectRecord.getData());
            Assertions.assertNotNull(connectRecord.getExtension("topic"));
            Assertions.assertNotNull(connectRecord.getPosition());
            Assertions.assertEquals(connectRecord.getExtension("topic"), sourceConfig.getConnectorConfig().getTopic());
        });
    }

    private List<MessageExt> generateMockedMessages() {
        final int mockCount = 5;
        List<MessageExt> messageExts = new ArrayList<>();
        for (int i = 0; i < mockCount; i++) {
            MessageExt messageExt = new MessageExt();
            messageExt.setTopic(sourceConfig.getConnectorConfig().getTopic());
            messageExt.setBody(EXPECTED_MESSAGE.getBytes(StandardCharsets.UTF_8));
            messageExt.setQueueOffset(1L);
            messageExt.setQueueId(2);
            messageExt.setBrokerName("testBroker");
            messageExts.add(messageExt);
        }
        return messageExts;
    }
}
