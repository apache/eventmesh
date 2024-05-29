package com.apache.eventmesh.admin.server.web.db;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DBThreadPool {
    private final ThreadPoolExecutor executor =
            new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2,
                    Runtime.getRuntime().availableProcessors() * 2, 0L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1000), new EventMeshThreadFactory("admin-server-db"),
                    new ThreadPoolExecutor.DiscardOldestPolicy());
    @PreDestroy
    private void destroy() {
        if (!executor.isShutdown()) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.info("wait heart beat handler thread pool shutdown timeout, it will shutdown immediately");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("wait heart beat handler thread pool shutdown fail");
            }
        }
    }

    public ThreadPoolExecutor getExecutors() {
        return executor;
    }
}
