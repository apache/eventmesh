package org.apache.eventmesh.runtime;

public interface RuntimeFactory extends AutoCloseable {

    void init() throws Exception;

    Runtime createRuntime(RuntimeInstanceConfig runtimeInstanceConfig);

}
