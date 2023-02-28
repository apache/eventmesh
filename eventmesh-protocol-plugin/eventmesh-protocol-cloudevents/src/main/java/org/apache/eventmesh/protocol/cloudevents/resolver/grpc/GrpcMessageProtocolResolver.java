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

        String seqNum = getMessageItemValue(message.getSeqNum(), event, ProtocolKey.SEQ_NUM);
        String uniqueId = getMessageItemValue(message.getUniqueId(), event, ProtocolKey.UNIQUE_ID);
        String ttl = getMessageItemValue(message.getTtl(), event, ProtocolKey.TTL);
        String producerGroup = getMessageItemValue(message.getProducerGroup(), event, ProtocolKey.PRODUCERGROUP);
        String topic = StringUtils.defaultIfEmpty(message.getTopic(), event.getSubject());

        CloudEventBuilder eventBuilder = builderCloudEventBuilder(header, event);
        eventBuilder.withSubject(topic)
            .withExtension(ProtocolKey.SEQ_NUM, seqNum)
            .withExtension(ProtocolKey.UNIQUE_ID, uniqueId)
            .withExtension(ProtocolKey.PRODUCERGROUP, producerGroup)
            .withExtension(ProtocolKey.TTL, ttl);

        message.getPropertiesMap().forEach(eventBuilder::withExtension);

        return eventBuilder.build();
    }

    public static SimpleMessageWrapper buildSimpleMessage(CloudEvent cloudEvent) {

        String env = getCloudEventExtension(cloudEvent, ProtocolKey.ENV, "env");
        String idc = getCloudEventExtension(cloudEvent, ProtocolKey.IDC, "idc");

        String ip = getCloudEventExtension(cloudEvent, ProtocolKey.IP, "127.0.0.1");
        String pid = getCloudEventExtension(cloudEvent, ProtocolKey.PID, "123");
        String sys = getCloudEventExtension(cloudEvent, ProtocolKey.SYS, "sys123");
        String userName = getCloudEventExtension(cloudEvent, ProtocolKey.USERNAME, "user");
        String passwd = getCloudEventExtension(cloudEvent, ProtocolKey.PASSWD, "pass");
        String language = getCloudEventExtension(cloudEvent, ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);

        String protocol = getCloudEventExtension(cloudEvent, ProtocolKey.PROTOCOL_TYPE, "protocol");
        String protocolDesc = getCloudEventExtension(cloudEvent, ProtocolKey.PROTOCOL_DESC, "protocolDesc");
        String protocolVersion = getCloudEventExtension(cloudEvent, ProtocolKey.PROTOCOL_VERSION, "1.0");
        String seqNum = getCloudEventExtension(cloudEvent, ProtocolKey.SEQ_NUM, "");
        String uniqueId = getCloudEventExtension(cloudEvent, ProtocolKey.UNIQUE_ID, "");

        String producerGroup = getCloudEventExtension(cloudEvent, ProtocolKey.PRODUCERGROUP, "producerGroup");
        String ttl = getCloudEventExtension(cloudEvent, ProtocolKey.TTL, "3000");

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

    private static String getCloudEventExtension(CloudEvent cloudEvent, String protocolKey, String defaultValue) {
        Object extension = cloudEvent.getExtension(protocolKey);
        return Objects.isNull(extension) ? defaultValue : extension.toString();
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

            CloudEventBuilder eventBuilder = builderCloudEventBuilder(header, event);

            eventBuilder.withSubject(topic)
                .withExtension(ProtocolKey.SEQ_NUM, seqNum)
                .withExtension(ProtocolKey.UNIQUE_ID, uniqueId)
                .withExtension(ProtocolKey.PRODUCERGROUP, producerGroup)
                .withExtension(ProtocolKey.TTL, ttl);

            item.getPropertiesMap().forEach(eventBuilder::withExtension);

            cloudEvents.add(eventBuilder.build());
        }
        return cloudEvents;
    }

    private static String getHeaderValue(String value, CloudEvent event, String key) {
        return StringUtils.isEmpty(value) ? Objects.requireNonNull(event.getExtension(key)).toString() : value;
    }

    private static String getMessageItemValue(String value, CloudEvent event, String key) {
        return StringUtils.isEmpty(value) ? Objects.requireNonNull(event.getExtension(key)).toString() : value;
    }

    private static CloudEventBuilder builderCloudEventBuilder(RequestHeader header, CloudEvent event) {
        String env = getHeaderValue(header.getEnv(), event, ProtocolKey.ENV);
        String idc = getHeaderValue(header.getIdc(), event, ProtocolKey.IDC);
        String ip = getHeaderValue(header.getIp(), event, ProtocolKey.IP);
        String pid = getHeaderValue(header.getPid(), event, ProtocolKey.PID);
        String sys = getHeaderValue(header.getSys(), event, ProtocolKey.SYS);
        String language = getHeaderValue(header.getLanguage(), event, ProtocolKey.LANGUAGE);
        String protocolType = getHeaderValue(header.getProtocolType(), event, ProtocolKey.PROTOCOL_TYPE);
        String protocolDesc = getHeaderValue(header.getProtocolDesc(), event, ProtocolKey.PROTOCOL_DESC);
        String protocolVersion = getHeaderValue(header.getProtocolVersion(), event, ProtocolKey.PROTOCOL_VERSION);
        String username = getHeaderValue(header.getUsername(), event, ProtocolKey.USERNAME);
        String passwd = getHeaderValue(header.getPassword(), event, ProtocolKey.PASSWD);

        CloudEventBuilder eventBuilder = StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)
            ? CloudEventBuilder.v1(event) : CloudEventBuilder.v03(event);

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
            .withExtension(ProtocolKey.PROTOCOL_VERSION, protocolVersion);

    }
    
    private static String getEventExtension(CloudEvent event, String protocolKey) {
        return Objects.requireNonNull(event.getExtension(protocolKey)).toString();
    }

    private static String getEventExtension(CloudEvent event, String protocolKey, String defaultValue) {
        Object extension = event.getExtension(protocolKey);
        return Objects.isNull(extension) ? defaultValue : extension.toString();
    }

}
