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

import org.apache.eventmesh.client.http.AbstractHttpClient;
import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.model.RequestParam;
import org.apache.eventmesh.client.http.util.HttpUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.io.IOException;

import io.netty.handler.codec.http.HttpMethod;
import io.openmessaging.api.Message;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class OpenMessageProducer extends AbstractHttpClient implements EventMeshProtocolProducer<Message> {

    private static final String PROTOCOL_TYPE = "openmessage";

    private static final String PROTOCOL_DESC = "http";

    public OpenMessageProducer(EventMeshHttpClientConfig eventMeshHttpClientConfig)
            throws EventMeshException {
        super(eventMeshHttpClientConfig);
    }

    @Override
    public void publish(Message openMessage) throws EventMeshException {
        validateOpenMessage(openMessage);
        RequestParam requestParam = buildCommonPostParam(openMessage)
                .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_ASYNC.getRequestCode());
        String target = selectEventMesh();
        try {
            String response = HttpUtils.post(httpClient, target, requestParam);
            EventMeshRetObj ret = JsonUtils.deserialize(response, EventMeshRetObj.class);

            if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
                throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
            }
        } catch (Exception exception) {
            throw new EventMeshException(String.format("Publish message error, target:%s", target), exception);
        }
    }

    @Override
    public Message request(Message message, long timeout) throws EventMeshException {
        validateOpenMessage(message);
        RequestParam requestParam = buildCommonPostParam(message)
                .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_SYNC.getRequestCode())
                .setTimeout(timeout);
        String target = selectEventMesh();

        try {
            String response = HttpUtils.post(httpClient, target, requestParam);
            EventMeshRetObj ret = JsonUtils.deserialize(response, EventMeshRetObj.class);
            if (ret.getRetCode() == EventMeshRetCode.SUCCESS.getRetCode()) {
                return transformMessage(ret);
            }
            throw new EventMeshException(ret.getRetCode(), ret.getRetMsg());
        } catch (Exception e) {
            throw new EventMeshException(String.format("Request message error, target:%s", target), e);
        }
    }

    @Override
    public void request(Message message, RRCallback<Message> rrCallback, long timeout) throws EventMeshException {
        validateOpenMessage(message);
        RequestParam requestParam = buildCommonPostParam(message)
                .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_SYNC.getRequestCode())
                .setTimeout(timeout);
        String target = selectEventMesh();
        RRCallbackResponseHandlerAdapter<Message> adapter =
                new RRCallbackResponseHandlerAdapter<>(message, rrCallback, timeout);
        try {
            HttpUtils.post(httpClient, null, target, requestParam, adapter);
        } catch (IOException e) {
            throw new EventMeshException(String.format("Request message error, target:%s", target), e);
        }
    }

    private void validateOpenMessage(Message openMessage) {
        Preconditions.checkNotNull(openMessage, "Message cannot be null");
    }

    private RequestParam buildCommonPostParam(Message openMessage) {
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, eventMeshHttpClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, eventMeshHttpClientConfig.getPassword())
                .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
                .addHeader(ProtocolKey.PROTOCOL_TYPE, PROTOCOL_TYPE)
                .addHeader(ProtocolKey.PROTOCOL_DESC, PROTOCOL_DESC)
                // todo: add producerGroup to header, set protocol type, protocol version
                .addBody(SendMessageRequestBody.PRODUCERGROUP, eventMeshHttpClientConfig.getProducerGroup())
                .addBody(SendMessageRequestBody.CONTENT, JsonUtils.serialize(openMessage));
        return requestParam;
    }

    private Message transformMessage(EventMeshRetObj retObj) {
        SendMessageResponseBody.ReplyMessage replyMessage = JsonUtils.deserialize(retObj.getRetMsg(),
                SendMessageResponseBody.ReplyMessage.class);
        // todo: deserialize message
        return null;
    }
}
