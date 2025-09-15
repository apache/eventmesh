package org.apache.eventmesh.connector.mcp.server;

import org.apache.eventmesh.connector.mcp.config.McpServerConfig;
import org.apache.eventmesh.connector.mcp.sink.McpSinkConnector;
import org.apache.eventmesh.connector.mcp.source.McpSourceConnector;
import org.apache.eventmesh.openconnect.Application;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

public class McpConnectServer {
    public static void main(String[] args) throws Exception {
        McpServerConfig serverConfig = ConfigUtil.parse(McpServerConfig.class, "server-config.yml");

        if (serverConfig.isSourceEnable()) {
            Application mcpSourceApp = new Application();
            mcpSourceApp.run(McpSourceConnector.class);
        }

        if (serverConfig.isSinkEnable()) {
            Application mcpSinkApp = new Application();
            mcpSinkApp.run(McpSinkConnector.class);
        }
    }
}
