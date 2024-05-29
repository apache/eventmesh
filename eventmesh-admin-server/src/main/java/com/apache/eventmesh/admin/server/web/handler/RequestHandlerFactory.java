package com.apache.eventmesh.admin.server.web.handler;

import org.apache.eventmesh.common.remote.request.BaseRemoteRequest;
import org.apache.eventmesh.common.remote.response.BaseRemoteResponse;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestHandlerFactory implements ApplicationListener<ContextRefreshedEvent> {

    private final Map<String, BaseRequestHandler<BaseRemoteRequest, BaseRemoteResponse>> handlers =
            new ConcurrentHashMap<>();

    public BaseRequestHandler<BaseRemoteRequest, BaseRemoteResponse> getHandler(String type) {
        return handlers.get(type);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<String, BaseRequestHandler> beans =
                event.getApplicationContext().getBeansOfType(BaseRequestHandler.class);

        for (BaseRequestHandler<BaseRemoteRequest, BaseRemoteResponse> requestHandler : beans.values()) {
            Class<?> clazz = requestHandler.getClass();
            boolean skip = false;
            while (!clazz.getSuperclass().equals(BaseRequestHandler.class)) {
                if (clazz.getSuperclass().equals(Object.class)) {
                    skip = true;
                    break;
                }
                clazz = clazz.getSuperclass();
            }
            if (skip) {
                continue;
            }

            Class tClass = (Class) ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
            handlers.putIfAbsent(tClass.getSimpleName(), requestHandler);
        }
    }
}
