package org.apache.eventmesh.connector.mcp.source.data;

import io.vertx.ext.web.RoutingContext;
import lombok.Data;
import org.apache.eventmesh.connector.mcp.session.MCPSession;

import java.util.Map; /**
 * Mcp Streaming Request
 */
@Data
public class McpStreamingRequest extends McpRequest {

    public McpStreamingRequest(String protocol, String absoluteURI, Map<String, String> headerMap,
                               boolean isStreaming, Map<String, Object> payloadMap,
                               RoutingContext ctx, MCPSession session) {
        super(protocol, absoluteURI, headerMap, isStreaming, payloadMap, ctx);
        setSession(session);
    }

    public McpStreamingRequest(String protocol, String absoluteURI, Map<String, String> headerMap,
                                boolean isStreaming, Map<String, Object> payloadMap,
                                RoutingContext ctx, String sessionId, String protocolVersion, MCPSession session) {
        super(protocol, absoluteURI, headerMap, isStreaming, payloadMap, ctx, sessionId, protocolVersion, session);
    }
}
