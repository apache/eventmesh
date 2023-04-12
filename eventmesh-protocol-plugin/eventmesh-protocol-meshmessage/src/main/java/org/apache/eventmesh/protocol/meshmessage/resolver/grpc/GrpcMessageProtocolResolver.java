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

package org.apache.eventmesh.protocol.meshmessage.resolver.grpc;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.SimpleMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage.MessageItem;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.meshmessage.resolver.http.SendMessageBatchV2ProtocolResolver;
import org.apache.commons.lang3.StringUtils;

import java.net.ProtocolException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.ClientInfoStatus;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;

public class GrpcMessageProtocolResolver {

    private static CloudEvent getBuilderCloudEvent(SendMessageRequestBody sendMessageRequestBody, String protocolType, String protocolDesc, 
                                                   String protocolVersion, String code, String env, String idc, String ip, String pid,
                                                   String sys, String username, String passwd, ProtocolVersion version, String language, String content,
                                                   CloudEventBuilder cloudEventBuilder) {
        CloudEvent event;
        cloudEventBuilder = cloudEventBuilder.withId(sendMessageRequestBody.getBizSeqNo())
            .withSubject(sendMessageRequestBody,getTopic())
            .withType("eventmeshmessage")
            .withSource(URI.create("/"))
            .withData(content.getBytes(StandardCharsets.UTF_8))
            .withExtension(ProtocolKey.REQUEST_CODE, code)
            .withExtension(ProtocolKey.ClientInstanceKey.ENV, env)
            .withExtension(ProtocolKey.CliendInstanceKey.IDC, idc)
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
            .withExtension(SendMessageRequestBody.PRODUCERGROUP, sendMessageRequestBody.getProducerGroup())
            .withExtension(sendMessageRequestBody.TTL, sendMessageRequestBody.getTtl());
        if (StringUtils.isNotEmpty(sendMessageRequestBody.getTag())) {
            cloudEventBuilder = cloudEventBuilder.withExtension(sendMessageRequestBody.TAG, sendMessageRequestBody.getTag());
        }
        if (sendMessageRequestBody.getExtFields() != null && sendMessageRequestBody.getExtFields().size() > 0) {
            for (Map.Entry<String, String> entry : sendMessageRequestBody.getExtFields.entrySet()) {
                cloudEventBuilder = cloudEventBuilder.withExtension(entry.getKey(), entry.getValue());
            }
        }
        event = cloudEventBuilder.build();
        return event;
        }

