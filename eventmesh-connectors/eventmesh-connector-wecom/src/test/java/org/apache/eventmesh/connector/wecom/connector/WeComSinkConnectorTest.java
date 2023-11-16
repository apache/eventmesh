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

package org.apache.eventmesh.connector.wecom.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.wecom.config.WeComMessageTemplateType;
import org.apache.eventmesh.connector.wecom.constants.ConnectRecordExtensionKeys;
import org.apache.eventmesh.connector.wecom.sink.config.WeComSinkConfig;
import org.apache.eventmesh.connector.wecom.sink.connector.AccessTokenDTO;
import org.apache.eventmesh.connector.wecom.sink.connector.SendMessageDTO;
import org.apache.eventmesh.connector.wecom.sink.connector.WeComSinkConnector;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
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

@ExtendWith(MockitoExtension.class)
public class WeComSinkConnectorTest {

    @Spy
    private WeComSinkConnector connector;

    @Mock
    private CloseableHttpClient httpClient;

    @BeforeEach
    public void setUp() throws Exception {
        Mockito.doReturn(JsonUtils.toJSONString(new AccessTokenDTO().setAccessToken("testAccessToken")))
            .when(httpClient).execute(any(HttpGet.class), any(ResponseHandler.class));
        Mockito.doReturn(JsonUtils.toJSONString(new SendMessageDTO()))
            .when(httpClient).execute(any(HttpPost.class), any(ResponseHandler.class));
        WeComSinkConfig sinkConfig = (WeComSinkConfig) ConfigUtil.parse(connector.configClass());
        connector.init(sinkConfig);
        Field httpClientField = ReflectionSupport.findFields(connector.getClass(),
            (f) -> f.getName().equals("httpClient"),
            HierarchyTraversalMode.BOTTOM_UP).get(0);
        httpClientField.setAccessible(true);
        httpClientField.set(connector, httpClient);
        connector.start();
    }

    @Test
    public void testSendMessageToWeCom() throws IOException {
        final int times = 3;
        List<ConnectRecord> records = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset,
                System.currentTimeMillis(), "Hello, EventMesh!".getBytes(StandardCharsets.UTF_8));
            connectRecord.addExtension(ConnectRecordExtensionKeys.WECOM_MESSAGE_TEMPLATE_TYPE_KEY,
                WeComMessageTemplateType.TEXT.getTemplateKey());
            connectRecord.addExtension(ConnectRecordExtensionKeys.WECOM_TO_USER_ID,
                "testUserId");
            records.add(connectRecord);
        }
        connector.put(records);
        verify(httpClient, times(times)).execute(any(HttpPost.class), any(ResponseHandler.class));
        // verify for access token cache.
        verify(httpClient, times(1)).execute(any(HttpGet.class), any(ResponseHandler.class));
    }

    @AfterEach
    public void tearDown() {
        WeComSinkConnector.AUTH_CACHE.invalidate(WeComSinkConnector.ACCESS_TOKEN_CACHE_KEY);
        connector.stop();
    }
}