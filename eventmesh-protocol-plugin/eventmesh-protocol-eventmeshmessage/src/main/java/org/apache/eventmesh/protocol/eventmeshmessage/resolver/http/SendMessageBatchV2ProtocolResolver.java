package org.apache.eventmesh.protocol.eventmeshmessage.resolver.http;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.v03.CloudEventV03;
import io.cloudevents.core.v1.CloudEventV1;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchV2RequestBody;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2RequestHeader;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.nio.charset.StandardCharsets;

public class SendMessageBatchV2ProtocolResolver {
    public static CloudEvent buildEvent(Header header, Body body) throws ProtocolHandleException {
        try {
            SendMessageBatchV2RequestHeader sendMessageBatchV2RequestHeader = (SendMessageBatchV2RequestHeader) header;
            SendMessageBatchV2RequestBody sendMessageBatchV2RequestBody = (SendMessageBatchV2RequestBody) body;

            String protocolType = sendMessageBatchV2RequestHeader.getProtocolType();
            String protocolDesc = sendMessageBatchV2RequestHeader.getProtocolDesc();
            String protocolVersion = sendMessageBatchV2RequestHeader.getProtocolVersion();

            String code = sendMessageBatchV2RequestHeader.getCode();
            String env = sendMessageBatchV2RequestHeader.getEnv();
            String idc = sendMessageBatchV2RequestHeader.getIdc();
            String ip = sendMessageBatchV2RequestHeader.getIp();
            String pid = sendMessageBatchV2RequestHeader.getPid();
            String sys = sendMessageBatchV2RequestHeader.getSys();
            String username = sendMessageBatchV2RequestHeader.getUsername();
            String passwd = sendMessageBatchV2RequestHeader.getPasswd();
            ProtocolVersion version = sendMessageBatchV2RequestHeader.getVersion();
            String language = sendMessageBatchV2RequestHeader.getLanguage();

            String content = sendMessageBatchV2RequestBody.getMsg();

            CloudEvent event = null;
            CloudEventBuilder cloudEventBuilder;
            if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v1();

                event = cloudEventBuilder.withId(sendMessageBatchV2RequestBody.getBizSeqNo())
                        .withSubject(sendMessageBatchV2RequestBody.getTopic())
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
                        .withExtension(SendMessageBatchV2RequestBody.BIZSEQNO, sendMessageBatchV2RequestBody.getBizSeqNo())
                        .withExtension(SendMessageBatchV2RequestBody.PRODUCERGROUP, sendMessageBatchV2RequestBody.getProducerGroup())
                        .withExtension(SendMessageBatchV2RequestBody.TTL, sendMessageBatchV2RequestBody.getTtl())
                        .withExtension(SendMessageBatchV2RequestBody.TAG, sendMessageBatchV2RequestBody.getTag())
                        .build();
            } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v03();
                event = cloudEventBuilder.withId(sendMessageBatchV2RequestBody.getBizSeqNo())
                        .withSubject(sendMessageBatchV2RequestBody.getTopic())
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
                        .withExtension(SendMessageBatchV2RequestBody.BIZSEQNO, sendMessageBatchV2RequestBody.getBizSeqNo())
                        .withExtension(SendMessageBatchV2RequestBody.PRODUCERGROUP, sendMessageBatchV2RequestBody.getProducerGroup())
                        .withExtension(SendMessageBatchV2RequestBody.TTL, sendMessageBatchV2RequestBody.getTtl())
                        .withExtension(SendMessageBatchV2RequestBody.TAG, sendMessageBatchV2RequestBody.getTag())
                        .build();
            }
            return event;
        } catch (Exception e) {
            throw new ProtocolHandleException(e.getMessage(), e.getCause());
        }
    }
}
