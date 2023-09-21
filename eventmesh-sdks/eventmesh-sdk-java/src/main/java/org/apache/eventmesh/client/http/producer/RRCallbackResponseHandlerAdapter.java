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

import org.apache.eventmesh.client.http.EventMeshRetObj;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.openmessaging.api.Message;

/**
 * RRCallbackResponseHandlerAdapter.
 */
public class RRCallbackResponseHandlerAdapter<ProtocolMessage> implements ResponseHandler<String> {

    private final transient long createTime;

    private final transient ProtocolMessage protocolMessage;

    private final transient RRCallback<ProtocolMessage> rrCallback;

    private final transient long timeout;

    public RRCallbackResponseHandlerAdapter(final ProtocolMessage protocolMessage, final RRCallback<ProtocolMessage> rrCallback,
        final long timeout) {
        Objects.requireNonNull(rrCallback, "rrCallback invalid");
        Objects.requireNonNull(protocolMessage, "message invalid");

        if (!(protocolMessage instanceof EventMeshMessage)
            && !(protocolMessage instanceof CloudEvent)
            && !(protocolMessage instanceof Message)) {
            throw new IllegalArgumentException(String.format("ProtocolMessage: %s is not supported", protocolMessage));
        }
        this.protocolMessage = protocolMessage;
        this.rrCallback = rrCallback;
        this.timeout = timeout;
        this.createTime = System.currentTimeMillis();
    }

    @Override
    public String handleResponse(final HttpResponse response) throws IOException {
        Objects.requireNonNull(response, "HttpResponse must not be null");

        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            rrCallback.onException(new EventMeshException(response.toString()));
            return response.toString();
        }

        if (System.currentTimeMillis() - createTime > timeout) {
            final String err = String.format("response too late, message: %s", protocolMessage);
            rrCallback.onException(new EventMeshException(err));
            return err;
        }

        final String res = EntityUtils.toString(response.getEntity(), Constants.DEFAULT_CHARSET);
        final EventMeshRetObj ret = JsonUtils.parseObject(res, EventMeshRetObj.class);
        Objects.requireNonNull(ret, "EventMeshRetObj must not be null");
        if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
            rrCallback.onException(new EventMeshException(ret.getRetCode(), ret.getRetMsg()));
            return res;
        }

        // todo: constructor protocol message
        final ProtocolMessage protocolMessage = transformToProtocolMessage(ret);
        rrCallback.onSuccess(protocolMessage);

        return protocolMessage.toString();
    }

    @SuppressWarnings("unchecked")
    private ProtocolMessage transformToProtocolMessage(final EventMeshRetObj ret) {
        Objects.requireNonNull(ret, "EventMeshRetObj must not be null");

        final SendMessageResponseBody.ReplyMessage replyMessage = JsonUtils.parseObject(ret.getRetMsg(),
            SendMessageResponseBody.ReplyMessage.class);
        Objects.requireNonNull(replyMessage, "ReplyMessage must not be null");
        if (protocolMessage instanceof EventMeshMessage) {
            final EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                .content(replyMessage.body)
                .prop(replyMessage.properties)
                .topic(replyMessage.topic)
                .build();

            return (ProtocolMessage) eventMeshMessage;
        }
        // todo: constructor other protocol message
        throw new RuntimeException("Unsupported callback message type");
    }
}
