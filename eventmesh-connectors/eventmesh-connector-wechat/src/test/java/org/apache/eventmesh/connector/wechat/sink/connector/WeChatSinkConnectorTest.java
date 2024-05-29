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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.eventmesh.common.config.connector.wechat.WeChatSinkConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@ExtendWith(MockitoExtension.class)
public class WeChatSinkConnectorTest {

    private WeChatSinkConnector weChatSinkConnector;

    @Mock
    private OkHttpClient okHttpClient;

    @BeforeEach
    public void setUp() throws Exception {
        Request tokenRequest = new Request.Builder().url("https://api.weixin.qq.com/cgi-bin/token").build();
        String tokenResponseJson = "{\"access_token\":\"ACCESS_TOKEN\",\"expires_in\":7200}";
        ResponseBody responseBody = ResponseBody.create(MediaType.parse("application/json; charset=utf-8"), tokenResponseJson);
        Response tokenResponse = new Response.Builder()
            .request(tokenRequest)
            .protocol(Protocol.HTTP_1_0)
            .message("ok")
            .code(200)
            .body(responseBody)
            .build();
        ArgumentMatcher<Request> tokenMatcher = (anyRequest) -> tokenRequest.url().encodedPath().startsWith(anyRequest.url().encodedPath());
        Call tokenCall = Mockito.mock(Call.class);
        Mockito.doReturn(tokenCall).when(okHttpClient).newCall(Mockito.argThat(tokenMatcher));
        Mockito.doReturn(tokenResponse).when(tokenCall).execute();

        weChatSinkConnector = new WeChatSinkConnector();
        WeChatSinkConfig weChatSinkConfig = (WeChatSinkConfig) ConfigUtil.parse(weChatSinkConnector.configClass());
        weChatSinkConnector.init(weChatSinkConfig);
        Field clientField = ReflectionSupport.findFields(weChatSinkConnector.getClass(),
            (f) -> f.getName().equals("okHttpClient"),
            HierarchyTraversalMode.BOTTOM_UP).get(0);
        clientField.setAccessible(true);
        clientField.set(weChatSinkConnector, okHttpClient);
        weChatSinkConnector.start();
    }

    @Test
    public void testSendMessageToWeChat() throws Exception {

        Request sendMessageRequest = new Request.Builder().url("https://api.weixin.qq.com/cgi-bin/message/template/send").build();
        String sendMessageResponseJson = "{\"errcode\":0,\"errmsg\":\"ok\",\"msgid\":200228332}";
        ResponseBody sendMessageBody = ResponseBody.create(MediaType.parse("application/json; charset=utf-8"), sendMessageResponseJson);
        Response sendMessageResponse = new Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_1_0)
            .request(sendMessageRequest)
            .body(sendMessageBody)
            .message("ok")
            .build();
        ArgumentMatcher<Request> sendMessageMatcher =
            (anyRequest) -> sendMessageRequest.url().encodedPath().startsWith(anyRequest.url().encodedPath());
        Call sendMessageRequestCall = Mockito.mock(Call.class);
        Mockito.doReturn(sendMessageRequestCall).when(okHttpClient).newCall(Mockito.argThat(sendMessageMatcher));
        Mockito.doReturn(sendMessageResponse).when(sendMessageRequestCall).execute();

        List<ConnectRecord> records = new ArrayList<>();
        RecordPartition partition = new RecordPartition();
        RecordOffset offset = new RecordOffset();
        ConnectRecord connectRecord = new ConnectRecord(partition, offset,
            System.currentTimeMillis(), "Hello, EventMesh!".getBytes(StandardCharsets.UTF_8));
        records.add(connectRecord);

        weChatSinkConnector.put(records);
        verify(okHttpClient, times(2)).newCall(any(Request.class));

        WeChatSinkConnector.ACCESS_TOKEN_CACHE.invalidate(WeChatSinkConnector.ACCESS_TOKEN_CACHE_KEY);
    }

    @Test
    public void testSendMessageToWeChatAbnormally() throws Exception {
        Request sendMessageRequest = new Request.Builder().url("https://api.weixin.qq.com/cgi-bin/message/template/send").build();
        String sendMessageResponseJson = "{\"errcode\":42001,\"errmsg\":\"access_token expired rid: 656e8793-061949b5-738cb8f4\"}";
        ResponseBody sendMessageBody = ResponseBody.create(MediaType.parse("application/json; charset=utf-8"), sendMessageResponseJson);
        Response sendMessageResponse = new Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_1_0)
            .request(sendMessageRequest)
            .body(sendMessageBody)
            .message("ok")
            .build();
        ArgumentMatcher<Request> sendMessageMatcher =
            (anyRequest) -> sendMessageRequest.url().encodedPath().startsWith(anyRequest.url().encodedPath());
        Call sendMessageRequestCall = Mockito.mock(Call.class);
        Mockito.doReturn(sendMessageRequestCall).when(okHttpClient).newCall(Mockito.argThat(sendMessageMatcher));
        Mockito.doReturn(sendMessageResponse).when(sendMessageRequestCall).execute();

        RecordPartition partition = new RecordPartition();
        RecordOffset offset = new RecordOffset();
        ConnectRecord connectRecord = new ConnectRecord(partition, offset,
            System.currentTimeMillis(), "Hello, EventMesh!".getBytes(StandardCharsets.UTF_8));
        Method sendMessageMethod = WeChatSinkConnector.class.getDeclaredMethod("sendMessage", ConnectRecord.class);
        sendMessageMethod.setAccessible(true);
        Assertions.assertThrows(InvocationTargetException.class, () -> sendMessageMethod.invoke(weChatSinkConnector, connectRecord));
    }

    @AfterEach
    public void tearDown() throws IOException {
        weChatSinkConnector.stop();
    }

}