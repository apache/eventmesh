package org.apache.eventmesh.runtime.connector;

import org.apache.eventmesh.common.config.Config;

import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config(path = "classPath://connector.yaml")
public class ConnectorRuntimeConfig {

    private String connectorRuntimeInstanceId;

    private String jobID;

    private String sourceConnectorType;

    private String sourceConnectorDesc;

    private Map<String, Object> sourceConnectorConfig;

    private String sinkConnectorType;

    private String sinkConnectorDesc;

    private Map<String, Object> sinkConnectorConfig;

}
