package org.apache.eventmesh.protocol.cloudevents.resolver.http;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.v03.CloudEventV03;
import io.cloudevents.core.v1.CloudEventV1;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchV2RequestBody;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2RequestHeader;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

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
            if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
                event = JsonUtils.deserialize(content, CloudEventV1.class);
                event = CloudEventBuilder.from(event)
                        .withExtension("code", code)
                        .withExtension("env", env)
                        .withExtension("idc", idc)
                        .withExtension("ip", ip)
                        .withExtension("pid", pid)
                        .withExtension("sys", sys)
                        .withExtension("username", username)
                        .withExtension("passwd", passwd)
                        .withExtension("version", version.getVersion())
                        .withExtension("language", language)
                        .withExtension("protocolType", protocolType)
                        .withExtension("protocolDesc", protocolDesc)
                        .withExtension("protocolVersion", protocolVersion)
                        .build();
            } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
                event = JsonUtils.deserialize(content, CloudEventV03.class);
                event = CloudEventBuilder.from(event)
                        .withExtension("code", code)
                        .withExtension("env", env)
                        .withExtension("idc", idc)
                        .withExtension("ip", ip)
                        .withExtension("pid", pid)
                        .withExtension("sys", sys)
                        .withExtension("username", username)
                        .withExtension("passwd", passwd)
                        .withExtension("version", version.getVersion())
                        .withExtension("language", language)
                        .build();
            }
            return event;
        } catch (Exception e) {
            throw new ProtocolHandleException(e.getMessage(), e.getCause());
        }
    }
}
