package org.apache.eventmesh.auth.http.basic.impl;

import org.apache.eventmesh.api.exception.AuthException;
import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.auth.http.basic.config.AuthConfigs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class AuthHttpBasicService implements AuthService {

    private AuthConfigs authConfigs;

    @Override
    public void init() throws AuthException {
        authConfigs = AuthConfigs.getConfigs();
    }

    @Override
    public void start() throws AuthException {

    }

    @Override
    public void shutdown() throws AuthException {

    }

    @Override
    public Map getAuthParams() throws AuthException {
        if (authConfigs == null) {
            init();
        }

        String token = Base64.getEncoder().encodeToString((authConfigs.username + authConfigs.password)
            .getBytes(StandardCharsets.UTF_8));

        Map authParams = new HashMap();
        authParams.put("Authorization", "Basic " + token);
        return authParams;
    }
}
