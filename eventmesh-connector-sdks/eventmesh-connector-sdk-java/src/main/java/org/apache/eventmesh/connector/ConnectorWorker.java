package org.apache.eventmesh.connector;

/**
 * Connector worker interface
 */
public interface ConnectorWorker {

    /**
     * Starts the worker
     */
    void start();

    /**
     * Stops the worker
     */
    void stop();
}
