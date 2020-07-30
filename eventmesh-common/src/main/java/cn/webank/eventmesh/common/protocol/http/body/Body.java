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

package cn.webank.eventmesh.common.protocol.http.body;


import cn.webank.eventmesh.common.protocol.http.body.client.HeartbeatRequestBody;
import cn.webank.eventmesh.common.protocol.http.body.client.UnRegRequestBody;
import cn.webank.eventmesh.common.protocol.http.common.RequestCode;
import cn.webank.eventmesh.common.protocol.http.body.client.RegRequestBody;
import cn.webank.eventmesh.common.protocol.http.body.message.PushMessageRequestBody;
import cn.webank.eventmesh.common.protocol.http.body.message.RMBTraceLogRequestBody;
import cn.webank.eventmesh.common.protocol.http.body.message.ReplyMessageRequestBody;
import cn.webank.eventmesh.common.protocol.http.body.message.SendMessageBatchRequestBody;
import cn.webank.eventmesh.common.protocol.http.body.message.SendMessageBatchV2RequestBody;
import cn.webank.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;

import java.util.Map;

public abstract class Body {

    public abstract Map<String, Object> toMap();

    public static Body buildBody(String requestCode, Map<String, Object> originalMap) throws Exception {
        if (String.valueOf(RequestCode.MSG_BATCH_SEND.getRequestCode()).equals(requestCode)) {
            return SendMessageBatchRequestBody.buildBody(originalMap);
        } if (String.valueOf(RequestCode.MSG_BATCH_SEND_V2.getRequestCode()).equals(requestCode)) {
            return SendMessageBatchV2RequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.MSG_SEND_ASYNC.getRequestCode()).equals(requestCode)) {
            return SendMessageRequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()).equals(requestCode)) {
            return SendMessageRequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode()).equals(requestCode)) {
            return PushMessageRequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode()).equals(requestCode)) {
            return PushMessageRequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.REGISTER.getRequestCode()).equals(requestCode)) {
            return RegRequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.UNREGISTER.getRequestCode()).equals(requestCode)) {
            return UnRegRequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.HEARTBEAT.getRequestCode()).equals(requestCode)) {
            return HeartbeatRequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.REPLY_MESSAGE.getRequestCode()).equals(requestCode)) {
            return ReplyMessageRequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.ADMIN_SHUTDOWN.getRequestCode()).equals(requestCode)) {
            return BaseRequestBody.buildBody(originalMap);
        } else if (String.valueOf(RequestCode.MSG_TRACE_LOG.getRequestCode()).equals(requestCode)) {
            return RMBTraceLogRequestBody.buildBody(originalMap);
        } else {
            throw new Exception();
        }
    }
}
