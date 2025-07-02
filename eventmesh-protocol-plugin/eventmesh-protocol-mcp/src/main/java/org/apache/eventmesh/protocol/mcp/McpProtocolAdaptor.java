package org.apache.eventmesh.protocol.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import io.cloudevents.CloudEvent;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.mcp.resolver.McpRequestProtocolResolver;
import org.apache.eventmesh.common.protocol.mcp.McpEventWrapper;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.apache.eventmesh.protocol.mcp.McpProtocolConstant.CONSTANTS_KEY_BODY;
import static org.apache.eventmesh.protocol.mcp.McpProtocolConstant.CONSTANTS_KEY_HEADERS;

public class McpProtocolAdaptor<T extends ProtocolTransportObject>
        implements ProtocolAdaptor<ProtocolTransportObject> {

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject protocolTransportObject) throws ProtocolHandleException {
        if (protocolTransportObject instanceof McpEventWrapper) {
            McpEventWrapper wrapper = (McpEventWrapper) protocolTransportObject;
            return McpRequestProtocolResolver.buildEvent(wrapper);
        } else {
            throw new ProtocolHandleException("Unsupported protocol: " + protocolTransportObject.getClass());
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        return Collections.emptyList();  // 可支持批处理扩展
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        McpEventWrapper wrapper = new McpEventWrapper();

        Map<String, Object> sysHeaderMap = new HashMap<>();
        for (String attr : cloudEvent.getAttributeNames()) {
            sysHeaderMap.put(attr, cloudEvent.getAttribute(attr));
        }
        for (String ext : cloudEvent.getExtensionNames()) {
            sysHeaderMap.put(ext, cloudEvent.getExtension(ext));
        }
        wrapper.setSysHeaderMap(sysHeaderMap);

        if (cloudEvent.getData() != null) {
            Map<String, Object> dataContentMap = JsonUtils.parseTypeReferenceObject(
                    new String(Objects.requireNonNull(cloudEvent.getData()).toBytes(), Constants.DEFAULT_CHARSET),
                    new TypeReference<Map<String, Object>>() {}
            );

            String rawHeader = JsonUtils.toJSONString(dataContentMap.get(CONSTANTS_KEY_HEADERS));
            byte[] body = JsonUtils.toJSONString(dataContentMap.get(CONSTANTS_KEY_BODY)).getBytes(StandardCharsets.UTF_8);

            Map<String, Object> headerMap = JsonUtils.parseTypeReferenceObject(
                    rawHeader, new TypeReference<Map<String, Object>>() {}
            );

            wrapper.setHeaderMap(headerMap);
            wrapper.setBody(body);
        }

        return wrapper;
    }

    @Override
    public String getProtocolType() {
        return McpProtocolConstant.PROTOCOL_NAME;
    }
}
