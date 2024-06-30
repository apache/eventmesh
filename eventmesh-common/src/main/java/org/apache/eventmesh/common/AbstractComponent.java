package org.apache.eventmesh.common;

import lombok.extern.slf4j.Slf4j;

/**
 * @Description:
 */
@Slf4j
public abstract class AbstractComponent implements ComponentLifeCycle {
    @Override
    public void start() throws Exception {
        log.info("component [{}] will start", this.getClass());
        startup();
        log.info("component [{}] has started successfully", this.getClass());
    }

    @Override
    public void stop() throws Exception {
        log.info("component [{}] will stop", this.getClass());
        shutdown();
        log.info("component [{}] has stopped successfully", this.getClass());
    }

    protected abstract void startup() throws Exception;
    protected abstract void shutdown() throws Exception;
}
