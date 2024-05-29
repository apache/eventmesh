package com.apache.eventmesh.admin.server.web.service.position;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.remote.job.DataSourceType;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PositionHandlerFactory implements ApplicationListener<ContextRefreshedEvent> {
    private final Map<DataSourceType, PositionHandler> handlers =
            new ConcurrentHashMap<>();
    public PositionHandler getHandler(DataSourceType type) {
        return handlers.get(type);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<String, PositionHandler> beans =
                event.getApplicationContext().getBeansOfType(PositionHandler.class);

        for (PositionHandler handler: beans.values()) {
            DataSourceType type = handler.getSourceType();
            if (handlers.containsKey(type)) {
                log.warn("data source type [{}] handler already exists", type);
                continue;
            }
            handlers.put(type, handler);
        }
    }
}
