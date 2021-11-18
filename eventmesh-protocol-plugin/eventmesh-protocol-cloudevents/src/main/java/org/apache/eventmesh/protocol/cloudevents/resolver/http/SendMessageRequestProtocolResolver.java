package org.apache.eventmesh.protocol.cloudevents.resolver.http;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.v03.CloudEventV03;
import io.cloudevents.core.v1.CloudEventV1;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

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
