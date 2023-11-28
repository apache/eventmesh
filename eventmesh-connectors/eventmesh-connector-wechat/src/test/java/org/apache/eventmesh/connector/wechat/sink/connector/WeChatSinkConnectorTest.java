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

package org.apache.eventmesh.connector.wechat.sink.connector;

import static org.mockito.ArgumentMatchers.any;

import org.apache.eventmesh.connector.wechat.sink.config.WeChatSinkConfig;
import org.apache.eventmesh.openconnect.util.ConfigUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.lang.reflect.Field;


@ExtendWith(MockitoExtension.class)
public class WeChatSinkConnectorTest {

    private WeChatSinkConnector weChatSinkConnector;

    @Mock
    private OkHttpClient okHttpClient;

    @BeforeEach
    public void setUp() throws Exception {
        weChatSinkConnector = new WeChatSinkConnector();
        WeChatSinkConfig weChatSinkConfig = (WeChatSinkConfig) ConfigUtil.parse(weChatSinkConnector.configClass());
        weChatSinkConnector.init(weChatSinkConfig);
        Response mockedResponse = Mockito.mock(Response.class);
        ResponseBody responseBody = Mockito.mock(ResponseBody.class);
        Mockito.doReturn(responseBody).when(mockedResponse).body();
        Mockito.doReturn(mockedResponse).when(okHttpClient).newCall(any(Request.class)).execute();

        Field clientField = ReflectionSupport.findFields(weChatSinkConnector.getClass(),
            (f) -> f.getName().equals("okHttpClient"),
            HierarchyTraversalMode.BOTTOM_UP).get(0);
        clientField.setAccessible(true);
        clientField.set(weChatSinkConnector, okHttpClient);
        weChatSinkConnector.start();
    }

    @Test
    public void testSendMessageToWeChat() throws Exception {
    }

}