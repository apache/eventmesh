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
import org.apache.eventmesh.client.http.ProtocolConstant;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.model.RequestParam;
import org.apache.eventmesh.client.http.util.HttpUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.io.IOException;

import io.cloudevents.SpecVersion;
import io.netty.handler.codec.http.HttpMethod;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class EventMeshMessageProducer extends AbstractHttpClient implements EventMeshProtocolProducer<EventMeshMessage> {

    public EventMeshMessageProducer(EventMeshHttpClientConfig eventMeshHttpClientConfig) throws EventMeshException {
        super(eventMeshHttpClientConfig);
    }

    @Override
    public void publish(EventMeshMessage message) throws EventMeshException {
        validateEventMeshMessage(message);
        RequestParam requestParam = buildCommonPostParam(message)
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
    public EventMeshMessage request(EventMeshMessage message, long timeout) throws EventMeshException {
        validateEventMeshMessage(message);
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
    public void request(EventMeshMessage message, RRCallback<EventMeshMessage> rrCallback, long timeout)
            throws EventMeshException {
        validateEventMeshMessage(message);

        RequestParam requestParam = buildCommonPostParam(message)
                .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_SYNC.getRequestCode())
                .setTimeout(timeout);

        String target = selectEventMesh();
        RRCallbackResponseHandlerAdapter<EventMeshMessage> adapter =
                new RRCallbackResponseHandlerAdapter<>(message, rrCallback, timeout);
        try {
            HttpUtils.post(httpClient, null, target, requestParam, adapter);
        } catch (IOException e) {
            throw new EventMeshException(String.format("Request message error, target:%s", target), e);
        }
    }

    private void validateEventMeshMessage(EventMeshMessage message) {
        Preconditions.checkNotNull(message, "eventMeshMessage invalid");
        Preconditions.checkNotNull(message.getTopic(), "eventMeshMessage[topic] invalid");
        Preconditions.checkNotNull(message.getContent(), "eventMeshMessage[content] invalid");
    }

    private RequestParam buildCommonPostParam(EventMeshMessage message) {
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam
                .addHeader(ProtocolKey.ClientInstanceKey.ENV, eventMeshHttpClientConfig.getEnv())
                .addHeader(ProtocolKey.ClientInstanceKey.IDC, eventMeshHttpClientConfig.getIdc())
                .addHeader(ProtocolKey.ClientInstanceKey.IP, eventMeshHttpClientConfig.getIp())
                .addHeader(ProtocolKey.ClientInstanceKey.PID, eventMeshHttpClientConfig.getPid())
                .addHeader(ProtocolKey.ClientInstanceKey.SYS, eventMeshHttpClientConfig.getSys())
                .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, eventMeshHttpClientConfig.getUserName())
                .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, eventMeshHttpClientConfig.getPassword())
                .addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion())
                .addHeader(ProtocolKey.PROTOCOL_TYPE, ProtocolConstant.EM_MESSAGE_PROTOCOL)
                .addHeader(ProtocolKey.PROTOCOL_DESC, ProtocolConstant.PROTOCOL_DESC)
                //default ce version is 1.0
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

    private EventMeshMessage transformMessage(EventMeshRetObj retObj) {
        SendMessageResponseBody.ReplyMessage replyMessage = JsonUtils.deserialize(retObj.getRetMsg(),
                SendMessageResponseBody.ReplyMessage.class);
        return EventMeshMessage.builder()
                .content(replyMessage.body)
                .prop(replyMessage.properties)
                .topic(replyMessage.topic).build();
    }
}
