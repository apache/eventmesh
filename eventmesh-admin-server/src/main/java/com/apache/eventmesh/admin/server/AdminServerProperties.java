package com.apache.eventmesh.admin.server;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("event-mesh.admin-server")
@Getter
@Setter
public class AdminServerProperties {
    private int port;
    private boolean enableSSL;
    private String configurationPath;
    private String configurationFile;
    private String serviceName;
}
