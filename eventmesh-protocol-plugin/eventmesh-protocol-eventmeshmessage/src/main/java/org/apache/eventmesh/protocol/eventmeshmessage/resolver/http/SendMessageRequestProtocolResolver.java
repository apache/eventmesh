package org.apache.eventmesh.protocol.eventmeshmessage.resolver.http;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.v03.CloudEventV03;
import io.cloudevents.core.v1.CloudEventV1;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchV2RequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.nio.charset.StandardCharsets;

public class SendMessageRequestProtocolResolver {

    public static CloudEvent buildEvent(Header header, Body body) throws ProtocolHandleException {
        try {
            SendMessageRequestHeader sendMessageRequestHeader = (SendMessageRequestHeader) header;
            SendMessageRequestBody sendMessageRequestBody = (SendMessageRequestBody) body;

            String protocolType = sendMessageRequestHeader.getProtocolType();
            String protocolDesc = sendMessageRequestHeader.getProtocolDesc();
            String protocolVersion = sendMessageRequestHeader.getProtocolVersion();

            String code = sendMessageRequestHeader.getCode();
            String env = sendMessageRequestHeader.getEnv();
            String idc = sendMessageRequestHeader.getIdc();
            String ip = sendMessageRequestHeader.getIp();
            String pid = sendMessageRequestHeader.getPid();
            String sys = sendMessageRequestHeader.getSys();
            String username = sendMessageRequestHeader.getUsername();
            String passwd = sendMessageRequestHeader.getPasswd();
            ProtocolVersion version = sendMessageRequestHeader.getVersion();
            String language = sendMessageRequestHeader.getLanguage();

            String content = sendMessageRequestBody.getContent();

            CloudEvent event = null;
            CloudEventBuilder cloudEventBuilder;
            if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v1();

                event = cloudEventBuilder.withId(sendMessageRequestBody.getBizSeqNo())
                        .withSubject(sendMessageRequestBody.getTopic())
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
                        .withExtension(SendMessageBatchV2RequestBody.BIZSEQNO, sendMessageRequestBody.getBizSeqNo())
                        .withExtension(SendMessageBatchV2RequestBody.PRODUCERGROUP, sendMessageRequestBody.getProducerGroup())
                        .withExtension(SendMessageBatchV2RequestBody.TTL, sendMessageRequestBody.getTtl())
                        .withExtension(SendMessageBatchV2RequestBody.TAG, sendMessageRequestBody.getTag())
                        .build();
            } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
                cloudEventBuilder = CloudEventBuilder.v03();
                event = cloudEventBuilder.withId(sendMessageRequestBody.getBizSeqNo())
                        .withSubject(sendMessageRequestBody.getTopic())
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
                        .withExtension(SendMessageBatchV2RequestBody.BIZSEQNO, sendMessageRequestBody.getBizSeqNo())
                        .withExtension(SendMessageBatchV2RequestBody.PRODUCERGROUP, sendMessageRequestBody.getProducerGroup())
                        .withExtension(SendMessageBatchV2RequestBody.TTL, sendMessageRequestBody.getTtl())
                        .withExtension(SendMessageBatchV2RequestBody.TAG, sendMessageRequestBody.getTag())
                        .build();
            }
            return event;
        } catch (Exception e) {
            throw new ProtocolHandleException(e.getMessage(), e.getCause());
        }
    }
}
