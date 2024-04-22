package org.apache.eventmesh.runtime;

public interface Runtime {

    void init() throws Exception;

    void start() throws Exception;

    void stop() throws Exception;

}
