package org.apache.eventmesh.auth.http.basic.config;

import org.apache.eventmesh.api.common.ConfigurationWrapper;

import java.util.Properties;

public class AuthConfigs {

    public String username;

    public String password;

    private static AuthConfigs instance;

    public static synchronized AuthConfigs getConfigs() {
        if (instance == null) {
            Properties props = ConfigurationWrapper.getConfig("auth-http-basic.properties");
            instance = new AuthConfigs();
            instance.username = props.getProperty("auth.username");
            instance.password = props.getProperty("auth.password");
        }
        return instance;
    }
}
