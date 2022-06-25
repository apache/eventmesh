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

            String protocolType = sendMessageRequestHeader.getProtocolType();
            String protocolDesc = sendMessageRequestHeader.getProtocolDesc();
            String protocolVersion = sendMessageRequestHeader.getProtocolVersion();

            String code = sendMessageRequestHeader.getCode();
            String env = sendMessageRequestHeader.getEnv();
            String idc = sendMessageRequestHeader.getIdc();
            String ip = sendMessageRequestHeader.getIp();
            String pid = sendMessageRequestHeader.getPid();
            String sys = sendMessageRequestHeader.getSys();
            String username = sendMessageRequestHeader.getUsername();
            String passwd = sendMessageRequestHeader.getPasswd();
            ProtocolVersion version = sendMessageRequestHeader.getVersion();
            String language = sendMessageRequestHeader.getLanguage();

            String content = sendMessageRequestBody.getContent();

            CloudEvent event = null;
            CloudEventBuilder cloudEventBuilder;
            if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v1();

                cloudEventBuilder = cloudEventBuilder.withId(sendMessageRequestBody.getBizSeqNo())
                    .withSubject(sendMessageRequestBody.getTopic())
                    .withType("eventmeshmessage")
                    .withSource(URI.create("/"))
                    .withData(content.getBytes(StandardCharsets.UTF_8))
                    .withExtension(ProtocolKey.REQUEST_CODE, code)
                    .withExtension(ProtocolKey.ClientInstanceKey.ENV, env)
                    .withExtension(ProtocolKey.ClientInstanceKey.IDC, idc)
                    .withExtension(ProtocolKey.ClientInstanceKey.IP, ip)
                    .withExtension(ProtocolKey.ClientInstanceKey.PID, pid)
                    .withExtension(ProtocolKey.ClientInstanceKey.SYS, sys)
                    .withExtension(ProtocolKey.ClientInstanceKey.USERNAME, username)
                    .withExtension(ProtocolKey.ClientInstanceKey.PASSWD, passwd)
                    .withExtension(ProtocolKey.VERSION, version.getVersion())
                    .withExtension(ProtocolKey.LANGUAGE, language)
                    .withExtension(ProtocolKey.PROTOCOL_TYPE, protocolType)
                    .withExtension(ProtocolKey.PROTOCOL_DESC, protocolDesc)
                    .withExtension(ProtocolKey.PROTOCOL_VERSION, protocolVersion)
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
                event = cloudEventBuilder.build();
            } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v03();
                cloudEventBuilder = cloudEventBuilder.withId(sendMessageRequestBody.getBizSeqNo())
                    .withSubject(sendMessageRequestBody.getTopic())
                    .withType("eventmeshmessage")
                    .withSource(URI.create("/"))
                    .withData(content.getBytes(StandardCharsets.UTF_8))
                    .withExtension(ProtocolKey.REQUEST_CODE, code)
                    .withExtension(ProtocolKey.ClientInstanceKey.ENV, env)
                    .withExtension(ProtocolKey.ClientInstanceKey.IDC, idc)
                    .withExtension(ProtocolKey.ClientInstanceKey.IP, ip)
                    .withExtension(ProtocolKey.ClientInstanceKey.PID, pid)
                    .withExtension(ProtocolKey.ClientInstanceKey.SYS, sys)
                    .withExtension(ProtocolKey.ClientInstanceKey.USERNAME, username)
                    .withExtension(ProtocolKey.ClientInstanceKey.PASSWD, passwd)
                    .withExtension(ProtocolKey.VERSION, version.getVersion())
                    .withExtension(ProtocolKey.LANGUAGE, language)
                    .withExtension(ProtocolKey.PROTOCOL_TYPE, protocolType)
                    .withExtension(ProtocolKey.PROTOCOL_DESC, protocolDesc)
                    .withExtension(ProtocolKey.PROTOCOL_VERSION, protocolVersion)
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
                event = cloudEventBuilder.build();
            }
            return event;
        } catch (Exception e) {
            throw new ProtocolHandleException(e.getMessage(), e.getCause());
        }
    }
}
