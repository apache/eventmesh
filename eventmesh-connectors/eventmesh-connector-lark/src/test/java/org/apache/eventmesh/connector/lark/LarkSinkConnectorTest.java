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

package org.apache.eventmesh.connector.lark;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.ImService;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import org.apache.eventmesh.connector.lark.sink.config.LarkSinkConfig;
import org.apache.eventmesh.connector.lark.sink.connector.LarkSinkConnector;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.util.ConfigUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LarkSinkConnectorTest {

    @InjectMocks
    private LarkSinkConnector larkSinkConnector;

    @InjectMocks
    private ImService imService;

    @Mock
    private ImService.Message message;


    @BeforeEach
    public void setup() throws Exception {
        // todo imService and retryer mock
        Mockito.when(message.create(any(CreateMessageReq.class))).thenReturn(new CreateMessageResp());
        LarkSinkConfig larkConnectServerConfig = (LarkSinkConfig) ConfigUtil.parse(larkSinkConnector.configClass());

        /*message = Client.newBuilder(larkConnectServerConfig.getSinkConnectorConfig().getAppId(),
                        larkConnectServerConfig.getSinkConnectorConfig().getAppSecret())
                .requestTimeout(3, TimeUnit.SECONDS).build().im();*/

        SinkConnectorContext sinkConnectorContext = new SinkConnectorContext();
        sinkConnectorContext.setSinkConfig(larkConnectServerConfig);
        larkSinkConnector.init(sinkConnectorContext);

        larkSinkConnector.start();
    }

    @Test
    public void testRegularSink() throws Exception {
        final int times = 3;
        List<ConnectRecord> connectRecords = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset,
                    System.currentTimeMillis(), "test-lark".getBytes(StandardCharsets.UTF_8));
            connectRecords.add(connectRecord);
        }
        larkSinkConnector.put(connectRecords);

        verify(message, times(times)).create(any(CreateMessageReq.class));

    }

    @Test
    public void testRetrySink() throws Exception {
        final int times = 3;
        List<ConnectRecord> connectRecords = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset,
                    System.currentTimeMillis(), "test-lark".getBytes(StandardCharsets.UTF_8));
            connectRecords.add(connectRecord);
        }
        larkSinkConnector.put(connectRecords);

        verify(message, times(times)).create(any(CreateMessageReq.class));

    }

    @AfterEach
    public void tearDown() {
        larkSinkConnector.stop();
    }

}
