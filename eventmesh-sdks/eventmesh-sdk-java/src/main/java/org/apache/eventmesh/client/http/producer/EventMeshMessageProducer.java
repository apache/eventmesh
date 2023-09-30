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
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.Objects;

import io.cloudevents.SpecVersion;
import io.netty.handler.codec.http.HttpMethod;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class EventMeshMessageProducer extends AbstractProducerHttpClient<EventMeshMessage> {

    public EventMeshMessageProducer(final EventMeshHttpClientConfig eventMeshHttpClientConfig) throws EventMeshException {
        super(eventMeshHttpClientConfig);
    }

    @Override
    public RequestParam builderPublishRequestParam(final EventMeshMessage message) {
        return buildCommonPostParam(message)
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_ASYNC.getRequestCode());
    }

    @Override
    public RequestParam builderRequestParam(final EventMeshMessage message, final long timeout) {
        return buildCommonPostParam(message)
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_SYNC.getRequestCode())
            .setTimeout(timeout);
    }

    @Override
    public void validateMessage(final EventMeshMessage message) {
        Objects.requireNonNull(message, "eventMeshMessage invalid");
        Objects.requireNonNull(message.getTopic(), "eventMeshMessage[topic] invalid");
        Objects.requireNonNull(message.getContent(), "eventMeshMessage[content] invalid");

    }

    private RequestParam buildCommonPostParam(final EventMeshMessage message) {
        final RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam
            .addHeader(ProtocolKey.ClientInstanceKey.ENV.getKey(), eventMeshHttpClientConfig.getEnv())
            .addHeader(ProtocolKey.ClientInstanceKey.IDC.getKey(), eventMeshHttpClientConfig.getIdc())
            .addHeader(ProtocolKey.ClientInstanceKey.IP.getKey(), eventMeshHttpClientConfig.getIp())
            .addHeader(ProtocolKey.ClientInstanceKey.PID.getKey(), eventMeshHttpClientConfig.getPid())
            .addHeader(ProtocolKey.ClientInstanceKey.SYS.getKey(), eventMeshHttpClientConfig.getSys())
            .addHeader(ProtocolKey.ClientInstanceKey.USERNAME.getKey(), eventMeshHttpClientConfig.getUserName())
            .addHeader(ProtocolKey.ClientInstanceKey.PASSWD.getKey(), eventMeshHttpClientConfig.getPassword())
            .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
            .addHeader(ProtocolKey.PROTOCOL_TYPE, ProtocolConstant.EM_MESSAGE_PROTOCOL)
            .addHeader(ProtocolKey.PROTOCOL_DESC, ProtocolConstant.PROTOCOL_DESC)
            // default ce version is 1.0
            .addHeader(ProtocolKey.PROTOCOL_VERSION, SpecVersion.V1.toString())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .addBody(SendMessageRequestBody.PRODUCERGROUP, eventMeshHttpClientConfig.getProducerGroup())
            // todo: set message to content is better
            .addBody(SendMessageRequestBody.TOPIC, message.getTopic())
            .addBody(SendMessageRequestBody.CONTENT, message.getContent())
            .addBody(SendMessageRequestBody.TTL, message.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL))
            .addBody(SendMessageRequestBody.BIZSEQNO, message.getBizSeqNo())
            .addBody(SendMessageRequestBody.UNIQUEID, message.getUniqueId());
        return requestParam;
    }

    @Override
    public EventMeshMessage transformMessage(final EventMeshRetObj retObj) {
        final SendMessageResponseBody.ReplyMessage replyMessage = JsonUtils.parseObject(retObj.getRetMsg(),
            SendMessageResponseBody.ReplyMessage.class);
        return EventMeshMessage.builder()
            .content(Objects.requireNonNull(replyMessage, "ReplyMessage must not be null").body)
            .prop(replyMessage.properties)
            .topic(replyMessage.topic).build();
    }
}
