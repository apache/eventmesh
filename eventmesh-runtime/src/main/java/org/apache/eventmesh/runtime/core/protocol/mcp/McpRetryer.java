package org.apache.eventmesh.runtime.core.protocol.mcp;

import org.apache.eventmesh.retry.api.AbstractRetryer;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.runtime.boot.EventMeshMCPServer;

@Slf4j
public class McpRetryer extends AbstractRetryer {

    private final EventMeshMCPServer eventMeshMCPServer;

    public McpRetryer(EventMeshMCPServer eventMeshMCPServer) {
        this.eventMeshMCPServer = eventMeshMCPServer;
    }
}
