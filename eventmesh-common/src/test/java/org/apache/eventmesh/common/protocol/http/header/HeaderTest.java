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

package org.apache.eventmesh.common.protocol.http.header;

import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.HeartbeatRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.RegRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.SubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.UnRegRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.PushMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.ReplyMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2RequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HeaderTest {

    private Map<String, Object> originalMap;

    @BeforeEach
    public void before() {
        originalMap = new HashMap<>();
    }

    @Test
    public void testBuildHeader() throws Exception {
        Assertions.assertThrows(Exception.class, () -> Header.buildHeader("-1", originalMap));
        Header messageBatchRequestHeader = Header.buildHeader(String.valueOf(RequestCode.MSG_BATCH_SEND.getRequestCode()), originalMap);
        Assertions.assertNotNull(messageBatchRequestHeader);
        Assertions.assertEquals(messageBatchRequestHeader.getClass(), SendMessageBatchRequestHeader.class);
        Header sendMessageBatchV2RequestHeader = Header.buildHeader(String.valueOf(RequestCode.MSG_BATCH_SEND_V2.getRequestCode()), originalMap);
        Assertions.assertNotNull(sendMessageBatchV2RequestHeader);
        Assertions.assertEquals(sendMessageBatchV2RequestHeader.getClass(), SendMessageBatchV2RequestHeader.class);
        Header sendMessageRequestHeaderSync = Header.buildHeader(String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()), originalMap);
        Assertions.assertNotNull(sendMessageRequestHeaderSync);
        Assertions.assertEquals(sendMessageRequestHeaderSync.getClass(), SendMessageRequestHeader.class);
        Header sendMessageRequestHeaderAsync = Header.buildHeader(String.valueOf(RequestCode.MSG_SEND_ASYNC.getRequestCode()), originalMap);
        Assertions.assertNotNull(sendMessageRequestHeaderAsync);
        Assertions.assertEquals(sendMessageRequestHeaderAsync.getClass(), SendMessageRequestHeader.class);
        Header pushMessageRequestHeaderSync = Header.buildHeader(String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode()), originalMap);
        Assertions.assertNotNull(pushMessageRequestHeaderSync);
        Assertions.assertEquals(pushMessageRequestHeaderSync.getClass(), PushMessageRequestHeader.class);
        Header pushMessageRequestHeaderAsync = Header.buildHeader(String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode()), originalMap);
        Assertions.assertNotNull(pushMessageRequestHeaderAsync);
        Assertions.assertEquals(pushMessageRequestHeaderAsync.getClass(), PushMessageRequestHeader.class);
        Header regRequestHeader = Header.buildHeader(String.valueOf(RequestCode.REGISTER.getRequestCode()), originalMap);
        Assertions.assertNotNull(regRequestHeader);
        Assertions.assertEquals(regRequestHeader.getClass(), RegRequestHeader.class);
        Header unRegRequestHeader = Header.buildHeader(String.valueOf(RequestCode.UNREGISTER.getRequestCode()), originalMap);
        Assertions.assertNotNull(unRegRequestHeader);
        Assertions.assertEquals(unRegRequestHeader.getClass(), UnRegRequestHeader.class);
        Header subscribeRequestHeader = Header.buildHeader(String.valueOf(RequestCode.SUBSCRIBE.getRequestCode()), originalMap);
        Assertions.assertNotNull(subscribeRequestHeader);
        Assertions.assertEquals(subscribeRequestHeader.getClass(), SubscribeRequestHeader.class);
        Header unSubscribeRequestHeader = Header.buildHeader(String.valueOf(RequestCode.UNSUBSCRIBE.getRequestCode()), originalMap);
        Assertions.assertNotNull(unSubscribeRequestHeader);
        Assertions.assertEquals(unSubscribeRequestHeader.getClass(), UnSubscribeRequestHeader.class);
        Header heartbeatRequestHeader = Header.buildHeader(String.valueOf(RequestCode.HEARTBEAT.getRequestCode()), originalMap);
        Assertions.assertNotNull(heartbeatRequestHeader);
        Assertions.assertEquals(heartbeatRequestHeader.getClass(), HeartbeatRequestHeader.class);
        Header replyMessageRequestHeader = Header.buildHeader(String.valueOf(RequestCode.REPLY_MESSAGE.getRequestCode()), originalMap);
        Assertions.assertNotNull(replyMessageRequestHeader);
        Assertions.assertEquals(replyMessageRequestHeader.getClass(), ReplyMessageRequestHeader.class);
        Header baseRequestHeader = Header.buildHeader(String.valueOf(RequestCode.ADMIN_SHUTDOWN.getRequestCode()), originalMap);
        Assertions.assertNotNull(baseRequestHeader);
        Assertions.assertEquals(baseRequestHeader.getClass(), BaseRequestHeader.class);
    }
}
