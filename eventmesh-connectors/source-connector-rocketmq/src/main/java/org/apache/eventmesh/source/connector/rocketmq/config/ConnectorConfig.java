package org.apache.eventmesh.source.connector.rocketmq.config;

import lombok.Data;

@Data
public class ConnectorConfig {

    private String connectorName;

    private String nameserver;

    private String topic;
}
