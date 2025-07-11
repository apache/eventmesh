package org.apache.eventmesh.connector.mcp.source.data;

import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.eventmesh.connector.mcp.session.MCPSession;

import java.io.Serializable;
import java.util.Map;

/**
 * Mcp Protocol Request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpRequest implements Serializable {
    private static final long serialVersionUID = -483500600756490500L;

    private String protocol;
    private String absoluteURI;
    private Map<String, String> headerMap;
    private boolean isStreaming;
    private Map<String, Object> payloadMap;
    private RoutingContext ctx;
    private String sessionId;
    private String protocolVersion;
    private MCPSession session;

    // 构造器，为了兼容原有代码
    public McpRequest(String protocol, String absoluteURI, Map<String, String> headerMap,
                      boolean isStreaming, Map<String, Object> payloadMap, RoutingContext ctx) {
        this.protocol = protocol;
        this.absoluteURI = absoluteURI;
        this.headerMap = headerMap;
        this.isStreaming = isStreaming;
        this.payloadMap = payloadMap;
        this.ctx = ctx;
    }

    // 为了兼容原有代码，保留这些方法
    public String getProtocolName() {
        return protocol;
    }

    public Object getInputs() {
        return payloadMap != null ? payloadMap.get("inputs") : null;
    }

    public Map<String, Object> getMetadata() {
        return payloadMap != null ? (Map<String, Object>) payloadMap.get("metadata") : null;
    }

    public RoutingContext getRoutingContext() {
        return ctx;
    }
}

