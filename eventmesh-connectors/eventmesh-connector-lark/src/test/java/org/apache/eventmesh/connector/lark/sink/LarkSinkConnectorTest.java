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

package org.apache.eventmesh.connector.lark.sink;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.config.connector.lark.LarkSinkConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.connector.lark.sink.connector.LarkSinkConnector;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LarkSinkConnectorTest {

    private LarkSinkConnector larkSinkConnector;

    private LarkSinkConfig sinkConfig;

    /**
     * more test see {@link ImServiceHandlerTest}
     */
    @Mock
    private ImServiceHandler imServiceHandler;

    private MockedStatic<ImServiceHandler> imServiceWrapperMockedStatic;

    @BeforeEach
    public void setup() throws Exception {
        imServiceWrapperMockedStatic = mockStatic(ImServiceHandler.class);
        when(ImServiceHandler.create(any())).thenReturn(imServiceHandler);
        doNothing().when(imServiceHandler).sink(any(ConnectRecord.class));
        doNothing().when(imServiceHandler).sinkAsync(any(ConnectRecord.class));

        larkSinkConnector = new LarkSinkConnector();
        sinkConfig = (LarkSinkConfig) ConfigUtil.parse(larkSinkConnector.configClass());
        SinkConnectorContext sinkConnectorContext = new SinkConnectorContext();
        sinkConnectorContext.setSinkConfig(sinkConfig);
        larkSinkConnector.init(sinkConnectorContext);
        larkSinkConnector.start();
    }

    @Test
    public void testPut() throws Exception {
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
        if (Boolean.parseBoolean(sinkConfig.getSinkConnectorConfig().getSinkAsync())) {
            verify(imServiceHandler, times(times)).sinkAsync(any(ConnectRecord.class));
        } else {
            verify(imServiceHandler, times(times)).sink(any(ConnectRecord.class));
        }
    }

    @AfterEach
    public void tearDown() {
        LarkSinkConnector.AUTH_CACHE.invalidate(LarkSinkConnector.TENANT_ACCESS_TOKEN);
        larkSinkConnector.stop();
        imServiceWrapperMockedStatic.close();
    }
}
