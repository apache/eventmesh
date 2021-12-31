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
import java.nio.charset.Charset;

import io.cloudevents.CloudEvent;
import io.openmessaging.api.Message;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

/**
 * RRCallbackResponseHandlerAdapter.
 */
@Slf4j
public class RRCallbackResponseHandlerAdapter<ProtocolMessage> implements ResponseHandler<String> {

    private final long createTime;

    private final ProtocolMessage protocolMessage;

    private final RRCallback<ProtocolMessage> rrCallback;

    private final long timeout;

    public RRCallbackResponseHandlerAdapter(ProtocolMessage protocolMessage, RRCallback<ProtocolMessage> rrCallback,
                                            long timeout) {
        Preconditions.checkNotNull(rrCallback, "rrCallback invalid");
        Preconditions.checkNotNull(protocolMessage, "message invalid");
        if (!(protocolMessage instanceof EventMeshMessage) && !(protocolMessage instanceof CloudEvent)
                && !(protocolMessage instanceof Message)) {
            throw new IllegalArgumentException(String.format("ProtocolMessage: %s is not supported", protocolMessage));
        }
        this.protocolMessage = protocolMessage;
        this.rrCallback = rrCallback;
        this.timeout = timeout;
        this.createTime = System.currentTimeMillis();
    }

    @Override
    public String handleResponse(HttpResponse response) throws IOException {
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            rrCallback.onException(new EventMeshException(response.toString()));
            return response.toString();
        }

        if (System.currentTimeMillis() - createTime > timeout) {
            String err = String.format("response too late, message: %s", protocolMessage);
            rrCallback.onException(new EventMeshException(err));
            return err;
        }

        String res = EntityUtils.toString(response.getEntity(), Charset.forName(Constants.DEFAULT_CHARSET));
        EventMeshRetObj ret = JsonUtils.deserialize(res, EventMeshRetObj.class);
        if (ret.getRetCode() != EventMeshRetCode.SUCCESS.getRetCode()) {
            rrCallback.onException(new EventMeshException(ret.getRetCode(), ret.getRetMsg()));
            return res;
        }

        // todo: constructor protocol message
        ProtocolMessage protocolMessage = transformToProtocolMessage(ret);
        rrCallback.onSuccess(protocolMessage);

        return protocolMessage.toString();
    }

    private ProtocolMessage transformToProtocolMessage(EventMeshRetObj ret) {
        // todo: constructor other protocol message, can judge by protocol type in properties
        SendMessageResponseBody.ReplyMessage replyMessage = JsonUtils.deserialize(ret.getRetMsg(),
                SendMessageResponseBody.ReplyMessage.class);
        EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                .content(replyMessage.body)
                .prop(replyMessage.properties)
                .topic(replyMessage.topic)
                .build();
        return (ProtocolMessage) eventMeshMessage;
    }
}
