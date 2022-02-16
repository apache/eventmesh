package org.apache.eventmesh.runtime.core.plugin;

import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpAuthWrapper {

    private static final Logger logger = LoggerFactory.getLogger(HttpAuthWrapper.class);

    private static Map<String, AuthService> authServices = new ConcurrentHashMap<>();

    public static AuthService getHttpAuthPlugin(String pluginType) {
        if (authServices.containsKey(pluginType)) {
            return authServices.get(pluginType);
        }

        AuthService authService = EventMeshExtensionFactory.getExtension(AuthService.class, pluginType);

        if (authService == null) {
            logger.error("can't load the authService plugin, please check.");
            throw new RuntimeException("doesn't load the authService plugin, please check.");
        }
        try {
            authService.init();
            authServices.put(pluginType, authService);
            return authService;
        } catch (Exception e) {
            logger.error("Error in initializing authService", e);
        }
        return null;
    }
}
