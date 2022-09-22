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

package org.apache.eventmesh.connector.pravega.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.net.URI;

import org.junit.BeforeClass;
import org.junit.Test;

public class PravegaConnectorConfigTest {

    private static PravegaConnectorConfig config;

    @BeforeClass
    public static void init() {
        config = PravegaConnectorConfig.getInstance();
    }

    @Test
    public void getControllerURI() {
        assertEquals(URI.create("tcp://127.0.0.1:9090"), config.getControllerURI());
    }

    @Test
    public void getScope() {
        assertEquals("eventmesh-pravega", config.getScope());
    }

    @Test
    public void isAuthEnabled() {
        assertFalse(config.isAuthEnabled());
    }

    @Test
    public void getUsername() {
        assertEquals("", config.getUsername());
    }

    @Test
    public void getPassword() {
        assertEquals("", config.getPassword());
    }

    @Test
    public void isTslEnabled() {
        assertFalse(config.isTlsEnable());
    }

    @Test
    public void getTruststore() {
        assertEquals("", config.getTruststore());
    }

    @Test
    public void getClientPoolSize() {
        assertEquals(8, config.getClientPoolSize());
    }

    @Test
    public void getQueueSize() {
        assertEquals(512, config.getQueueSize());
    }
}