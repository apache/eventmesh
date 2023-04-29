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

package org.apache.eventmesh.protocol.cloudevents.resolver.grpc;

import static org.apache.eventmesh.common.Constants.CONTENT_TYPE_CLOUDEVENTS_JSON;
import static org.apache.eventmesh.common.Constants.DEFAULT_CHARSET;
import static org.apache.eventmesh.common.Constants.LANGUAGE_JAVA;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.CONTENT_TYPE;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.ENV;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.IDC;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.IP;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.LANGUAGE;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.PASSWD;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.PID;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.PRODUCERGROUP;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.PROTOCOL_DESC;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.PROTOCOL_TYPE;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.PROTOCOL_VERSION;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.SEQ_NUM;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.SYS;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.TTL;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.UNIQUE_ID;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.USERNAME;

import org.apache.eventmesh.common.protocol.grpc.common.SimpleMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;

public class GrpcMessageProtocolResolver {

    public static CloudEvent buildEvent(SimpleMessage message) {
        String cloudEventJson = message.getContent();

        String contentType = message.getPropertiesOrDefault(CONTENT_TYPE, CONTENT_TYPE_CLOUDEVENTS_JSON);
        EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
        CloudEvent event = Objects.requireNonNull(eventFormat).deserialize(cloudEventJson.getBytes(DEFAULT_CHARSET));

        RequestHeader header = message.getHeader();

        String seqNum = getEventExtensionIfAbsent(message.getSeqNum(), event, SEQ_NUM);
        String uniqueId = getEventExtensionIfAbsent(message.getUniqueId(), event, UNIQUE_ID);
        String ttl = getEventExtensionIfAbsent(message.getTtl(), event, TTL);
        String producerGroup = getEventExtensionIfAbsent(message.getProducerGroup(), event, PRODUCERGROUP);
        String topic = StringUtils.defaultIfEmpty(message.getTopic(), event.getSubject());

        CloudEventBuilder eventBuilder = builderCloudEventBuilder(header, event, seqNum, uniqueId, producerGroup, ttl,
                topic);

        message.getPropertiesMap().forEach(eventBuilder::withExtension);

        return eventBuilder.build();
    }

    public static SimpleMessageWrapper buildSimpleMessage(CloudEvent cloudEvent) {
        String env = getEventExtensionOrDefault(cloudEvent, ENV, "env");
        String idc = getEventExtensionOrDefault(cloudEvent, IDC, "idc");
        String ip = getEventExtensionOrDefault(cloudEvent, IP, "127.0.0.1");
        String pid = getEventExtensionOrDefault(cloudEvent, PID, "123");
        String sys = getEventExtensionOrDefault(cloudEvent, SYS, "sys123");
        String userName = getEventExtensionOrDefault(cloudEvent, USERNAME, "user");
        String passwd = getEventExtensionOrDefault(cloudEvent, PASSWD, "pass");
        String language = getEventExtensionOrDefault(cloudEvent, LANGUAGE, LANGUAGE_JAVA);
        String protocol = getEventExtensionOrDefault(cloudEvent, PROTOCOL_TYPE, "protocol");
        String protocolDesc = getEventExtensionOrDefault(cloudEvent, PROTOCOL_DESC, "protocolDesc");
        String protocolVersion = getEventExtensionOrDefault(cloudEvent, PROTOCOL_VERSION, "1.0");
        String seqNum = getEventExtensionOrDefault(cloudEvent, SEQ_NUM, "");
        String uniqueId = getEventExtensionOrDefault(cloudEvent, UNIQUE_ID, "");
        String producerGroup = getEventExtensionOrDefault(cloudEvent, PRODUCERGROUP, "producerGroup");
        String ttl = getEventExtensionOrDefault(cloudEvent, TTL, "3000");

        RequestHeader header = RequestHeader.newBuilder()
            .setEnv(env).setIdc(idc)
            .setIp(ip).setPid(pid)
            .setSys(sys).setUsername(userName).setPassword(passwd)
            .setLanguage(language).setProtocolType(protocol)
            .setProtocolDesc(protocolDesc).setProtocolVersion(protocolVersion)
            .build();

        String contentType = Objects.requireNonNull(cloudEvent.getDataContentType());
        EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);

        SimpleMessage.Builder messageBuilder = SimpleMessage.newBuilder()
            .setHeader(header)
            .setContent(new String(Objects.requireNonNull(eventFormat).serialize(cloudEvent), DEFAULT_CHARSET))
            .setProducerGroup(producerGroup)
            .setSeqNum(seqNum)
            .setUniqueId(uniqueId)
            .setTopic(cloudEvent.getSubject())
            .setTtl(ttl)
            .putProperties(CONTENT_TYPE, contentType);

