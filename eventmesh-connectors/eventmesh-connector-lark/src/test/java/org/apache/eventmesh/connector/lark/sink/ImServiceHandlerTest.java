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

import static org.apache.eventmesh.connector.lark.sink.ImServiceHandler.create;
import static org.apache.eventmesh.connector.lark.sink.connector.LarkSinkConnector.AUTH_CACHE;
import static org.apache.eventmesh.connector.lark.sink.connector.LarkSinkConnector.TENANT_ACCESS_TOKEN;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.config.connector.lark.LarkSinkConfig;
import org.apache.eventmesh.common.config.connector.lark.SinkConnectorConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.lark.oapi.service.im.v1.ImService;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ImServiceHandlerTest {

    private SinkConnectorConfig sinkConnectorConfig;

    private ImServiceHandler imServiceHandler;

    @Mock
    private ImService imService;

    @Mock
    private ImService.Message message;

    @BeforeEach
    public void setup() throws Exception {
        sinkConnectorConfig = ((LarkSinkConfig) ConfigUtil.parse(LarkSinkConfig.class)).getSinkConnectorConfig();
    }

    private void init() throws Exception {
        // prevent rely on Lark's ExtService
        AUTH_CACHE.put(TENANT_ACCESS_TOKEN, "test-TenantAccessToken");

        imServiceHandler = create(sinkConnectorConfig);

        // prevent rely on Lark's ImService
        when(message.create(any(), any())).thenReturn(new CreateMessageResp());
        when(imService.message()).thenReturn(message);
        Field imServiceField = ReflectionSupport.findFields(imServiceHandler.getClass(),
            (f) -> f.getName().equals("imService"),
            HierarchyTraversalMode.BOTTOM_UP).get(0);
        imServiceField.setAccessible(true);
        imServiceField.set(imServiceHandler, imService);
    }

    @Test
    public void testRegularSink() throws Exception {
        sinkConnectorConfig.setSinkAsync("false");
        init();
        regularSink();
    }

    @Test
    public void testRegularSinkAsync() throws Exception {
        sinkConnectorConfig.setSinkAsync("true");
        init();
        regularSink();
    }

    private void regularSink() throws Exception {
        final int times = 3;
        for (int i = 0; i < times; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset,
                System.currentTimeMillis(), "test-lark".getBytes(StandardCharsets.UTF_8));
            if (Boolean.parseBoolean(sinkConnectorConfig.getSinkAsync())) {
                imServiceHandler.sinkAsync(connectRecord);
                long retryDelayInMills = Long.parseLong(sinkConnectorConfig.getRetryDelayInMills());
                long duration = retryDelayInMills * times;
                Thread.sleep(duration);
            } else {
                imServiceHandler.sink(connectRecord);
            }
        }
        verify(message, times(times)).create(any(), any());
    }

    @Test
    public void testRetrySink() throws Exception {
        sinkConnectorConfig.setSinkAsync("false");
        init();
        retrySink();
    }

    @Test
    public void testRetrySinkAsync() throws Exception {
        sinkConnectorConfig.setSinkAsync("true");
        init();
        retrySink();
    }

    private void retrySink() throws Exception {
        CreateMessageResp resp = new CreateMessageResp();
        resp.setCode(1);
        doReturn(resp).when(message).create(any(), any());
        final int times = 3;
        long retryDelayInMills = Long.parseLong(sinkConnectorConfig.getRetryDelayInMills());
        int maxRetryTimes = Integer.parseInt(sinkConnectorConfig.getMaxRetryTimes());
        // (maxRetryTimes + 1) event are actually sent
        int sinkTimes = times * (maxRetryTimes + 1);
        long duration = retryDelayInMills * sinkTimes;

        for (int i = 0; i < times; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord connectRecord = new ConnectRecord(partition, offset,
                System.currentTimeMillis(), "test-lark".getBytes(StandardCharsets.UTF_8));
            if (Boolean.parseBoolean(sinkConnectorConfig.getSinkAsync())) {
                imServiceHandler.sinkAsync(connectRecord);

                Thread.sleep(duration);
            } else {
                Assertions.assertThrows(Exception.class, () -> imServiceHandler.sink(connectRecord));

            }
        }
        verify(message, times(sinkTimes)).create(any(), any());
    }
}
