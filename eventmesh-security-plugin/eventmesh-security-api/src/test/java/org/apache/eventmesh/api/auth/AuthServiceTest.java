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

package org.apache.eventmesh.api.auth;

import org.apache.eventmesh.api.exception.AuthException;

import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class AuthServiceTest {

    private static class DemoAuthService implements AuthService {

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
            return null;
        }
    }

    private static AuthService service;

    @BeforeClass
    public static void beforeClass() {
        service = new DemoAuthService();
    }

    @Test
    public void testInit() {
        try {
            service.init();
        } catch (AuthException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testStart() {
        try {
            service.start();
        } catch (AuthException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testShutdown() {
        try {
            service.shutdown();
        } catch (AuthException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testGetAuthParams() {
        try {
            Map<String, String> authParams = service.getAuthParams();
            Assert.assertNull(authParams);
        } catch (AuthException e) {
            Assert.fail(e.getMessage());
        }
    }
}
