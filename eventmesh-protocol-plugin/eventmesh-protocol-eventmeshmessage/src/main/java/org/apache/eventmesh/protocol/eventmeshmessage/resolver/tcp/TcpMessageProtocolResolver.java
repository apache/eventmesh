package org.apache.eventmesh.protocol.eventmeshmessage.resolver.tcp;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.eventmeshmessage.EventMeshMessageProtocolConstant;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TcpMessageProtocolResolver {


    public static CloudEvent buildEvent(Header header, Object body) throws ProtocolHandleException {

        CloudEventBuilder cloudEventBuilder;

        String protocolType = header.getProperty(Constants.PROTOCOL_TYPE).toString();
        String protocolVersion = header.getProperty(Constants.PROTOCOL_VERSION).toString();
        String protocolDesc = header.getProperty(Constants.PROTOCOL_DESC).toString();

        if (StringUtils.isBlank(protocolType)
                || StringUtils.isBlank(protocolVersion)
                || StringUtils.isBlank(protocolDesc)) {
            throw new ProtocolHandleException(String.format("invalid protocol params protocolType %s|protocolVersion %s|protocolDesc %s",
                    protocolType, protocolVersion, protocolDesc));
        }

        if (!StringUtils.equals(EventMeshMessageProtocolConstant.PROTOCOL_NAME, protocolType)) {
            throw new ProtocolHandleException(String.format("Unsupported protocolType: %s", protocolType));
        }

        EventMeshMessage message = (EventMeshMessage) body;

        String topic = message.getTopic();

        String content = message.getBody();

        if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
            cloudEventBuilder = CloudEventBuilder.v1();

        } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
            cloudEventBuilder = CloudEventBuilder.v03();

        } else {
            throw new ProtocolHandleException(String.format("Unsupported protocolVersion: %s", protocolVersion));
        }

        cloudEventBuilder = cloudEventBuilder
                .withId(header.getSeq())
                .withSubject(topic)
                .withData(content.getBytes(StandardCharsets.UTF_8));

        for (String propKey : header.getProperties().keySet()) {
            cloudEventBuilder.withExtension(propKey, header.getProperty(propKey).toString());
        }

        for (String propKey : message.getProperties().keySet()) {
            cloudEventBuilder.withExtension(propKey, message.getProperties().get(propKey));
        }

        return cloudEventBuilder.build();

    }

    public static Package buildEventMeshMessage(CloudEvent cloudEvent) {
        Package pkg = new Package();
        EventMeshMessage eventMeshMessage = new EventMeshMessage();
        eventMeshMessage.setTopic(cloudEvent.getSubject());
        eventMeshMessage.setBody(new String(cloudEvent.getData().toBytes(), StandardCharsets.UTF_8));

        Map<String, String> prop = new HashMap<>();
        for (String extKey : cloudEvent.getExtensionNames()) {
            prop.put(extKey, cloudEvent.getExtension(extKey).toString());
        }
        eventMeshMessage.setProperties(prop);

        pkg.setBody(eventMeshMessage);

        return pkg;
    }
}
