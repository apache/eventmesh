/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.auth.http.basic.impl;

import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.api.exception.AuthException;
import org.apache.eventmesh.auth.http.basic.config.AuthConfigs;
import org.apache.eventmesh.common.config.Config;

import org.apache.commons.lang3.Validate;

import org.apache.commons.lang3.Validate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Config(field = "authConfigs")
public class AuthHttpBasicService implements AuthService {

    /**
     * Unified configuration class corresponding to auth-http-basic.properties
     */
    private AuthConfigs authConfigs;

    @Override
    public void init() throws AuthException {

    }

    @Override
    public void start() throws AuthException {

    }

    @Override
    public void shutdown() throws AuthException {

    }

    @Override
    public Map<String, String> getAuthParams() throws AuthException {
<<<<<<< HEAD
        String password = authConfigs.getPassword();
        String username = authConfigs.getUsername();
        String token = Base64.getEncoder()
                .encodeToString((username + password).getBytes(StandardCharsets.UTF_8));
=======
        if (authConfigs == null) {
            init();
        }

        Validate.notNull(authConfigs);
        String token = Base64.getEncoder().encodeToString((authConfigs.getUsername() + authConfigs.getPassword())
                .getBytes(StandardCharsets.UTF_8));
>>>>>>> fb02d481 ([ISSUE #858] Add test code for this module [eventmesh-security-plugin] (#2502))

        Map<String, String> authParams = new HashMap<>(2);
        authParams.put("Authorization", "Basic " + token);
        return authParams;
    }

    public AuthConfigs getClientConfiguration() {
        return this.authConfigs;
    }
}
