package org.apache.eventmesh.connector.mcp.source.data;

import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private String protocolName;

    private String sessionId;

    private Map<String, String> metadata;

    private Boolean stream;

    private Object inputs;

    private RoutingContext routingContext;
}
