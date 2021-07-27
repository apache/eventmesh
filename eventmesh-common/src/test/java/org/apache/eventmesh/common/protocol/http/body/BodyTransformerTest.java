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

package org.apache.eventmesh.common.protocol.http.body;

import org.apache.eventmesh.common.protocol.http.body.client.HeartbeatRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.RegRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.UnRegRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.PushMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.ReplyMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchV2RequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class BodyTransformerTest {

    @Test
    public void testTransformParamToBody() throws Exception {
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.MSG_BATCH_SEND.getRequestCode()), new HashMap<>())
                        instanceof SendMessageBatchRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.MSG_BATCH_SEND_V2.getRequestCode()), new HashMap<>())
                        instanceof SendMessageBatchV2RequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.MSG_SEND_ASYNC.getRequestCode()), new HashMap<>())
                        instanceof SendMessageRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()), new HashMap<>())
                        instanceof SendMessageRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode()), new HashMap<>())
                        instanceof PushMessageRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode()), new HashMap<>())
                        instanceof PushMessageRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.REGISTER.getRequestCode()), new HashMap<>())
                        instanceof RegRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.UNREGISTER.getRequestCode()), new HashMap<>())
                        instanceof UnRegRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.SUBSCRIBE.getRequestCode()), new HashMap<>())
                        instanceof SubscribeRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.UNSUBSCRIBE.getRequestCode()), new HashMap<>())
                        instanceof UnSubscribeRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.HEARTBEAT.getRequestCode()), new HashMap<>())
                        instanceof HeartbeatRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.REPLY_MESSAGE.getRequestCode()), new HashMap<>())
                        instanceof ReplyMessageRequestBody
        );
        Assert.assertTrue(
                BodyTransformer.transformParamToBody(String.valueOf(RequestCode.ADMIN_SHUTDOWN.getRequestCode()), new HashMap<>())
                        instanceof BaseRequestBody
        );

    }


}