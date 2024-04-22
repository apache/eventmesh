package org.apache.eventmesh.runtime.connector;

import org.apache.eventmesh.runtime.Runtime;
import org.apache.eventmesh.runtime.RuntimeFactory;
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;

public class ConnectorRuntimeFactory implements RuntimeFactory {

    @Override
    public void init() throws Exception {

    }

    @Override
    public Runtime createRuntime(RuntimeInstanceConfig runtimeInstanceConfig) {
        return new ConnectorRuntime(runtimeInstanceConfig);
    }

    @Override
    public void close() throws Exception {

    }
}
