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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.SimpleMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;

public class GrpcMessageProtocolResolver {

    public static CloudEvent buildEvent(SimpleMessage message) {
        String cloudEventJson = message.getContent();

        String contentType = message.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE, Constants.CONTENT_TYPE_CLOUDEVENTS_JSON);
        EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
        CloudEvent event = Objects.requireNonNull(eventFormat).deserialize(cloudEventJson.getBytes(Constants.DEFAULT_CHARSET));

        RequestHeader header = message.getHeader();

        String seqNum = getEventExtensionIfAbsent(message.getSeqNum(), event, ProtocolKey.SEQ_NUM);
        String uniqueId = getEventExtensionIfAbsent(message.getUniqueId(), event, ProtocolKey.UNIQUE_ID);
        String ttl = getEventExtensionIfAbsent(message.getTtl(), event, ProtocolKey.TTL);
        String producerGroup = getEventExtensionIfAbsent(message.getProducerGroup(), event, ProtocolKey.PRODUCERGROUP);
        String topic = StringUtils.defaultIfEmpty(message.getTopic(), event.getSubject());

        CloudEventBuilder eventBuilder = builderCloudEventBuilder(header, event, seqNum, uniqueId, producerGroup, ttl,
                topic);

        message.getPropertiesMap().forEach(eventBuilder::withExtension);

        return eventBuilder.build();
    }

    public static SimpleMessageWrapper buildSimpleMessage(CloudEvent cloudEvent) {
        String env = getEventExtensionOrDefault(cloudEvent, ProtocolKey.ENV, "env");
        String idc = getEventExtensionOrDefault(cloudEvent, ProtocolKey.IDC, "idc");
        String ip = getEventExtensionOrDefault(cloudEvent, ProtocolKey.IP, "127.0.0.1");
        String pid = getEventExtensionOrDefault(cloudEvent, ProtocolKey.PID, "123");
        String sys = getEventExtensionOrDefault(cloudEvent, ProtocolKey.SYS, "sys123");
        String userName = getEventExtensionOrDefault(cloudEvent, ProtocolKey.USERNAME, "user");
        String passwd = getEventExtensionOrDefault(cloudEvent, ProtocolKey.PASSWD, "pass");
        String language = getEventExtensionOrDefault(cloudEvent, ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        String protocol = getEventExtensionOrDefault(cloudEvent, ProtocolKey.PROTOCOL_TYPE, "protocol");
        String protocolDesc = getEventExtensionOrDefault(cloudEvent, ProtocolKey.PROTOCOL_DESC, "protocolDesc");
        String protocolVersion = getEventExtensionOrDefault(cloudEvent, ProtocolKey.PROTOCOL_VERSION, "1.0");
        String seqNum = getEventExtensionOrDefault(cloudEvent, ProtocolKey.SEQ_NUM, "");
        String uniqueId = getEventExtensionOrDefault(cloudEvent, ProtocolKey.UNIQUE_ID, "");
        String producerGroup = getEventExtensionOrDefault(cloudEvent, ProtocolKey.PRODUCERGROUP, "producerGroup");
        String ttl = getEventExtensionOrDefault(cloudEvent, ProtocolKey.TTL, "3000");

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
            .setContent(new String(Objects.requireNonNull(eventFormat).serialize(cloudEvent), Constants.DEFAULT_CHARSET))
            .setProducerGroup(producerGroup)
            .setSeqNum(seqNum)
            .setUniqueId(uniqueId)
            .setTopic(cloudEvent.getSubject())
            .setTtl(ttl)
            .putProperties(ProtocolKey.CONTENT_TYPE, contentType);

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

            String contentType = item.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE, Constants.CONTENT_TYPE_CLOUDEVENTS_JSON);
            EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
            CloudEvent event = Objects.requireNonNull(eventFormat).deserialize(cloudEventJson.getBytes(Constants.DEFAULT_CHARSET));

            String seqNum = getEventExtensionIfAbsent(item.getSeqNum(), event, ProtocolKey.SEQ_NUM);
            String uniqueId = getEventExtensionIfAbsent(item.getUniqueId(), event, ProtocolKey.UNIQUE_ID);
            String producerGroup = getEventExtensionIfAbsent(batchMessage.getProducerGroup(), event, ProtocolKey.PRODUCERGROUP);
            String ttl = getEventExtensionIfAbsent(item.getTtl(), event, ProtocolKey.TTL);
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
        String env = getEventExtensionIfAbsent(header.getEnv(), event, ProtocolKey.ENV);
        String idc = getEventExtensionIfAbsent(header.getIdc(), event, ProtocolKey.IDC);
        String ip = getEventExtensionIfAbsent(header.getIp(), event, ProtocolKey.IP);
        String pid = getEventExtensionIfAbsent(header.getPid(), event, ProtocolKey.PID);
        String sys = getEventExtensionIfAbsent(header.getSys(), event, ProtocolKey.SYS);
        String language = getEventExtensionIfAbsent(header.getLanguage(), event, ProtocolKey.LANGUAGE);
        String protocolType = getEventExtensionIfAbsent(header.getProtocolType(), event, ProtocolKey.PROTOCOL_TYPE);
        String protocolDesc = getEventExtensionIfAbsent(header.getProtocolDesc(), event, ProtocolKey.PROTOCOL_DESC);
        String protocolVersion = getEventExtensionIfAbsent(header.getProtocolVersion(), event, ProtocolKey.PROTOCOL_VERSION);
        String username = getEventExtensionIfAbsent(header.getUsername(), event, ProtocolKey.USERNAME);
        String passwd = getEventExtensionIfAbsent(header.getPassword(), event, ProtocolKey.PASSWD);

        CloudEventBuilder eventBuilder = CloudEventBuilder.from(event);

        return eventBuilder
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
            .withExtension(ProtocolKey.SEQ_NUM, seqNum)
            .withExtension(ProtocolKey.UNIQUE_ID, uniqueId)
            .withExtension(ProtocolKey.PRODUCERGROUP, producerGroup)
            .withExtension(ProtocolKey.TTL, ttl)
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
