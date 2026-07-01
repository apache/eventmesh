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

package org.apache.eventmesh.client.http.producer;

import org.apache.eventmesh.client.http.AbstractProducerHttpClient;
import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.client.http.ProtocolConstant;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.model.RequestParam;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.Objects;

import io.netty.handler.codec.http.HttpMethod;
import io.openmessaging.api.Message;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class OpenMessageProducer extends AbstractProducerHttpClient<Message> {

    public OpenMessageProducer(final EventMeshHttpClientConfig eventMeshHttpClientConfig)
                                                                                          throws EventMeshException {
        super(eventMeshHttpClientConfig);
    }

    @Override
    public RequestParam builderPublishRequestParam(final Message openMessage) {
        return buildCommonPostParam(openMessage)
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_ASYNC.getRequestCode());
    }

    @Override
    public RequestParam builderRequestParam(final Message message, final long timeout) {
        return buildCommonPostParam(message)
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_SYNC.getRequestCode())
            .setTimeout(timeout);
    }

    @Override
    public void validateMessage(final Message message) {
        Objects.requireNonNull(message, "Message cannot be null");
    }

    private RequestParam buildCommonPostParam(final Message openMessage) {
        final RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam
            .addHeader(ProtocolKey.ClientInstanceKey.USERNAME.getKey(), eventMeshHttpClientConfig.getUserName())
            .addHeader(ProtocolKey.ClientInstanceKey.PASSWD.getKey(), eventMeshHttpClientConfig.getPassword())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .addHeader(ProtocolKey.PROTOCOL_TYPE, ProtocolConstant.OP_MESSAGE_PROTOCOL)
            .addHeader(ProtocolKey.PROTOCOL_DESC, ProtocolConstant.PROTOCOL_DESC)
            // todo: add producerGroup to header, set protocol type, protocol version
            .addBody(SendMessageRequestBody.PRODUCERGROUP, eventMeshHttpClientConfig.getProducerGroup())
            .addBody(SendMessageRequestBody.CONTENT, JsonUtils.toJSONString(openMessage));
        return requestParam;
    }

    @Override
    public Message transformMessage(final EventMeshRetObj retObj) {
        final SendMessageResponseBody.ReplyMessage replyMessage = JsonUtils.parseObject(retObj.getRetMsg(),
            SendMessageResponseBody.ReplyMessage.class);
        // todo: deserialize message
        return null;
    }
}
