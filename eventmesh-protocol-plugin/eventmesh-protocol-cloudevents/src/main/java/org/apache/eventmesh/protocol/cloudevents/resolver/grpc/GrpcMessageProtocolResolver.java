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

import java.nio.charset.StandardCharsets;
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

        String contentType = message.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE, Constants.CONTENT_TYPE_CLOUDEVENTS_JSON);
        EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
        CloudEvent event = Objects.requireNonNull(eventFormat).deserialize(cloudEventJson.getBytes(StandardCharsets.UTF_8));

        RequestHeader header = message.getHeader();
        String env = StringUtils.defaultIfEmpty(header.getEnv(), getEventExtension(event, ProtocolKey.ENV));
        String idc = StringUtils.defaultIfEmpty(header.getIdc(), getEventExtension(event, ProtocolKey.IDC));
        String ip = StringUtils.defaultIfEmpty(header.getIp(), getEventExtension(event, ProtocolKey.IP));
        String pid = StringUtils.defaultIfEmpty(header.getPid(), getEventExtension(event, ProtocolKey.PID));
        String sys = StringUtils.defaultIfEmpty(header.getSys(), getEventExtension(event, ProtocolKey.SYS));
        String language = StringUtils.defaultIfEmpty(header.getLanguage(), getEventExtension(event, ProtocolKey.LANGUAGE));
        String protocolType = StringUtils.defaultIfEmpty(header.getProtocolType(), getEventExtension(event, ProtocolKey.PROTOCOL_TYPE));
        String protocolDesc = StringUtils.defaultIfEmpty(header.getProtocolDesc(), getEventExtension(event, ProtocolKey.PROTOCOL_DESC));
        String protocolVersion = StringUtils.defaultIfEmpty(header.getProtocolVersion(), getEventExtension(event, ProtocolKey.PROTOCOL_VERSION));
        String uniqueId = StringUtils.defaultIfEmpty(message.getUniqueId(), getEventExtension(event, ProtocolKey.UNIQUE_ID));
        String seqNum = StringUtils.defaultIfEmpty(message.getSeqNum(), getEventExtension(event, ProtocolKey.SEQ_NUM));
        String topic = StringUtils.defaultIfEmpty(message.getTopic(), event.getSubject());
        String username = StringUtils.defaultIfEmpty(header.getUsername(), getEventExtension(event, ProtocolKey.USERNAME));
        String passwd = StringUtils.defaultIfEmpty(header.getPassword(), getEventExtension(event, ProtocolKey.PASSWD));
        String ttl = StringUtils.defaultIfEmpty(message.getTtl(), getEventExtension(event, ProtocolKey.TTL));
        String producerGroup = StringUtils.defaultIfEmpty(message.getProducerGroup(), getEventExtension(event, ProtocolKey.PRODUCERGROUP));

        CloudEventBuilder eventBuilder;
        if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
            eventBuilder = CloudEventBuilder.v1(event);
        } else {
            eventBuilder = CloudEventBuilder.v03(event);
        }

        eventBuilder.withSubject(topic)
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
            .withExtension(ProtocolKey.TTL, ttl);

        message.getPropertiesMap().forEach(eventBuilder::withExtension);

        return eventBuilder.build();
    }

    public static SimpleMessageWrapper buildSimpleMessage(CloudEvent cloudEvent) {
        String env = getEventExtension(cloudEvent, ProtocolKey.ENV, "env");
        String idc = getEventExtension(cloudEvent, ProtocolKey.IDC, "idc");
        String ip = getEventExtension(cloudEvent, ProtocolKey.IP, "127.0.0.1");
        String pid = getEventExtension(cloudEvent, ProtocolKey.PID, "123");
        String sys = getEventExtension(cloudEvent, ProtocolKey.SYS, "sys123");
        String userName = getEventExtension(cloudEvent, ProtocolKey.USERNAME, "user");
        String passwd = getEventExtension(cloudEvent, ProtocolKey.PASSWD, "pass");
        String language = getEventExtension(cloudEvent, ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        String protocol = getEventExtension(cloudEvent, ProtocolKey.PROTOCOL_TYPE, "protocol");
        String protocolDesc = getEventExtension(cloudEvent, ProtocolKey.PROTOCOL_DESC, "protocolDesc");
        String protocolVersion = getEventExtension(cloudEvent, ProtocolKey.PROTOCOL_VERSION, "1.0");
        String seqNum = getEventExtension(cloudEvent, ProtocolKey.SEQ_NUM, "");
        String uniqueId = getEventExtension(cloudEvent, ProtocolKey.UNIQUE_ID, "");
        String producerGroup = getEventExtension(cloudEvent, ProtocolKey.PRODUCERGROUP, "producerGroup");
        String ttl = getEventExtension(cloudEvent, ProtocolKey.TTL, "3000");

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
            .setContent(new String(Objects.requireNonNull(eventFormat).serialize(cloudEvent), StandardCharsets.UTF_8))
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
            CloudEvent event = Objects.requireNonNull(eventFormat).deserialize(cloudEventJson.getBytes(StandardCharsets.UTF_8));

            String env = StringUtils.isEmpty(header.getEnv()) ? getEventExtension(event, ProtocolKey.ENV) : header.getEnv();
            String idc = StringUtils.isEmpty(header.getIdc()) ? getEventExtension(event, ProtocolKey.IDC) : header.getIdc();
            String ip = StringUtils.isEmpty(header.getIp()) ? getEventExtension(event, ProtocolKey.IP) : header.getIp();
            String pid = StringUtils.isEmpty(header.getPid()) ? getEventExtension(event, ProtocolKey.PID) : header.getPid();
            String sys = StringUtils.isEmpty(header.getSys()) ? getEventExtension(event, ProtocolKey.SYS) : header.getSys();

            String language = StringUtils.isEmpty(header.getLanguage())
                ? getEventExtension(event, ProtocolKey.LANGUAGE) : header.getLanguage();

            String protocolType = StringUtils.isEmpty(header.getProtocolType())
                ? getEventExtension(event, ProtocolKey.PROTOCOL_TYPE) : header.getProtocolType();

            String protocolDesc = StringUtils.isEmpty(header.getProtocolDesc())
                ? getEventExtension(event, ProtocolKey.PROTOCOL_DESC) : header.getProtocolDesc();

            String protocolVersion = StringUtils.isEmpty(header.getProtocolVersion())
                ? getEventExtension(event, ProtocolKey.PROTOCOL_VERSION) : header.getProtocolVersion();

            String username = StringUtils.isEmpty(header.getUsername()) ? getEventExtension(event, ProtocolKey.USERNAME) : header.getUsername();
            String passwd = StringUtils.isEmpty(header.getPassword()) ? getEventExtension(event, ProtocolKey.PASSWD) : header.getPassword();

            String seqNum = StringUtils.isEmpty(item.getSeqNum()) ? getEventExtension(event, ProtocolKey.SEQ_NUM) : item.getSeqNum();
            String uniqueId = StringUtils.isEmpty(item.getUniqueId()) ? getEventExtension(event, ProtocolKey.UNIQUE_ID) : item.getUniqueId();

            String topic = StringUtils.isEmpty(batchMessage.getTopic()) ? event.getSubject() : batchMessage.getTopic();

            String producerGroup = StringUtils.isEmpty(batchMessage.getProducerGroup())
                ? getEventExtension(event, ProtocolKey.PRODUCERGROUP) : batchMessage.getProducerGroup();
            String ttl = StringUtils.isEmpty(item.getTtl()) ? getEventExtension(event, ProtocolKey.TTL) : item.getTtl();

            CloudEventBuilder eventBuilder;
            if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
                eventBuilder = CloudEventBuilder.v1(event);
            } else {
                eventBuilder = CloudEventBuilder.v03(event);
            }

            eventBuilder.withSubject(topic)
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
                .withExtension(ProtocolKey.TTL, ttl);

            item.getPropertiesMap().forEach(eventBuilder::withExtension);

            cloudEvents.add(eventBuilder.build());
        }
        return cloudEvents;
    }
    
    private static String getEventExtension(CloudEvent event, String protocolKey) {
        return Objects.requireNonNull(event.getExtension(protocolKey)).toString();
    }

    private static String getEventExtension(CloudEvent event, String protocolKey, String defaultValue) {
        Object extension = event.getExtension(protocolKey);
        return Objects.isNull(extension) ? defaultValue : extension.toString();
    }
    
}