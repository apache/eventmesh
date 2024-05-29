package org.apache.eventmesh.common.remote.payload;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class PayloadFactory {
    private PayloadFactory(){
    }

    private static class PayloadFactoryHolder {
        private static final PayloadFactory INSTANCE = new PayloadFactory();
    }

    public static PayloadFactory getInstance(){
        return PayloadFactoryHolder.INSTANCE;
    }

    private final Map<String, Class<?>> registryPayload = new ConcurrentHashMap<>();

    private boolean initialized = false;

    public void init() {
        scan();
    }

    private synchronized void scan() {
        if (initialized) {
            return;
        }
        ServiceLoader<IPayload> payloads = ServiceLoader.load(IPayload.class);
        for (IPayload payload : payloads) {
            register(payload.getClass().getSimpleName(), payload.getClass());
        }
        initialized = true;
    }

    public void register(String type, Class<?> clazz) {
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }
        if (registryPayload.containsKey(type)) {
            throw new RuntimeException(String.format("Fail to register, type:%s ,clazz:%s ", type, clazz.getName()));
        }
        registryPayload.put(type, clazz);
    }

    public Class<?> getClassByType(String type) {
        return registryPayload.get(type);
    }
}
