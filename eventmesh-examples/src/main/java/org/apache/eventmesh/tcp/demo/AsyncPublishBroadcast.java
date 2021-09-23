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

package org.apache.eventmesh.tcp.demo;

import java.util.Properties;

import org.apache.eventmesh.client.tcp.EventMeshClient;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.impl.DefaultEventMeshClient;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPublishBroadcast {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishBroadcast.class);

    private static EventMeshClient client;

    public static void main(String[] agrs) throws Exception {
        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final int eventMeshTcpPort = Integer.parseInt(properties.getProperty("eventmesh.tcp.port"));
        try {
            UserAgent userAgent = EventMeshTestUtils.generateClient1();
            client = new DefaultEventMeshClient(eventMeshIp, eventMeshTcpPort, userAgent);
            client.init();
            client.heartbeat();

            Package broadcastMsg = EventMeshTestUtils.broadcastMessage();
            logger.info("begin send broadcast msg============={}", broadcastMsg);
            client.broadcast(broadcastMsg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);

            Thread.sleep(2000);
            // release resource and close client
            // client.close();
        } catch (Exception e) {
            logger.warn("AsyncPublishBroadcast failed", e);
        }
    }
}
