package org.apache.eventmesh.client.tcp.conf;

import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventMeshTcpClientConfig {
    private String    host;
    private int       port;
    private UserAgent userAgent;
}
