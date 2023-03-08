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
import org.apache.eventmesh.common.utils.RandomStringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.netty.handler.codec.http.HttpMethod;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CloudEventProducer extends AbstractProducerHttpClient<CloudEvent> {

    public CloudEventProducer(final EventMeshHttpClientConfig eventMeshHttpClientConfig) throws EventMeshException {
        super(eventMeshHttpClientConfig);
    }

    @Override
    public RequestParam builderPublishRequestParam(final CloudEvent cloudEvent) {
        final CloudEvent enhanceCloudEvent = enhanceCloudEvent(cloudEvent);
        return buildCommonPostParam(enhanceCloudEvent)
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_ASYNC.getRequestCode());
    }

    @Override
    public RequestParam builderRequestParam(final CloudEvent cloudEvent, long timeout) {
        final CloudEvent enhanceCloudEvent = enhanceCloudEvent(cloudEvent);
        return buildCommonPostParam(enhanceCloudEvent)
            .addHeader(ProtocolKey.REQUEST_CODE, RequestCode.MSG_SEND_SYNC.getRequestCode())
            .setTimeout(timeout);
    }

    @Override
    public void validateMessage(final CloudEvent cloudEvent) {
        Preconditions.checkNotNull(cloudEvent, "CloudEvent cannot be null");
    }

    private void validateCloudEvent(final CloudEvent cloudEvent) {
        Preconditions.checkNotNull(cloudEvent, "CloudEvent cannot be null");
    }

    private RequestParam buildCommonPostParam(final CloudEvent cloudEvent) {
        validateCloudEvent(cloudEvent);
        final byte[] bodyByte = Objects.requireNonNull(EventFormatProvider.getInstance().resolveFormat(cloudEvent.getDataContentType()))
            .serialize(cloudEvent);
        final String content = new String(bodyByte, StandardCharsets.UTF_8);

        final RequestParam requestParam = new RequestParam(HttpMethod.POST);
        requestParam
            .addHeader(ProtocolKey.ClientInstanceKey.ENV, eventMeshHttpClientConfig.getEnv())
            .addHeader(ProtocolKey.ClientInstanceKey.IDC, eventMeshHttpClientConfig.getIdc())
            .addHeader(ProtocolKey.ClientInstanceKey.IP, eventMeshHttpClientConfig.getIp())
            .addHeader(ProtocolKey.ClientInstanceKey.PID, eventMeshHttpClientConfig.getPid())
            .addHeader(ProtocolKey.ClientInstanceKey.SYS, eventMeshHttpClientConfig.getSys())
            .addHeader(ProtocolKey.ClientInstanceKey.USERNAME, eventMeshHttpClientConfig.getUserName())
            .addHeader(ProtocolKey.ClientInstanceKey.PASSWD, eventMeshHttpClientConfig.getPassword())
            .addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .addHeader(ProtocolKey.PROTOCOL_TYPE, ProtocolConstant.CE_PROTOCOL)
            .addHeader(ProtocolKey.PROTOCOL_DESC, ProtocolConstant.PROTOCOL_DESC)
            .addHeader(ProtocolKey.PROTOCOL_VERSION, cloudEvent.getSpecVersion().toString())

            // todo: move producerGroup tp header
            .addBody(SendMessageRequestBody.PRODUCERGROUP, eventMeshHttpClientConfig.getProducerGroup())
            .addBody(SendMessageRequestBody.CONTENT, content);
        return requestParam;
    }

    private CloudEvent enhanceCloudEvent(final CloudEvent cloudEvent) {
        return CloudEventBuilder.from(cloudEvent)
            .withExtension(ProtocolKey.ClientInstanceKey.ENV, eventMeshHttpClientConfig.getEnv())
            .withExtension(ProtocolKey.ClientInstanceKey.IDC, eventMeshHttpClientConfig.getIdc())
            .withExtension(ProtocolKey.ClientInstanceKey.IP, eventMeshHttpClientConfig.getIp())
            .withExtension(ProtocolKey.ClientInstanceKey.PID, eventMeshHttpClientConfig.getPid())
            .withExtension(ProtocolKey.ClientInstanceKey.SYS, eventMeshHttpClientConfig.getSys())
            .withExtension(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .withExtension(ProtocolKey.PROTOCOL_DESC, cloudEvent.getSpecVersion().name())
            .withExtension(ProtocolKey.PROTOCOL_VERSION, cloudEvent.getSpecVersion().toString())
            .withExtension(ProtocolKey.ClientInstanceKey.BIZSEQNO, RandomStringUtils.generateNum(30))
            .withExtension(ProtocolKey.ClientInstanceKey.UNIQUEID, RandomStringUtils.generateNum(30))
            .build();
    }

    @Override
    public CloudEvent transformMessage(final EventMeshRetObj retObj) {
        final SendMessageResponseBody.ReplyMessage replyMessage = JsonUtils.parseObject(retObj.getRetMsg(),
            SendMessageResponseBody.ReplyMessage.class);
        // todo: deserialize message
        return null;
    }
}
