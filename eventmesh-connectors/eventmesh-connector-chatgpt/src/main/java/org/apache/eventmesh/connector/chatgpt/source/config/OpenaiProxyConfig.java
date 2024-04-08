package org.apache.eventmesh.connector.chatgpt.source.config;

import lombok.Data;

@Data
public class OpenaiProxyConfig {

    private String host;

    private int port;

}
