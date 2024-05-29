package com.apache.eventmesh.admin.server;

public interface ComponentLifeCycle {
    void start() throws Exception;
    void destroy();
}
