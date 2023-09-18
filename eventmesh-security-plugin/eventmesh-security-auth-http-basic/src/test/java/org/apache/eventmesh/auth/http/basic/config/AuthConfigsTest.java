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

import org.apache.eventmesh.api.auth.AuthService;
import org.apache.eventmesh.auth.http.basic.impl.AuthHttpBasicService;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.junit.Assert;
import org.junit.Test;

public class AuthConfigsTest {

    @Test
    public void getConfigWhenAuthHttpBasicServiceInit() {
        AuthHttpBasicService authService = (AuthHttpBasicService) EventMeshExtensionFactory.getExtension(
                AuthService.class, "auth-http-basic");

        AuthConfigs config = authService.getClientConfiguration();
        assertConfig(config);
    }

    private void assertConfig(AuthConfigs config) {
        Assert.assertEquals(config.getUsername(), "username-success!!!");
        Assert.assertEquals(config.getPassword(), "password-success!!!");
    }
}
