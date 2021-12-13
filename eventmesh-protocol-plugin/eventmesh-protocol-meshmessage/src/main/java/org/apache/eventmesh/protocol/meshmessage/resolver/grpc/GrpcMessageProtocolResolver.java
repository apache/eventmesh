package org.apache.eventmesh.protocol.meshmessage.resolver.grpc;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

public class GrpcMessageProtocolResolver {

    public static CloudEvent buildEvent(EventMeshMessage message) throws ProtocolHandleException {
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
}
