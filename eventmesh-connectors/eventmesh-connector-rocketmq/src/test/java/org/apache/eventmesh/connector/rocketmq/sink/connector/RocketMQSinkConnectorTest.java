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

package org.apache.eventmesh.connector.rocketmq.sink.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.eventmesh.common.config.connector.mq.rocketmq.RocketMQSinkConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
public class RocketMQSinkConnectorTest {

    @InjectMocks
    private RocketMQSinkConnector sinkConnector;

    @Mock
    private DefaultMQProducer producer;

    private static final String EXPECTED_MESSAGE = "\"testMessage\"";

    @BeforeEach
    public void setUp() throws Exception {
        Mockito.doNothing().when(producer).start();
        Mockito.doReturn(null).when(producer).send(Mockito.any(Message.class));
        Field field = ReflectionSupport.findFields(sinkConnector.getClass(),
            (f) -> f.getName().equals("producer"), HierarchyTraversalMode.BOTTOM_UP).get(0);
        field.setAccessible(true);
        field.set(sinkConnector, producer);
        producer.start();
        RocketMQSinkConfig sinkConfig = (RocketMQSinkConfig) ConfigUtil.parse(sinkConnector.configClass());
        sinkConnector.init(sinkConfig);
        sinkConnector.start();
    }

    @Test
    public void testRocketMQSinkConnector() throws Exception {
        final int messageCount = 5;
        sinkConnector.put(generateMockedRecords(messageCount));
        verify(producer, times(messageCount)).send(any(Message.class));
    }

    private List<ConnectRecord> generateMockedRecords(final int messageCount) {
        List<ConnectRecord> records = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset, System.currentTimeMillis(),
                EXPECTED_MESSAGE.getBytes(StandardCharsets.UTF_8));
            connectRecord.addExtension("id", String.valueOf(UUID.randomUUID()));
            records.add(connectRecord);
        }
        return records;
    }
}
