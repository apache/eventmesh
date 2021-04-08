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

package client.common;

import com.webank.eventmesh.runtime.boot.EventMeshServer;
import com.webank.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;

public class Server {

    EventMeshServer server;

    static {
        System.setProperty("proxy.home", "E:\\projects\\external-1\\proxy");
        System.setProperty("confPath", "E:\\projects\\external-1\\proxy\\conf");
        System.setProperty("log4j.configurationFile", "E:\\projects\\external-1\\proxy\\conf\\log4j2.xml");
        System.setProperty("proxy.log.home", "E:\\projects\\external-1\\proxy\\logs");
    }

    public void startAccessServer() throws Exception {
        EventMeshHTTPConfiguration eventMeshHttpConfiguration = new EventMeshHTTPConfiguration(null);
        eventMeshHttpConfiguration.init();
        server = new EventMeshServer(eventMeshHttpConfiguration, null);
        server.init();
        server.start();
    }

    public void shutdownAccessServer() throws Exception {
        server.shutdown();
    }
}
