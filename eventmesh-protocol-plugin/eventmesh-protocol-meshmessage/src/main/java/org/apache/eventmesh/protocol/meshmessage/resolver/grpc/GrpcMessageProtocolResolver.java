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

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.common.SimpleMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage.MessageItem;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class GrpcMessageProtocolResolver {

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

                cloudEventBuilder = cloudEventBuilder.withId(message.getSeqNum())
                    .withSubject(message.getTopic())
                    .withType("eventmeshmessage")
                    .withSource(URI.create("/"))
                    .withData(content.getBytes(StandardCharsets.UTF_8))
                    .withExtension(ProtocolKey.ENV, env)
                    .withExtension(ProtocolKey.IDC, idc)
                    .withExtension(ProtocolKey.IP, ip)
                    .withExtension(ProtocolKey.PID, pid)
                    .withExtension(ProtocolKey.SYS, sys)
                    .withExtension(ProtocolKey.USERNAME, username)
                    .withExtension(ProtocolKey.PASSWD, passwd)
                    .withExtension(ProtocolKey.LANGUAGE, language)
                    .withExtension(ProtocolKey.PROTOCOL_TYPE, protocolType)
                    .withExtension(ProtocolKey.PROTOCOL_DESC, protocolDesc)
                    .withExtension(ProtocolKey.PROTOCOL_VERSION, protocolVersion)
                    .withExtension(ProtocolKey.SEQ_NUM, message.getSeqNum())
                    .withExtension(ProtocolKey.UNIQUE_ID, message.getUniqueId())
                    .withExtension(ProtocolKey.PRODUCERGROUP, message.getProducerGroup())
                    .withExtension(ProtocolKey.TTL, message.getTtl());

                for (Map.Entry<String, String> entry : message.getPropertiesMap().entrySet()) {
                    cloudEventBuilder.withExtension(entry.getKey(), entry.getValue());
                }
                if (StringUtils.isNotEmpty(message.getTag())) {
                    cloudEventBuilder = cloudEventBuilder.withExtension(ProtocolKey.TAG, message.getTag());
                }
                event = cloudEventBuilder.build();
            } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v03();
                cloudEventBuilder = cloudEventBuilder.withId(message.getSeqNum())
                    .withSubject(message.getTopic())
                    .withType("eventmeshmessage")
                    .withSource(URI.create("/"))
                    .withData(content.getBytes(StandardCharsets.UTF_8))
                    .withExtension(ProtocolKey.ENV, env)
                    .withExtension(ProtocolKey.IDC, idc)
                    .withExtension(ProtocolKey.IP, ip)
                    .withExtension(ProtocolKey.PID, pid)
                    .withExtension(ProtocolKey.SYS, sys)
                    .withExtension(ProtocolKey.USERNAME, username)
                    .withExtension(ProtocolKey.PASSWD, passwd)
                    .withExtension(ProtocolKey.LANGUAGE, language)
                    .withExtension(ProtocolKey.PROTOCOL_TYPE, protocolType)
                    .withExtension(ProtocolKey.PROTOCOL_DESC, protocolDesc)
                    .withExtension(ProtocolKey.PROTOCOL_VERSION, protocolVersion)
                    .withExtension(ProtocolKey.SEQ_NUM, message.getSeqNum())
                    .withExtension(ProtocolKey.UNIQUE_ID, message.getUniqueId())
                    .withExtension(ProtocolKey.PRODUCERGROUP, message.getProducerGroup())
                    .withExtension(ProtocolKey.TTL, message.getTtl());

                for (Map.Entry<String, String> entry : message.getPropertiesMap().entrySet()) {
                    cloudEventBuilder.withExtension(entry.getKey(), entry.getValue());
                }
                if (StringUtils.isNotEmpty(message.getTag())) {
                    cloudEventBuilder = cloudEventBuilder.withExtension(ProtocolKey.TAG, message.getTag());
                }
                event = cloudEventBuilder.build();
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

                cloudEventBuilder = cloudEventBuilder.withId(item.getSeqNum())
                    .withSubject(message.getTopic())
                    .withType("eventmeshmessage")
                    .withSource(URI.create("/"))
                    .withData(content.getBytes(StandardCharsets.UTF_8))
                    .withExtension(ProtocolKey.ENV, env)
                    .withExtension(ProtocolKey.IDC, idc)
                    .withExtension(ProtocolKey.IP, ip)
                    .withExtension(ProtocolKey.PID, pid)
                    .withExtension(ProtocolKey.SYS, sys)
                    .withExtension(ProtocolKey.USERNAME, username)
                    .withExtension(ProtocolKey.PASSWD, passwd)
                    .withExtension(ProtocolKey.LANGUAGE, language)
                    .withExtension(ProtocolKey.PROTOCOL_TYPE, protocolType)
                    .withExtension(ProtocolKey.PROTOCOL_DESC, protocolDesc)
                    .withExtension(ProtocolKey.PROTOCOL_VERSION, protocolVersion)
                    .withExtension(ProtocolKey.SEQ_NUM, item.getSeqNum())
                    .withExtension(ProtocolKey.UNIQUE_ID, item.getUniqueId())
                    .withExtension(ProtocolKey.PRODUCERGROUP, message.getProducerGroup())
                    .withExtension(ProtocolKey.TTL, item.getTtl());

                for (Map.Entry<String, String> entry : item.getPropertiesMap().entrySet()) {
                    cloudEventBuilder.withExtension(entry.getKey(), entry.getValue());
                }
                if (StringUtils.isNotEmpty(item.getTag())) {
                    cloudEventBuilder = cloudEventBuilder.withExtension(ProtocolKey.TAG, item.getTag());
                }
                event = cloudEventBuilder.build();
            } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v03();
                cloudEventBuilder = cloudEventBuilder.withId(item.getSeqNum())
                    .withSubject(message.getTopic())
                    .withType("eventmeshmessage")
                    .withSource(URI.create("/"))
                    .withData(content.getBytes(StandardCharsets.UTF_8))
                    .withExtension(ProtocolKey.ENV, env)
                    .withExtension(ProtocolKey.IDC, idc)
                    .withExtension(ProtocolKey.IP, ip)
                    .withExtension(ProtocolKey.PID, pid)
                    .withExtension(ProtocolKey.SYS, sys)
                    .withExtension(ProtocolKey.USERNAME, username)
                    .withExtension(ProtocolKey.PASSWD, passwd)
                    .withExtension(ProtocolKey.LANGUAGE, language)
                    .withExtension(ProtocolKey.PROTOCOL_TYPE, protocolType)
                    .withExtension(ProtocolKey.PROTOCOL_DESC, protocolDesc)
                    .withExtension(ProtocolKey.PROTOCOL_VERSION, protocolVersion)
                    .withExtension(ProtocolKey.SEQ_NUM, item.getSeqNum())
                    .withExtension(ProtocolKey.UNIQUE_ID, item.getUniqueId())
                    .withExtension(ProtocolKey.PRODUCERGROUP, message.getProducerGroup())
                    .withExtension(ProtocolKey.TTL, item.getTtl());

                for (Map.Entry<String, String> entry : item.getPropertiesMap().entrySet()) {
                    cloudEventBuilder.withExtension(entry.getKey(), entry.getValue());
                }
                if (StringUtils.isNotEmpty(item.getTag())) {
                    cloudEventBuilder = cloudEventBuilder.withExtension(ProtocolKey.TAG, item.getTag());
                }
                event = cloudEventBuilder.build();
            }
            events.add(event);
        }

        return events;
    }

    public static SimpleMessageWrapper buildSimpleMessage(CloudEvent cloudEvent) {
        String env = cloudEvent.getExtension(ProtocolKey.ENV) == null ? "env" : cloudEvent.getExtension(ProtocolKey.ENV).toString();
        String idc = cloudEvent.getExtension(ProtocolKey.IDC) == null ? "idc" : cloudEvent.getExtension(ProtocolKey.IDC).toString();
        String ip = cloudEvent.getExtension(ProtocolKey.IP) == null ? "ip" : cloudEvent.getExtension(ProtocolKey.IP).toString();
        String pid = cloudEvent.getExtension(ProtocolKey.PID) == null ? "33" : cloudEvent.getExtension(ProtocolKey.PID).toString();
        String sys = cloudEvent.getExtension(ProtocolKey.SYS) == null ? "sys" : cloudEvent.getExtension(ProtocolKey.SYS).toString();
        String userName = cloudEvent.getExtension(ProtocolKey.USERNAME) == null ? "user" : cloudEvent.getExtension(ProtocolKey.USERNAME).toString();
        String passwd = cloudEvent.getExtension(ProtocolKey.PASSWD) == null ? "pass" : cloudEvent.getExtension(ProtocolKey.PASSWD).toString();
        String language = cloudEvent.getExtension(ProtocolKey.LANGUAGE) == null ? "JAVA" : cloudEvent.getExtension(ProtocolKey.LANGUAGE).toString();
        String protocol = cloudEvent.getExtension(ProtocolKey.PROTOCOL_TYPE) == null ? "" :
            cloudEvent.getExtension(ProtocolKey.PROTOCOL_TYPE).toString();
        String protocolDesc = cloudEvent.getExtension(ProtocolKey.PROTOCOL_DESC) == null ? "" :
            cloudEvent.getExtension(ProtocolKey.PROTOCOL_DESC).toString();
        String protocolVersion = cloudEvent.getExtension(ProtocolKey.PROTOCOL_VERSION) == null ? "" :
            cloudEvent.getExtension(ProtocolKey.PROTOCOL_VERSION).toString();
        String seqNum = cloudEvent.getExtension(ProtocolKey.SEQ_NUM) == null ? "" : cloudEvent.getExtension(ProtocolKey.SEQ_NUM).toString();
        String uniqueId = cloudEvent.getExtension(ProtocolKey.UNIQUE_ID) == null ? "" : cloudEvent.getExtension(ProtocolKey.UNIQUE_ID).toString();
        String producerGroup = cloudEvent.getExtension(ProtocolKey.PRODUCERGROUP) == null ? "" :
            cloudEvent.getExtension(ProtocolKey.PRODUCERGROUP).toString();
        String ttl = cloudEvent.getExtension(ProtocolKey.TTL) == null ? "4000" : cloudEvent.getExtension(ProtocolKey.TTL).toString();

        RequestHeader header = RequestHeader.newBuilder()
            .setEnv(env).setIdc(idc)
            .setIp(ip).setPid(pid)
            .setSys(sys).setUsername(userName).setPassword(passwd)
            .setLanguage(language).setProtocolType(protocol)
            .setProtocolDesc(protocolDesc).setProtocolVersion(protocolVersion)
            .build();

        SimpleMessage.Builder messageBuilder = SimpleMessage.newBuilder()
            .setHeader(header)
            .setContent(new String(cloudEvent.getData().toBytes(), StandardCharsets.UTF_8))
            .setProducerGroup(producerGroup)
            .setSeqNum(seqNum)
            .setUniqueId(uniqueId)
            .setTopic(cloudEvent.getSubject())
            .setTtl(ttl);

        for (String key : cloudEvent.getExtensionNames()) {
            messageBuilder.putProperties(key, cloudEvent.getExtension(key).toString());
        }

        SimpleMessage simpleMessage = messageBuilder.build();

        return new SimpleMessageWrapper(simpleMessage);
    }
}
