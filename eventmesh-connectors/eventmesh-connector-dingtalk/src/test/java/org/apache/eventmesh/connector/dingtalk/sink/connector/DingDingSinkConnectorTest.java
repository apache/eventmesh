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

package org.apache.eventmesh.connector.dingtalk.sink.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.eventmesh.common.config.connector.dingtalk.DingDingSinkConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.connector.dingtalk.common.constants.ConnectRecordExtensionKeys;
import org.apache.eventmesh.connector.dingtalk.config.DingDingMessageTemplateType;
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
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponseBody;

@ExtendWith(MockitoExtension.class)
public class DingDingSinkConnectorTest {

    @Spy
    private DingDingSinkConnector connector;

    @Mock
    private com.aliyun.dingtalkrobot_1_0.Client sendMessageClient;

    @Mock
    private com.aliyun.dingtalkoauth2_1_0.Client authClient;

    @BeforeEach
    public void setUp() throws Exception {
        Mockito.doReturn(null).when(sendMessageClient)
            .orgGroupSendWithOptions(Mockito.any(), Mockito.any(), Mockito.any());
        GetAccessTokenResponse response = new GetAccessTokenResponse();
        GetAccessTokenResponseBody body = new GetAccessTokenResponseBody();
        body.setAccessToken("testAccessToken");
        response.setBody(body);
        Mockito.doReturn(response).when(authClient).getAccessToken(Mockito.any());

        DingDingSinkConfig sinkConfig = (DingDingSinkConfig) ConfigUtil.parse(connector.configClass());
        connector.init(sinkConfig);
        Field sendMessageClientField = ReflectionSupport.findFields(connector.getClass(),
            (f) -> f.getName().equals("sendMessageClient"),
            HierarchyTraversalMode.BOTTOM_UP).get(0);
        Field authClientField = ReflectionSupport.findFields(connector.getClass(),
            (f) -> f.getName().equals("authClient"),
            HierarchyTraversalMode.BOTTOM_UP).get(0);
        sendMessageClientField.setAccessible(true);
        authClientField.setAccessible(true);
        sendMessageClientField.set(connector, sendMessageClient);
        authClientField.set(connector, authClient);
        connector.start();
    }

    @Test
    public void testSendMessageToDingDing() throws Exception {
        final int times = 3;
        List<ConnectRecord> records = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset,
                System.currentTimeMillis(), "Hello, EventMesh!".getBytes(StandardCharsets.UTF_8));
            connectRecord.addExtension(ConnectRecordExtensionKeys.DINGTALK_TEMPLATE_TYPE,
                DingDingMessageTemplateType.PLAIN_TEXT.getTemplateType());
            records.add(connectRecord);
        }
        connector.put(records);
        verify(sendMessageClient, times(times)).orgGroupSendWithOptions(any(), any(), any());
        // verify for access token cache.
        verify(authClient, times(1)).getAccessToken(any());
    }

    @AfterEach
    public void tearDown() {
        DingDingSinkConnector.AUTH_CACHE.invalidate(DingDingSinkConnector.ACCESS_TOKEN_CACHE_KEY);
        connector.stop();
    }
}
