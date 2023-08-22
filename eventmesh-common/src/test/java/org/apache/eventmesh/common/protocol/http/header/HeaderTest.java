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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HeaderTest {

    private Map<String, Object> originalMap;

    @Before
    public void before() {
        originalMap = new HashMap<>();
    }

    @Test
    public void testBuildHeader() throws Exception {
        Assert.assertThrows(Exception.class, () -> Header.buildHeader("-1", originalMap));
        Header messageBatchRequestHeader = Header.buildHeader(String.valueOf(RequestCode.MSG_BATCH_SEND.getRequestCode()), originalMap);
        Assert.assertNotNull(messageBatchRequestHeader);
        Assert.assertEquals(messageBatchRequestHeader.getClass(), SendMessageBatchRequestHeader.class);
        Header sendMessageBatchV2RequestHeader = Header.buildHeader(String.valueOf(RequestCode.MSG_BATCH_SEND_V2.getRequestCode()), originalMap);
        Assert.assertNotNull(sendMessageBatchV2RequestHeader);
        Assert.assertEquals(sendMessageBatchV2RequestHeader.getClass(), SendMessageBatchV2RequestHeader.class);
        Header sendMessageRequestHeaderSync = Header.buildHeader(String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()), originalMap);
        Assert.assertNotNull(sendMessageRequestHeaderSync);
        Assert.assertEquals(sendMessageRequestHeaderSync.getClass(), SendMessageRequestHeader.class);
        Header sendMessageRequestHeaderAsync = Header.buildHeader(String.valueOf(RequestCode.MSG_SEND_ASYNC.getRequestCode()), originalMap);
        Assert.assertNotNull(sendMessageRequestHeaderAsync);
        Assert.assertEquals(sendMessageRequestHeaderAsync.getClass(), SendMessageRequestHeader.class);
        Header pushMessageRequestHeaderSync = Header.buildHeader(String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode()), originalMap);
        Assert.assertNotNull(pushMessageRequestHeaderSync);
        Assert.assertEquals(pushMessageRequestHeaderSync.getClass(), PushMessageRequestHeader.class);
        Header pushMessageRequestHeaderAsync = Header.buildHeader(String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode()), originalMap);
        Assert.assertNotNull(pushMessageRequestHeaderAsync);
        Assert.assertEquals(pushMessageRequestHeaderAsync.getClass(), PushMessageRequestHeader.class);
        Header regRequestHeader = Header.buildHeader(String.valueOf(RequestCode.REGISTER.getRequestCode()), originalMap);
        Assert.assertNotNull(regRequestHeader);
        Assert.assertEquals(regRequestHeader.getClass(), RegRequestHeader.class);
        Header unRegRequestHeader = Header.buildHeader(String.valueOf(RequestCode.UNREGISTER.getRequestCode()), originalMap);
        Assert.assertNotNull(unRegRequestHeader);
        Assert.assertEquals(unRegRequestHeader.getClass(), UnRegRequestHeader.class);
        Header subscribeRequestHeader = Header.buildHeader(String.valueOf(RequestCode.SUBSCRIBE.getRequestCode()), originalMap);
        Assert.assertNotNull(subscribeRequestHeader);
        Assert.assertEquals(subscribeRequestHeader.getClass(), SubscribeRequestHeader.class);
        Header unSubscribeRequestHeader = Header.buildHeader(String.valueOf(RequestCode.UNSUBSCRIBE.getRequestCode()), originalMap);
        Assert.assertNotNull(unSubscribeRequestHeader);
        Assert.assertEquals(unSubscribeRequestHeader.getClass(), UnSubscribeRequestHeader.class);
        Header heartbeatRequestHeader = Header.buildHeader(String.valueOf(RequestCode.HEARTBEAT.getRequestCode()), originalMap);
        Assert.assertNotNull(heartbeatRequestHeader);
        Assert.assertEquals(heartbeatRequestHeader.getClass(), HeartbeatRequestHeader.class);
        Header replyMessageRequestHeader = Header.buildHeader(String.valueOf(RequestCode.REPLY_MESSAGE.getRequestCode()), originalMap);
        Assert.assertNotNull(replyMessageRequestHeader);
        Assert.assertEquals(replyMessageRequestHeader.getClass(), ReplyMessageRequestHeader.class);
        Header baseRequestHeader = Header.buildHeader(String.valueOf(RequestCode.ADMIN_SHUTDOWN.getRequestCode()), originalMap);
        Assert.assertNotNull(baseRequestHeader);
        Assert.assertEquals(baseRequestHeader.getClass(), BaseRequestHeader.class);
    }
}
