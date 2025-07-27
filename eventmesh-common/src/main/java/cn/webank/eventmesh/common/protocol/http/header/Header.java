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

package cn.webank.eventmesh.common.protocol.http.header;


import cn.webank.eventmesh.common.protocol.http.common.RequestCode;
import cn.webank.eventmesh.common.protocol.http.header.client.HeartbeatRequestHeader;
import cn.webank.eventmesh.common.protocol.http.header.client.RegRequestHeader;
import cn.webank.eventmesh.common.protocol.http.header.client.UnRegRequestHeader;
import cn.webank.eventmesh.common.protocol.http.header.message.PushMessageRequestHeader;
import cn.webank.eventmesh.common.protocol.http.header.message.SendMessageBatchV2RequestHeader;
import cn.webank.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import cn.webank.eventmesh.common.protocol.http.header.message.RMBTraceLogRequestHeader;
import cn.webank.eventmesh.common.protocol.http.header.message.ReplyMessageRequestHeader;
import cn.webank.eventmesh.common.protocol.http.header.message.SendMessageBatchRequestHeader;

import java.util.Map;

public abstract class Header {

    public abstract Map<String, Object> toMap();

    public static Header buildHeader(String requestCode, Map<String, Object> originalMap) throws Exception {
        if (String.valueOf(RequestCode.MSG_BATCH_SEND.getRequestCode()).equals(requestCode)) {
            return SendMessageBatchRequestHeader.buildHeader(originalMap);
        } if (String.valueOf(RequestCode.MSG_BATCH_SEND_V2.getRequestCode()).equals(requestCode)) {
            return SendMessageBatchV2RequestHeader.buildHeader(originalMap);
        } else if (String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()).equals(requestCode)) {
            return SendMessageRequestHeader.buildHeader(originalMap);
        } else if (String.valueOf(RequestCode.MSG_SEND_ASYNC.getRequestCode()).equals(requestCode)) {
            return SendMessageRequestHeader.buildHeader(originalMap);
        } else if (String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode()).equals(requestCode)) {
            return PushMessageRequestHeader.buildHeader(originalMap);
        } else if (String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode()).equals(requestCode)) {
            return PushMessageRequestHeader.buildHeader(originalMap);
        } else if (String.valueOf(RequestCode.REGISTER.getRequestCode()).equals(requestCode)) {
            return RegRequestHeader.buildHeader(originalMap);
        } else if (String.valueOf(RequestCode.UNREGISTER.getRequestCode()).equals(requestCode)) {
            return UnRegRequestHeader.buildHeader(originalMap);
        } else if (String.valueOf(RequestCode.HEARTBEAT.getRequestCode()).equals(requestCode)) {
            return HeartbeatRequestHeader.buildHeader(originalMap);
        } else if (String.valueOf(RequestCode.REPLY_MESSAGE.getRequestCode()).equals(requestCode)) {
            return ReplyMessageRequestHeader.buildHeader(originalMap);
        } else if (String.valueOf(RequestCode.ADMIN_SHUTDOWN.getRequestCode()).equals(requestCode)) {
            return BaseRequestHeader.buildHeader(originalMap);
        } else if(String.valueOf(RequestCode.MSG_TRACE_LOG.getRequestCode()).equals(requestCode)) {
            return RMBTraceLogRequestHeader.buildHeader(originalMap);
        } else {
            throw new Exception();
        }
    }

}
