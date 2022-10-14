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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.common.config.ConfigurationWrapper;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

public class EventMeshBootstrapTest {

    private ConfigurationWrapper wrapper;

    @Before
    public void before() {
        String file = EventMeshServerTest.class.getResource("/eventmesh.properties").getFile();
        File f = new File(file);
        wrapper = new ConfigurationWrapper(f.getParent(), f.getName(), false);
        System.setProperty("confPath", f.getParent());
    }

    @Test
    public void eventMeshGrpcBootstrapTest() throws Exception {
        EventMeshServer server = new EventMeshServer(wrapper);
        EventMeshGrpcBootstrap bootstrap = new EventMeshGrpcBootstrap(wrapper, server.getRegistry());
        bootstrap.init();
        bootstrap.start();
        bootstrap.shutdown();
    }

    @Test
    public void eventMeshHttpBootstrapTest() throws Exception {
        EventMeshServer server = new EventMeshServer(wrapper);
        EventMeshHttpBootstrap bootstrap = new EventMeshHttpBootstrap(server, wrapper, server.getRegistry());
        bootstrap.init();
        bootstrap.start();
        bootstrap.shutdown();
    }

    @Test
    public void eventMeshTcpBootstrapTest() throws Exception {
        EventMeshServer server = new EventMeshServer(wrapper);
        EventMeshTcpBootstrap bootstrap = new EventMeshTcpBootstrap(server, wrapper, server.getRegistry());
        bootstrap.init();
        bootstrap.start();
        bootstrap.shutdown();
    }

}
