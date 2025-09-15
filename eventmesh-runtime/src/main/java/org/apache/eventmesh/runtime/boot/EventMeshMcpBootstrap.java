package org.apache.eventmesh.runtime.boot;

import lombok.Getter;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;

import static org.apache.eventmesh.common.Constants.HTTP;

public class EventMeshMcpBootstrap implements EventMeshBootstrap{
    @Getter
    public EventMeshMCPServer eventMeshMCPServer;

    private final EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    private final EventMeshServer eventMeshServer;

    public EventMeshMcpBootstrap(final EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;

        ConfigService configService = ConfigService.getInstance();
        this.eventMeshHttpConfiguration = configService.buildConfigInstance(EventMeshHTTPConfiguration.class);

        ConfigurationContextUtil.putIfAbsent(HTTP, eventMeshHttpConfiguration);
    }

    @Override
    public void init() throws Exception {
        // server init
        if (eventMeshHttpConfiguration != null) {
            eventMeshMCPServer = new EventMeshMCPServer(eventMeshServer, eventMeshHttpConfiguration);
            eventMeshMCPServer.init();
        }
    }

    @Override
    public void start() throws Exception {
        // server start
        if (eventMeshMCPServer != null) {
            eventMeshMCPServer.start();
        }
    }

    @Override
    public void shutdown() throws Exception {
        // server shutdown
        if (eventMeshMCPServer != null) {
            eventMeshMCPServer.shutdown();
        }
    }
}
