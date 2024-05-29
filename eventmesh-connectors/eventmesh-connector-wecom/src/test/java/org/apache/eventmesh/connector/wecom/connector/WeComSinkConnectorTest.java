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

import org.apache.eventmesh.common.config.connector.wecom.WeComSinkConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.wecom.config.WeComMessageTemplateType;
import org.apache.eventmesh.connector.wecom.constants.ConnectRecordExtensionKeys;
import org.apache.eventmesh.connector.wecom.sink.connector.SendMessageResponse;
import org.apache.eventmesh.connector.wecom.sink.connector.WeComSinkConnector;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class WeComSinkConnectorTest {

    private WeComSinkConnector connector;

    @Mock
    private CloseableHttpClient httpClient;

    @BeforeEach
    public void setUp() throws Exception {
        connector = new WeComSinkConnector();
        CloseableHttpResponse mockedResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
        Mockito.doReturn(mockedResponse).when(httpClient).execute(any(HttpPost.class));
        Mockito.doReturn(httpEntity).when(mockedResponse).getEntity();
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
        try (MockedStatic<EntityUtils> entityUtilsMockedStatic = Mockito.mockStatic(EntityUtils.class)) {
            entityUtilsMockedStatic.when(() -> EntityUtils.toString(any(HttpEntity.class), any(Charset.class)))
                .thenReturn(JsonUtils.toJSONString(new SendMessageResponse()));
            final int times = 3;
            List<ConnectRecord> records = new ArrayList<>();
            for (int i = 0; i < times; i++) {
                RecordPartition partition = new MockRecordPartition();
                RecordOffset offset = new MockRecordOffset();
                ConnectRecord connectRecord = new ConnectRecord(partition, offset,
                    System.currentTimeMillis(), "Hello, EventMesh!".getBytes(StandardCharsets.UTF_8));
                connectRecord.addExtension(ConnectRecordExtensionKeys.WECOM_MESSAGE_TEMPLATE_TYPE,
                    WeComMessageTemplateType.PLAIN_TEXT.getTemplateType());
                records.add(connectRecord);
            }
            connector.put(records);
            verify(httpClient, times(times)).execute(any(HttpPost.class));
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        connector.stop();
        httpClient.close();
    }
}
