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
