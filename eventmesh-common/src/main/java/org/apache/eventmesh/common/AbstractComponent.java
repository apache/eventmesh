package org.apache.eventmesh.common;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Description:
 */
@Slf4j
public abstract class AbstractComponent implements ComponentLifeCycle {
    private final AtomicBoolean init = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    @Override
    public void start() throws Exception {
        if (!init.compareAndSet(false, true)){
            log.info("component [{}] has started", this.getClass());
            return;
        }
        log.info("component [{}] will start", this.getClass());
        startup();
        log.info("component [{}] started successfully", this.getClass());
    }

    @Override
    public void stop() throws Exception {
        if (!stopped.compareAndSet(false, true)){
            log.info("component [{}] has stopped", this.getClass());
            return;
        }
        log.info("component [{}] will stop", this.getClass());
        shutdown();
        log.info("component [{}] stopped successfully", this.getClass());
    }

    protected abstract void startup() throws Exception;
    protected abstract void shutdown() throws Exception;
}
