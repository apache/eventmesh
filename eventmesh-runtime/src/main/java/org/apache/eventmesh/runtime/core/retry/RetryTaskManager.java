package org.apache.eventmesh.runtime.core.retry;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.enums.ProtocolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryTaskManager implements Retryer {

    private final Logger retryLogger = LoggerFactory.getLogger("RetryTaskManager");

    private final CommonConfiguration configuration;

    public RetryTaskManager(CommonConfiguration configuration) {
        this.configuration = configuration;
    }

    private final Queue<RetryContext> failed = new LinkedBlockingDeque<>();

    private ThreadPoolExecutor pool;

    private Thread dispatcher;

    private ProtocolType protocolType;

    @Override
    public void commit(RetryContext retryContext) {

    }

    @Override
    public void init() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void start() {

    }
}
