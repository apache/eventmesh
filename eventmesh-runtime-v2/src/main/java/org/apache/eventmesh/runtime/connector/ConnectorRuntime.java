package org.apache.eventmesh.runtime.connector;

import org.apache.eventmesh.runtime.Runtime;
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;

public class ConnectorRuntime implements Runtime {

    private RuntimeInstanceConfig runtimeInstanceConfig;

    public ConnectorRuntime(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
    }

}
