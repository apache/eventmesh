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

package org.apache.eventmesh.protocol.meshmessage.resolver.http;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchV2RequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2RequestHeader;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;

public class SendMessageBatchV2ProtocolResolver {

    public static CloudEvent buildEvent(Header header, Body body) throws ProtocolHandleException {
        try {
            SendMessageBatchV2RequestHeader sendMessageBatchV2RequestHeader = (SendMessageBatchV2RequestHeader) header;
            SendMessageBatchV2RequestBody sendMessageBatchV2RequestBody = (SendMessageBatchV2RequestBody) body;

            CloudEventBuilder cloudEventBuilder = CloudEventBuilder.fromSpecVersion(
                SpecVersion.parse(sendMessageBatchV2RequestHeader.getProtocolVersion()));

            return getBuildCloudEvent(sendMessageBatchV2RequestHeader, sendMessageBatchV2RequestBody, cloudEventBuilder);
        } catch (Exception e) {
            throw new ProtocolHandleException(e.getMessage(), e.getCause());
        }
    }

    private static CloudEvent getBuildCloudEvent(SendMessageBatchV2RequestHeader sendMessageBatchV2RequestHeader,
                                                 SendMessageBatchV2RequestBody sendMessageBatchV2RequestBody, CloudEventBuilder cloudEventBuilder) {
        cloudEventBuilder = cloudEventBuilder.withId(sendMessageBatchV2RequestBody.getBizSeqNo())
            .withSubject(sendMessageBatchV2RequestBody.getTopic())
            .withType("eventmeshmessage")
            .withSource(URI.create("/"))
            .withData(sendMessageBatchV2RequestBody.getMsg().getBytes(Constants.DEFAULT_CHARSET))
            .withExtension(ProtocolKey.REQUEST_CODE, sendMessageBatchV2RequestHeader.getCode())
            .withExtension(ProtocolKey.ClientInstanceKey.ENV.getKey(), sendMessageBatchV2RequestHeader.getEnv())
            .withExtension(ProtocolKey.ClientInstanceKey.IDC.getKey(), sendMessageBatchV2RequestHeader.getIdc())
            .withExtension(ProtocolKey.ClientInstanceKey.IP.getKey(), sendMessageBatchV2RequestHeader.getIp())
            .withExtension(ProtocolKey.ClientInstanceKey.PID.getKey(), sendMessageBatchV2RequestHeader.getPid())
            .withExtension(ProtocolKey.ClientInstanceKey.SYS.getKey(), sendMessageBatchV2RequestHeader.getSys())
            .withExtension(ProtocolKey.ClientInstanceKey.USERNAME.getKey(), sendMessageBatchV2RequestHeader.getUsername())
            .withExtension(ProtocolKey.ClientInstanceKey.PASSWD.getKey(), sendMessageBatchV2RequestHeader.getPasswd())
            .withExtension(ProtocolKey.VERSION, sendMessageBatchV2RequestHeader.getVersion().getVersion())
            .withExtension(ProtocolKey.LANGUAGE, sendMessageBatchV2RequestHeader.getLanguage())
            .withExtension(ProtocolKey.PROTOCOL_TYPE, sendMessageBatchV2RequestHeader.getProtocolType())
            .withExtension(ProtocolKey.PROTOCOL_DESC, sendMessageBatchV2RequestHeader.getProtocolDesc())
            .withExtension(ProtocolKey.PROTOCOL_VERSION, sendMessageBatchV2RequestHeader.getProtocolVersion())
            .withExtension(SendMessageBatchV2RequestBody.BIZSEQNO, sendMessageBatchV2RequestBody.getBizSeqNo())
            .withExtension(SendMessageBatchV2RequestBody.PRODUCERGROUP, sendMessageBatchV2RequestBody.getProducerGroup())
            .withExtension(SendMessageBatchV2RequestBody.TTL, sendMessageBatchV2RequestBody.getTtl());
        if (StringUtils.isNotEmpty(sendMessageBatchV2RequestBody.getTag())) {
            cloudEventBuilder = cloudEventBuilder.withExtension(SendMessageRequestBody.TAG, sendMessageBatchV2RequestBody.getTag());
        }
        return cloudEventBuilder.build();
    }
}
