package org.apache.eventmesh.protocol.cloudevents.resolver.tcp;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.cloudevents.CloudEventsProtocolConstant;

public class TcpMessageProtocolResolver {

    public static CloudEvent buildEvent(Header header, String cloudEventJson) throws ProtocolHandleException {
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

        if (!StringUtils.equals(CloudEventsProtocolConstant.PROTOCOL_NAME, protocolType)) {
            throw new ProtocolHandleException(String.format("Unsupported protocolType: %s", protocolType));
        }
        if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)) {
            // todo: transform cloudEventJson to cloudEvent
            cloudEventBuilder = CloudEventBuilder.v1(null);

            for (String propKey : header.getProperties().keySet()) {
                cloudEventBuilder.withExtension(propKey, header.getProperty(propKey).toString());
            }

            return cloudEventBuilder.build();

        } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)) {
            // todo: transform cloudEventJson to cloudEvent
            cloudEventBuilder = CloudEventBuilder.v03(null);

            for (String propKey : header.getProperties().keySet()) {
                cloudEventBuilder.withExtension(propKey, header.getProperty(propKey).toString());
            }

            return cloudEventBuilder.build();
        } else {
            throw new ProtocolHandleException(String.format("Unsupported protocolVersion: %s", protocolVersion));
        }
    }
}