        for (String key : cloudEvent.getExtensionNames()) {
            messageBuilder.putProperties(key, Objects.requireNonNull(cloudEvent.getExtension(key)).toString());
        }

        return new SimpleMessageWrapper(messageBuilder.build());
    }

    public static List<CloudEvent> buildBatchEvents(BatchMessage batchMessage) {
        List<CloudEvent> cloudEvents = new ArrayList<>();

        RequestHeader header = batchMessage.getHeader();

        for (BatchMessage.MessageItem item : batchMessage.getMessageItemList()) {
            String cloudEventJson = item.getContent();

            String contentType = item.getPropertiesOrDefault(CONTENT_TYPE, CONTENT_TYPE_CLOUDEVENTS_JSON);
            EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
            CloudEvent event = Objects.requireNonNull(eventFormat).deserialize(cloudEventJson.getBytes(DEFAULT_CHARSET));

            String seqNum = getEventExtensionIfAbsent(item.getSeqNum(), event, SEQ_NUM);
            String uniqueId = getEventExtensionIfAbsent(item.getUniqueId(), event, UNIQUE_ID);
            String producerGroup = getEventExtensionIfAbsent(batchMessage.getProducerGroup(), event, PRODUCERGROUP);
            String ttl = getEventExtensionIfAbsent(item.getTtl(), event, TTL);
            String topic = StringUtils.defaultIfEmpty(batchMessage.getTopic(), event.getSubject());

            CloudEventBuilder eventBuilder = builderCloudEventBuilder(header, event, seqNum, uniqueId, producerGroup,
                    ttl, topic);

            item.getPropertiesMap().forEach(eventBuilder::withExtension);

            cloudEvents.add(eventBuilder.build());
        }
        return cloudEvents;
    }

    private static CloudEventBuilder builderCloudEventBuilder(RequestHeader header, CloudEvent event, String seqNum,
                                                              String uniqueId, String producerGroup, String ttl,
                                                              String topic) {
        String env = getEventExtensionIfAbsent(header.getEnv(), event, ENV);
        String idc = getEventExtensionIfAbsent(header.getIdc(), event, IDC);
        String ip = getEventExtensionIfAbsent(header.getIp(), event, IP);
        String pid = getEventExtensionIfAbsent(header.getPid(), event, PID);
        String sys = getEventExtensionIfAbsent(header.getSys(), event, SYS);
        String language = getEventExtensionIfAbsent(header.getLanguage(), event, LANGUAGE);
        String protocolType = getEventExtensionIfAbsent(header.getProtocolType(), event, PROTOCOL_TYPE);
        String protocolDesc = getEventExtensionIfAbsent(header.getProtocolDesc(), event, PROTOCOL_DESC);
        String protocolVersion = getEventExtensionIfAbsent(header.getProtocolVersion(), event, PROTOCOL_VERSION);
        String username = getEventExtensionIfAbsent(header.getUsername(), event, USERNAME);
        String passwd = getEventExtensionIfAbsent(header.getPassword(), event, PASSWD);

        CloudEventBuilder eventBuilder = CloudEventBuilder.fromSpecVersion(SpecVersion.parse(protocolVersion));

        return eventBuilder
            .withExtension(ENV, env)
            .withExtension(IDC, idc)
            .withExtension(IP, ip)
            .withExtension(PID, pid)
            .withExtension(SYS, sys)
            .withExtension(USERNAME, username)
            .withExtension(PASSWD, passwd)
            .withExtension(LANGUAGE, language)
            .withExtension(PROTOCOL_TYPE, protocolType)
            .withExtension(PROTOCOL_DESC, protocolDesc)
            .withExtension(PROTOCOL_VERSION, protocolVersion)
            .withExtension(SEQ_NUM, seqNum)
            .withExtension(UNIQUE_ID, uniqueId)
            .withExtension(PRODUCERGROUP, producerGroup)
            .withExtension(TTL, ttl)
            .withSubject(topic);

    }

    private static String getEventExtensionOrDefault(CloudEvent event, String protocolKey, String defaultValue) {
        Object extension = event.getExtension(protocolKey);
        return Objects.isNull(extension) ? defaultValue : extension.toString();
    }

    private static String getEventExtensionIfAbsent(String value, CloudEvent event, String key) {
        return StringUtils.isEmpty(value) ? Objects.requireNonNull(event.getExtension(key)).toString() : value;
    }

}
