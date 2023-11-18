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

package org.apache.eventmesh.connector.s3.source;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.apache.eventmesh.connector.feishu.sink.config.FeishuSinkConfig;
import org.apache.eventmesh.connector.feishu.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.connector.feishu.sink.connector.FeishuSinkConnector;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lark.oapi.Client;
import com.lark.oapi.core.response.RawResponse;

@ExtendWith(MockitoExtension.class)
public class FeishuSinkConnectorTest {

    private static final FeishuSinkConfig sinkConfig;

    private static final SinkConnectorConfig SINK_CONNECTOR_CONFIG;

    static {
        sinkConfig = new FeishuSinkConfig();
        SINK_CONNECTOR_CONFIG = new SinkConnectorConfig();
        SINK_CONNECTOR_CONFIG.setConnectorName("FeishuSinkConnector");
        SINK_CONNECTOR_CONFIG.setAppId("xxx");
        SINK_CONNECTOR_CONFIG.setAppSecret("xxx");
        SINK_CONNECTOR_CONFIG.setReceiveId("xxx");
        SINK_CONNECTOR_CONFIG.setReceiveIdType("open_id");
        sinkConfig.setConnectorConfig(SINK_CONNECTOR_CONFIG);
    }

    @Spy
    private FeishuSinkConnector feishuSinkConnector;

    @Mock
    private Client feishuClient;

    @BeforeEach
    public void setup() throws Exception {
        RawResponse response = new RawResponse();
        response.setStatusCode(200);
        Mockito.doReturn(response).when(feishuClient).post(Mockito.any(), Mockito.any(), Mockito.any());

        Field feishuClientField = ReflectionSupport.findFields(feishuSinkConnector.getClass(),
            (f) -> f.getName().equals("feishuClient"),
            HierarchyTraversalMode.BOTTOM_UP).get(0);

        feishuClientField.setAccessible(true);
        feishuClientField.set(feishuSinkConnector, feishuClient);

        Field sinkConfigField = ReflectionSupport.findFields(feishuSinkConnector.getClass(),
            (f) -> f.getName().equals("sinkConfig"),
            HierarchyTraversalMode.BOTTOM_UP).get(0);

        sinkConfigField.setAccessible(true);
        sinkConfigField.set(feishuSinkConnector, sinkConfig);
    }

    @Test
    public void testFeishuSinkConnector() {
        assertDoesNotThrow(() -> {
            feishuSinkConnector.start();
            List<ConnectRecord> connectRecords = new ArrayList<>();
            connectRecords.add(new ConnectRecord(null, null, 0L, "test"));
            feishuSinkConnector.put(connectRecords);
        });
    }

}
