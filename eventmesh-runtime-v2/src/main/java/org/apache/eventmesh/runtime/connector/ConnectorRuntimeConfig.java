package org.apache.eventmesh.runtime.connector;

import lombok.Data;

@Data
public class ConnectorRuntimeConfig {

    private String connectorName;

    private String connectorInstanceId;

    private String connectorType;

    private String connectorDesc;

}
