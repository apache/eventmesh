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

import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;

public class SendMessageRequestProtocolResolver {

    public static CloudEvent buildEvent(Header header, Body body) throws ProtocolHandleException {
        try {
            SendMessageRequestHeader sendMessageRequestHeader = (SendMessageRequestHeader) header;
            SendMessageRequestBody sendMessageRequestBody = (SendMessageRequestBody) body;

            CloudEventBuilder cloudEventBuilder;
            switch (SpecVersion.parse(sendMessageRequestHeader.getProtocolVersion())) {
                case SpecVersion.V1:
                    cloudEventBuilder = CloudEventBuilder.v1();
                    break;
                case SpecVersion.V03:
                    cloudEventBuilder = CloudEventBuilder.v03();
                    break;
                default:
                    return null; // unsupported protocol version
            }

            return getBuilderCloudEvent(sendMessageRequestHeader, sendMessageRequestBody, cloudEventBuilder);

        } catch (Exception e) {
            throw new ProtocolHandleException(e.getMessage(), e.getCause());
        }
    }

    private static CloudEvent getBuilderCloudEvent(SendMessageRequestHeader sendMessageRequestHeader, SendMessageRequestBody sendMessageRequestBody,
        CloudEventBuilder cloudEventBuilder) {
        cloudEventBuilder = cloudEventBuilder.withId(sendMessageRequestBody.getBizSeqNo())
            .withSubject(sendMessageRequestBody.getTopic())
            .withType("eventmeshmessage")
            .withSource(URI.create("/"))
            .withData(sendMessageRequestBody.getContent().getBytes(StandardCharsets.UTF_8))
            .withExtension(ProtocolKey.REQUEST_CODE, sendMessageRequestHeader.getCode())
            .withExtension(ProtocolKey.ClientInstanceKey.ENV, sendMessageRequestHeader.getEnv())
            .withExtension(ProtocolKey.ClientInstanceKey.IDC, sendMessageRequestHeader.getIdc())
            .withExtension(ProtocolKey.ClientInstanceKey.IP, sendMessageRequestHeader.getIp())
            .withExtension(ProtocolKey.ClientInstanceKey.PID, sendMessageRequestHeader.getPid())
            .withExtension(ProtocolKey.ClientInstanceKey.SYS, sendMessageRequestHeader.getSys())
            .withExtension(ProtocolKey.ClientInstanceKey.USERNAME, sendMessageRequestHeader.getUsername())
            .withExtension(ProtocolKey.ClientInstanceKey.PASSWD, sendMessageRequestHeader.getPasswd())
            .withExtension(ProtocolKey.VERSION, sendMessageRequestHeader.getVersion().getVersion())
            .withExtension(ProtocolKey.LANGUAGE, sendMessageRequestHeader.getLanguage())
            .withExtension(ProtocolKey.PROTOCOL_TYPE, sendMessageRequestHeader.getProtocolType())
            .withExtension(ProtocolKey.PROTOCOL_DESC, sendMessageRequestHeader.getProtocolDesc())
            .withExtension(ProtocolKey.PROTOCOL_VERSION, sendMessageRequestHeader.getProtocolVersion())
            .withExtension(SendMessageRequestBody.BIZSEQNO, sendMessageRequestBody.getBizSeqNo())
            .withExtension(SendMessageRequestBody.UNIQUEID, sendMessageRequestBody.getUniqueId())
            .withExtension(SendMessageRequestBody.PRODUCERGROUP,
                sendMessageRequestBody.getProducerGroup())
            .withExtension(SendMessageRequestBody.TTL, sendMessageRequestBody.getTtl());
        if (StringUtils.isNotEmpty(sendMessageRequestBody.getTag())) {
            cloudEventBuilder = cloudEventBuilder.withExtension(SendMessageRequestBody.TAG, sendMessageRequestBody.getTag());
        }
        if (sendMessageRequestBody.getExtFields() != null && sendMessageRequestBody.getExtFields().size() > 0) {
            for (Map.Entry<String, String> entry : sendMessageRequestBody.getExtFields().entrySet()) {
                cloudEventBuilder = cloudEventBuilder.withExtension(entry.getKey(), entry.getValue());
            }
        }
        return cloudEventBuilder.build();
    }

}
