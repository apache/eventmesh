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

package org.apache.eventmesh.connector.slack.sink.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.eventmesh.common.config.connector.slack.SlackSinkConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.slack.api.RequestConfigurator;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;

@ExtendWith(MockitoExtension.class)
public class SlackSinkConnectorTest {

    private SlackSinkConnector sinkConnector;

    @Mock
    private MethodsClient client;

    @BeforeEach
    public void setUp() throws Exception {
        sinkConnector = new SlackSinkConnector();
        SlackSinkConfig sinkConfig = (SlackSinkConfig) ConfigUtil.parse(sinkConnector.configClass());
        sinkConnector.init(sinkConfig);
        doReturn(new ChatPostMessageResponse())
            .when(client).chatPostMessage(any(RequestConfigurator.class));
        Field clientField = ReflectionSupport.findFields(sinkConnector.getClass(),
            (f) -> f.getName().equals("client"),
            HierarchyTraversalMode.BOTTOM_UP).get(0);
        clientField.setAccessible(true);
        clientField.set(sinkConnector, client);
        sinkConnector.start();
    }

    @Test
    public void testSendMessageToSlack() throws Exception {
        final int times = 3;
        List<ConnectRecord> records = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset,
                System.currentTimeMillis(), "Hello, EventMesh!".getBytes(StandardCharsets.UTF_8));
            records.add(connectRecord);
        }
        sinkConnector.put(records);
        verify(client, times(times)).chatPostMessage(any(RequestConfigurator.class));
    }

    @AfterEach
    public void tearDown() {
        sinkConnector.stop();
    }
}
