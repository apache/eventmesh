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

package org.apache.eventmesh.api.acl;

import org.apache.eventmesh.api.exception.AclException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class AclServiceTest {

    private static class DemoAclService implements AclService {

        @Override
        public void init() throws AclException {

        }

        @Override
        public void start() throws AclException {

        }

        @Override
        public void shutdown() throws AclException {

        }

        @Override
        public void doAclCheckInConnect(AclProperties aclProperties) throws AclException {

        }

        @Override
        public void doAclCheckInHeartbeat(AclProperties aclProperties) throws AclException {

        }

        @Override
        public void doAclCheckInSend(AclProperties aclProperties) throws AclException {

        }

        @Override
        public void doAclCheckInReceive(AclProperties aclProperties) throws AclException {

        }
    }

    private static AclService service;

    @BeforeAll
    public static void beforeClass() {
        service = new DemoAclService();
    }

    @Test
    public void testInit() {
        Assertions.assertDoesNotThrow(service::init);
    }

    @Test
    public void testStart() {
        Assertions.assertDoesNotThrow(service::start);
    }

    @Test
    public void testShutdown() {
        Assertions.assertDoesNotThrow(service::shutdown);
    }

    @Test
    public void testDoAclCheckInConnect() {
        Assertions.assertDoesNotThrow(() -> service.doAclCheckInConnect(new AclProperties()));
    }

    @Test
    public void testDoAclCheckInHeartbeat() {
        Assertions.assertDoesNotThrow(() -> service.doAclCheckInHeartbeat(new AclProperties()));
    }

    @Test
    public void testDoAclCheckInSend() {
        Assertions.assertDoesNotThrow(() -> service.doAclCheckInSend(new AclProperties()));
    }

    @Test
    public void testDoAclCheckInReceive() {
        Assertions.assertDoesNotThrow(() -> service.doAclCheckInReceive(new AclProperties()));
    }

}
