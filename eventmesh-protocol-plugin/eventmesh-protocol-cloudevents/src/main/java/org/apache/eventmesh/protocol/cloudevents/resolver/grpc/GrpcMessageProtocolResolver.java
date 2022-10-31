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
        CloudEvent event = eventFormat.deserialize(cloudEventJson.getBytes(StandardCharsets.UTF_8));

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
        String env = cloudEvent.getExtension(ProtocolKey.ENV) == null ? "env" : cloudEvent.getExtension(ProtocolKey.ENV).toString();
        String idc = cloudEvent.getExtension(ProtocolKey.IDC) == null ? "idc" : cloudEvent.getExtension(ProtocolKey.IDC).toString();
        String ip = cloudEvent.getExtension(ProtocolKey.IP) == null ? "127.0.0.1" : cloudEvent.getExtension(ProtocolKey.IP).toString();
        String pid = cloudEvent.getExtension(ProtocolKey.PID) == null ? "123" : cloudEvent.getExtension(ProtocolKey.PID).toString();
        String sys = cloudEvent.getExtension(ProtocolKey.SYS) == null ? "sys123" : cloudEvent.getExtension(ProtocolKey.SYS).toString();
        String userName = cloudEvent.getExtension(ProtocolKey.USERNAME) == null ? "user" : cloudEvent.getExtension(ProtocolKey.USERNAME).toString();
        String passwd = cloudEvent.getExtension(ProtocolKey.PASSWD) == null ? "pass" : cloudEvent.getExtension(ProtocolKey.PASSWD).toString();
        String language = cloudEvent.getExtension(ProtocolKey.LANGUAGE) == null ? Constants.LANGUAGE_JAVA :
            cloudEvent.getExtension(ProtocolKey.LANGUAGE).toString();
        String protocol = cloudEvent.getExtension(ProtocolKey.PROTOCOL_TYPE) == null ? "protocol" :
            cloudEvent.getExtension(ProtocolKey.PROTOCOL_TYPE).toString();
        String protocolDesc = cloudEvent.getExtension(ProtocolKey.PROTOCOL_DESC) == null ? "protocolDesc" :
            cloudEvent.getExtension(ProtocolKey.PROTOCOL_DESC).toString();
        String protocolVersion = cloudEvent.getExtension(ProtocolKey.PROTOCOL_VERSION) == null ? "1.0" :
            cloudEvent.getExtension(ProtocolKey.PROTOCOL_VERSION).toString();
        String seqNum = cloudEvent.getExtension(ProtocolKey.SEQ_NUM) == null ? "" : cloudEvent.getExtension(ProtocolKey.SEQ_NUM).toString();
        String uniqueId = cloudEvent.getExtension(ProtocolKey.UNIQUE_ID) == null ? "" : cloudEvent.getExtension(ProtocolKey.UNIQUE_ID).toString();
        String producerGroup = cloudEvent.getExtension(ProtocolKey.PRODUCERGROUP) == null ? "producerGroup" :
            cloudEvent.getExtension(ProtocolKey.PRODUCERGROUP).toString();
        String ttl = cloudEvent.getExtension(ProtocolKey.TTL) == null ? "3000" : cloudEvent.getExtension(ProtocolKey.TTL).toString();

        RequestHeader header = RequestHeader.newBuilder()
            .setEnv(env).setIdc(idc)
            .setIp(ip).setPid(pid)
            .setSys(sys).setUsername(userName).setPassword(passwd)
            .setLanguage(language).setProtocolType(protocol)
            .setProtocolDesc(protocolDesc).setProtocolVersion(protocolVersion)
            .build();

        String contentType = cloudEvent.getDataContentType();
        EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);

        SimpleMessage.Builder messageBuilder = SimpleMessage.newBuilder()
            .setHeader(header)
            .setContent(new String(eventFormat.serialize(cloudEvent), StandardCharsets.UTF_8))
            .setProducerGroup(producerGroup)
            .setSeqNum(seqNum)
            .setUniqueId(uniqueId)
            .setTopic(cloudEvent.getSubject())
            .setTtl(ttl)
            .putProperties(ProtocolKey.CONTENT_TYPE, contentType);

        for (String key : cloudEvent.getExtensionNames()) {
            messageBuilder.putProperties(key, cloudEvent.getExtension(key).toString());
        }

        SimpleMessage simpleMessage = messageBuilder.build();

        return new SimpleMessageWrapper(simpleMessage);
    }

    public static List<CloudEvent> buildBatchEvents(BatchMessage batchMessage) {
        List<CloudEvent> cloudEvents = new ArrayList<>();

        RequestHeader header = batchMessage.getHeader();

        for (BatchMessage.MessageItem item : batchMessage.getMessageItemList()) {
            String cloudEventJson = item.getContent();

            String contentType = item.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE, Constants.CONTENT_TYPE_CLOUDEVENTS_JSON);
            EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
            CloudEvent event = eventFormat.deserialize(cloudEventJson.getBytes(StandardCharsets.UTF_8));

            String env = StringUtils.isEmpty(header.getEnv()) ? event.getExtension(ProtocolKey.ENV).toString() : header.getEnv();
            String idc = StringUtils.isEmpty(header.getIdc()) ? event.getExtension(ProtocolKey.IDC).toString() : header.getIdc();
            String ip = StringUtils.isEmpty(header.getIp()) ? event.getExtension(ProtocolKey.IP).toString() : header.getIp();
            String pid = StringUtils.isEmpty(header.getPid()) ? event.getExtension(ProtocolKey.PID).toString() : header.getPid();
            String sys = StringUtils.isEmpty(header.getSys()) ? event.getExtension(ProtocolKey.SYS).toString() : header.getSys();

            String language = StringUtils.isEmpty(header.getLanguage())
                ? event.getExtension(ProtocolKey.LANGUAGE).toString() : header.getLanguage();

            String protocolType = StringUtils.isEmpty(header.getProtocolType())
                ? event.getExtension(ProtocolKey.PROTOCOL_TYPE).toString() : header.getProtocolType();

            String protocolDesc = StringUtils.isEmpty(header.getProtocolDesc())
                ? event.getExtension(ProtocolKey.PROTOCOL_DESC).toString() : header.getProtocolDesc();

            String protocolVersion = StringUtils.isEmpty(header.getProtocolVersion())
                ? event.getExtension(ProtocolKey.PROTOCOL_VERSION).toString() : header.getProtocolVersion();

            String username = StringUtils.isEmpty(header.getUsername()) ? event.getExtension(ProtocolKey.USERNAME).toString() : header.getUsername();
            String passwd = StringUtils.isEmpty(header.getPassword()) ? event.getExtension(ProtocolKey.PASSWD).toString() : header.getPassword();

            String seqNum = StringUtils.isEmpty(item.getSeqNum()) ? event.getExtension(ProtocolKey.SEQ_NUM).toString() : item.getSeqNum();
            String uniqueId = StringUtils.isEmpty(item.getUniqueId()) ? event.getExtension(ProtocolKey.UNIQUE_ID).toString() : item.getUniqueId();

            String topic = StringUtils.isEmpty(batchMessage.getTopic()) ? event.getSubject() : batchMessage.getTopic();

            String producerGroup = StringUtils.isEmpty(batchMessage.getProducerGroup())
                ? event.getExtension(ProtocolKey.PRODUCERGROUP).toString() : batchMessage.getProducerGroup();
            String ttl = StringUtils.isEmpty(item.getTtl()) ? event.getExtension(ProtocolKey.TTL).toString() : item.getTtl();

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
    
}