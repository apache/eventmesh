package com.apache.eventmesh.admin.server.web;

import com.apache.eventmesh.admin.server.ComponentLifeCycle;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.remote.payload.PayloadFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Slf4j
public abstract class BaseServer implements ComponentLifeCycle {
    static {
        PayloadFactory.getInstance().init();
    }
    @PostConstruct
    public void init() throws Exception {
        log.info("[{}] server starting at port [{}]", this.getClass().getSimpleName(), getPort());
        start();
        log.info("[{}] server started at port [{}]", this.getClass().getSimpleName(), getPort());
    }

    @PreDestroy
    public void shutdown() {
        log.info("[{}] server will destroy", this.getClass().getSimpleName());
        destroy();
        log.info("[{}] server has be destroy", this.getClass().getSimpleName());
    }

    public abstract int getPort();
}
