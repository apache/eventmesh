package org.apache.eventmesh.runtime.connector;

import java.util.Map;

import lombok.Data;

@Data
public class ConnectorRuntimeConfig {

    private String connectorName;

    private String connectorRuntimeInstanceId;

    private String sourceConnectorType;

    private String sourceConnectorDesc;

    private Map<String, Object> sourceConnectorConfig;

    private String sinkConnectorType;

    private String sinkConnectorDesc;

    private Map<String, Object> sinkConnectorConfig;

}
