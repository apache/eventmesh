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

package org.apache.eventmesh.auth.http.basic.config;

import org.apache.eventmesh.api.common.ConfigurationWrapper;

import java.util.Properties;

public class AuthConfigs {

    private static final String AUTH_CONFIG_FILE_NAME = "auth-http-basic.properties";

    private static final String AUTH_CONFIG_KEY_USERNAME = "auth.username";

    private static final String AUTH_CONFIG_KEY_PASSWORD = "auth.password";

    public String username;

    public String password;

    private static AuthConfigs instance;

    public static synchronized AuthConfigs getConfigs() {
        if (instance == null) {
            Properties props = ConfigurationWrapper.getConfig(AUTH_CONFIG_FILE_NAME);
            instance = new AuthConfigs();
            instance.username = props.getProperty(AUTH_CONFIG_KEY_USERNAME);
            instance.password = props.getProperty(AUTH_CONFIG_KEY_PASSWORD);
        }
        return instance;
    }
}