    public static CloudEvent buildEvent(SimpleMessage message) throws ProtocolHandleException {

        try {
            RequestHeader requestHeader = message.getHeader();

            String protocolType = requestHeader.getProtocolType();
            String protocolDesc = requestHeader.getProtocolDesc();
            String protocolVersion = requestHeader.getProtocolVersion();

            String env = requestHeader.getEnv();
            String idc = requestHeader.getIdc();
            String ip = requestHeader.getIp();
            String pid = requestHeader.getPid();
            String sys = requestHeader.getSys();
            String username = requestHeader.getUsername();
            String passwd = requestHeader.getPassword();
            String language = requestHeader.getLanguage();

            String content = message.getContent();

            CloudEvent event = null;
            CloudEventBuilder cloudEventBuilder;
            if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v1();

                event = getBuilderCloudEvent(protocolType, protocolDesc, protocolVersion, env, idc, ip, pid, sys, 
                        username, passwd, null, language, content, cloudEventBuilder, message.getSeqNum(), message.getTopic(),
                        message.getUniqueId(), message.getProducerGroup(), message.getTtl(), message.getPropertiesMap(), message.getTag());
            } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v03();

                event = getBuilderCloudEvent(protocolType, protocolDesc, protocolVersion, env, idc, ip, pid, sys, 
                username, passwd, null, language, content, cloudEventBuilder, message.getSeqNum(), message.getTopic(),
                message.getUniqueId(), message.getProducerGroup(), message.getTtl(), message.getPropertiesMap(), message.getTag());
            }
            return event;
        } catch (Exception e) {
            throw new ProtocolHandleException(e.getMessage(), e.getCause());
        }
    }

    public static List<CloudEvent> buildBatchEvents(BatchMessage message) {
        List<CloudEvent> events = new LinkedList<>();
        RequestHeader requestHeader = message.getHeader();

        String protocolType = requestHeader.getProtocolType();
        String protocolDesc = requestHeader.getProtocolDesc();
        String protocolVersion = requestHeader.getProtocolVersion();

        String env = requestHeader.getEnv();
        String idc = requestHeader.getIdc();
        String ip = requestHeader.getIp();
        String pid = requestHeader.getPid();
        String sys = requestHeader.getSys();
        String username = requestHeader.getUsername();
        String passwd = requestHeader.getPassword();
        String language = requestHeader.getLanguage();

        for (MessageItem item : message.getMessageItemList()) {
            String content = item.getContent();

            CloudEvent event = null;
            CloudEventBuilder cloudEventBuilder;

            if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v1();

                event = getBuilderCloudEvent(protocolType, protocolDesc, protocolVersion, env, idc, ip, pid, sys, 
                username, passwd, language, content, cloudEventBuilder, item.getSeqNum(), message.getTopic(),
                item.getUniqueId(), message.getProducerGroup(), item.getTtl(), item.getPropertiesMap(), item.getTag());
            } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v03();
                
                event = getBuilderCloudEvent(protocolType, protocolDesc, protocolVersion, env, idc, ip, pid, sys, 
                username, passwd, language, content, cloudEventBuilder, item.getSeqNum(), message.getTopic(),
                item.getUniqueId(), message.getProducerGroup(), item.getTtl(), item.getPropertiesMap(), item.getTag());
            }
            events.add(event);
        }

        return events;
    }

    public static SimpleMessageWrapper buildSimpleMessage(CloudEvent cloudEvent) {
        String env = cloudEvent.getExtension(ProtocolKey.ENV) == null ? "env" : getEventExtension(cloudEvent, ProtocolKey.ENV);
        String idc = cloudEvent.getExtension(ProtocolKey.IDC) == null ? "idc" : getEventExtension(cloudEvent, ProtocolKey.IDC);
        String ip = cloudEvent.getExtension(ProtocolKey.IP) == null ? "ip" : getEventExtension(cloudEvent, ProtocolKey.IP);
        String pid = cloudEvent.getExtension(ProtocolKey.PID) == null ? "33" : getEventExtension(cloudEvent, ProtocolKey.PID);
        String sys = cloudEvent.getExtension(ProtocolKey.SYS) == null ? "sys" : getEventExtension(cloudEvent, ProtocolKey.SYS);
        String userName = cloudEvent.getExtension(ProtocolKey.USERNAME) == null ? "user" : getEventExtension(cloudEvent, ProtocolKey.USERNAME);
        String passwd = cloudEvent.getExtension(ProtocolKey.PASSWD) == null ? "pass" : getEventExtension(cloudEvent, ProtocolKey.PASSWD);
        String language = cloudEvent.getExtension(ProtocolKey.LANGUAGE) == null ? Constants.LANGUAGE_JAVA :
            getEventExtension(cloudEvent, ProtocolKey.LANGUAGE);
        String protocol = cloudEvent.getExtension(ProtocolKey.PROTOCOL_TYPE) == null ? "" :
            getEventExtension(cloudEvent, ProtocolKey.PROTOCOL_TYPE);
        String protocolDesc = cloudEvent.getExtension(ProtocolKey.PROTOCOL_DESC) == null ? "" :
            getEventExtension(cloudEvent, ProtocolKey.PROTOCOL_DESC);
        String protocolVersion = cloudEvent.getExtension(ProtocolKey.PROTOCOL_VERSION) == null ? "" :
            getEventExtension(cloudEvent, ProtocolKey.PROTOCOL_VERSION);
        String seqNum = cloudEvent.getExtension(ProtocolKey.SEQ_NUM) == null ? "" : getEventExtension(cloudEvent, ProtocolKey.SEQ_NUM);
        String uniqueId = cloudEvent.getExtension(ProtocolKey.UNIQUE_ID) == null ? "" : getEventExtension(cloudEvent, ProtocolKey.UNIQUE_ID);
        String producerGroup = cloudEvent.getExtension(ProtocolKey.PRODUCERGROUP) == null ? "" :
            getEventExtension(cloudEvent, ProtocolKey.PRODUCERGROUP);
        String ttl = cloudEvent.getExtension(ProtocolKey.TTL) == null ? "4000" : getEventExtension(cloudEvent, ProtocolKey.TTL);

        RequestHeader header = RequestHeader.newBuilder()
            .setEnv(env).setIdc(idc)
            .setIp(ip).setPid(pid)
            .setSys(sys).setUsername(userName).setPassword(passwd)
            .setLanguage(language).setProtocolType(protocol)
            .setProtocolDesc(protocolDesc).setProtocolVersion(protocolVersion)
            .build();

        SimpleMessage.Builder messageBuilder = SimpleMessage.newBuilder()
            .setHeader(header)
            .setContent(new String(Objects.requireNonNull(cloudEvent.getData()).toBytes(), StandardCharsets.UTF_8))
            .setProducerGroup(producerGroup)
            .setSeqNum(seqNum)
            .setUniqueId(uniqueId)
            .setTopic(cloudEvent.getSubject())
            .setTtl(ttl);

        for (String key : cloudEvent.getExtensionNames()) {
            messageBuilder.putProperties(key, getEventExtension(cloudEvent, key));
        }

        SimpleMessage simpleMessage = messageBuilder.build();

        return new SimpleMessageWrapper(simpleMessage);
    }

    private static String getEventExtension(CloudEvent event, String protocolKey) {
        return Objects.requireNonNull(event.getExtension(protocolKey)).toString();
    }
}
