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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class BodyTransformer {

    public static Map<String, Function<Map<String, Object>, Body>> requestCodeToBuildBodyFunction
            = new HashMap<>(16);

    static {
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.MSG_BATCH_SEND.getRequestCode()), SendMessageBatchRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.MSG_BATCH_SEND_V2.getRequestCode()), SendMessageBatchV2RequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.MSG_SEND_ASYNC.getRequestCode()), SendMessageRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()), SendMessageRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode()), PushMessageRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode()), PushMessageRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.REGISTER.getRequestCode()), RegRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.UNREGISTER.getRequestCode()), UnRegRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.SUBSCRIBE.getRequestCode()), SubscribeRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.UNSUBSCRIBE.getRequestCode()), UnSubscribeRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.HEARTBEAT.getRequestCode()), HeartbeatRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.REPLY_MESSAGE.getRequestCode()), ReplyMessageRequestBody::buildBody);
        requestCodeToBuildBodyFunction.put(String.valueOf(RequestCode.ADMIN_SHUTDOWN.getRequestCode()), BaseRequestBody::buildBody);
    }

    public static Body transformParamToBody(String requestCode, Map<String, Object> originalMap) throws Exception {
        Function<Map<String, Object>, Body> mapBodyFunction = requestCodeToBuildBodyFunction.get(requestCode);
        if (mapBodyFunction == null) {
            throw new Exception(String.format("requestCode:%s is not supported", requestCode));
        }
        return mapBodyFunction.apply(originalMap);
    }
}
