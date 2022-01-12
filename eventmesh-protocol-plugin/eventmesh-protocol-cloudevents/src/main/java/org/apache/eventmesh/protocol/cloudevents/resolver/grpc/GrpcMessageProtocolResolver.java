package org.apache.eventmesh.protocol.cloudevents.resolver.grpc;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GrpcMessageProtocolResolver {

    public static CloudEvent buildEvent(EventMeshMessage message) {
        String cloudEventJson = message.getContent();

        String contentType = message.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE, "application/cloudevents+json");
        EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);
        CloudEvent event = eventFormat.deserialize(cloudEventJson.getBytes(StandardCharsets.UTF_8));

        RequestHeader header = message.getHeader();
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

        String uniqueId = StringUtils.isEmpty(message.getUniqueId())
            ? event.getExtension(ProtocolKey.UNIQUE_ID).toString() : message.getUniqueId();

        String seqNum = StringUtils.isEmpty(message.getSeqNum())
            ? event.getExtension(ProtocolKey.SEQ_NUM).toString() : header.getProtocolVersion();

        String username = StringUtils.isEmpty(header.getUsername()) ? event.getExtension(ProtocolKey.USERNAME).toString() : header.getUsername();
        String passwd = StringUtils.isEmpty(header.getPassword()) ? event.getExtension(ProtocolKey.PASSWD).toString() : header.getPassword();
        String ttl = StringUtils.isEmpty(message.getTtl()) ? event.getExtension(ProtocolKey.TTL).toString() : message.getTtl();

        String producerGroup = StringUtils.isEmpty(message.getProducerGroup())
            ? event.getExtension(ProtocolKey.PRODUCERGROUP).toString() : message.getProducerGroup();

        return CloudEventBuilder.from(event)
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
            .withExtension(ProtocolKey.TTL, ttl).build();
    }

    public static EventMeshMessageWrapper buildEventMeshMessage(CloudEvent cloudEvent) {
        String env = cloudEvent.getExtension(ProtocolKey.ENV) == null ? null : cloudEvent.getExtension(ProtocolKey.ENV).toString();
        String idc = cloudEvent.getExtension(ProtocolKey.IDC) == null ? null : cloudEvent.getExtension(ProtocolKey.IDC).toString();
        String ip = cloudEvent.getExtension(ProtocolKey.IP) == null ? null : cloudEvent.getExtension(ProtocolKey.IP).toString();
        String pid = cloudEvent.getExtension(ProtocolKey.PID) == null ? null : cloudEvent.getExtension(ProtocolKey.PID).toString();
        String sys = cloudEvent.getExtension(ProtocolKey.SYS) == null ? null : cloudEvent.getExtension(ProtocolKey.SYS).toString();
        String userName = cloudEvent.getExtension(ProtocolKey.USERNAME) == null ? null : cloudEvent.getExtension(ProtocolKey.USERNAME).toString();
        String passwd = cloudEvent.getExtension(ProtocolKey.PASSWD) == null ? null : cloudEvent.getExtension(ProtocolKey.PASSWD).toString();
        String language = cloudEvent.getExtension(ProtocolKey.LANGUAGE) == null ? null : cloudEvent.getExtension(ProtocolKey.LANGUAGE).toString();
        String protocol = cloudEvent.getExtension(ProtocolKey.PROTOCOL_TYPE) == null ? null :
            cloudEvent.getExtension(ProtocolKey.PROTOCOL_TYPE).toString();
        String protocolDesc = cloudEvent.getExtension(ProtocolKey.PROTOCOL_DESC) == null ? null :
            cloudEvent.getExtension(ProtocolKey.PROTOCOL_DESC).toString();
        String protocolVersion = cloudEvent.getExtension(ProtocolKey.PROTOCOL_VERSION) == null ? null :
            cloudEvent.getExtension(ProtocolKey.PROTOCOL_VERSION).toString();
        String seqNum = cloudEvent.getExtension(ProtocolKey.SEQ_NUM) == null ? null : cloudEvent.getExtension(ProtocolKey.SEQ_NUM).toString();
        String uniqueId = cloudEvent.getExtension(ProtocolKey.UNIQUE_ID) == null ? null : cloudEvent.getExtension(ProtocolKey.UNIQUE_ID).toString();
        String producerGroup = cloudEvent.getExtension(ProtocolKey.PRODUCERGROUP) == null ? null :
            cloudEvent.getExtension(ProtocolKey.PRODUCERGROUP).toString();
        String ttl = cloudEvent.getExtension(ProtocolKey.TTL) == null ? null : cloudEvent.getExtension(ProtocolKey.TTL).toString();

        RequestHeader header = RequestHeader.newBuilder()
            .setEnv(env).setIdc(idc)
            .setIp(ip).setPid(pid)
            .setSys(sys).setUsername(userName).setPassword(passwd)
            .setLanguage(language).setProtocolType(protocol)
            .setProtocolDesc(protocolDesc).setProtocolVersion(protocolVersion)
            .build();

        String contentType = cloudEvent.getDataContentType();
        EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(contentType);

        EventMeshMessage.Builder messageBuilder = EventMeshMessage.newBuilder()
            .setHeader(header)
            .setContent(new String( eventFormat.serialize(cloudEvent), StandardCharsets.UTF_8))
            .setProducerGroup(producerGroup)
            .setSeqNum(seqNum)
            .setUniqueId(uniqueId)
            .setTopic(cloudEvent.getSubject())
            .setTtl(ttl)
            .putProperties(ProtocolKey.CONTENT_TYPE, contentType) ;

        for (String key : cloudEvent.getExtensionNames()) {
            messageBuilder.putProperties(key, cloudEvent.getExtension(key).toString());
        }

        EventMeshMessage eventMeshMessage = messageBuilder.build();

        return new EventMeshMessageWrapper(eventMeshMessage);
    }

    public static List<CloudEvent> buildBatchEvents(BatchMessage batchMessage) {
        List<CloudEvent> cloudEvents = new ArrayList<>();

        RequestHeader header = batchMessage.getHeader();

        for (BatchMessage.MessageItem item : batchMessage.getMessageItemList()) {
            String cloudEventJson = item.getContent();

            String contentType = item.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE, "application/cloudevents+json");
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

            CloudEvent enhancedEvent = CloudEventBuilder.from(event)
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
                .build();

            cloudEvents.add(enhancedEvent);
        }
        return cloudEvents;
    }
}
