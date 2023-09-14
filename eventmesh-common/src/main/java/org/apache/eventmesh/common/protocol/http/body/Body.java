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

import java.util.Map;

public abstract class Body {

    public abstract Map<String, Object> toMap();

    public static Body buildBody(String requestCode, Map<String, Object> originalMap) throws Exception {
        RequestCode code = RequestCode.get(Integer.valueOf(requestCode));
        if (code == null) {
            throw new Exception("Request code " + requestCode + "not support");
        }
        switch (code) {
            case MSG_BATCH_SEND:
                return SendMessageBatchRequestBody.buildBody(originalMap);
            case MSG_BATCH_SEND_V2:
                return SendMessageBatchV2RequestBody.buildBody(originalMap);
            case MSG_SEND_ASYNC:
            case MSG_SEND_SYNC:
                return SendMessageRequestBody.buildBody(originalMap);
            case HTTP_PUSH_CLIENT_ASYNC:
            case HTTP_PUSH_CLIENT_SYNC:
                return PushMessageRequestBody.buildBody(originalMap);
            case REGISTER:
                return RegRequestBody.buildBody(originalMap);
            case UNREGISTER:
                return UnRegRequestBody.buildBody(originalMap);
            case SUBSCRIBE:
                return SubscribeRequestBody.buildBody(originalMap);
            case UNSUBSCRIBE:
                return UnSubscribeRequestBody.buildBody(originalMap);
            case HEARTBEAT:
                return HeartbeatRequestBody.buildBody(originalMap);
            case REPLY_MESSAGE:
                return ReplyMessageRequestBody.buildBody(originalMap);
            case ADMIN_SHUTDOWN:
                return BaseRequestBody.buildBody(originalMap);
            default:
                throw new Exception("Request code " + requestCode + "not support");
        }

    }
}

