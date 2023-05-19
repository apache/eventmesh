package org.apache.eventmesh.sink.connector.rocketmq.config;

import lombok.Data;

@Data
public class ConnectorConfig {

    private String connectorName;

    private String nameServer;

    private String topic;
}
