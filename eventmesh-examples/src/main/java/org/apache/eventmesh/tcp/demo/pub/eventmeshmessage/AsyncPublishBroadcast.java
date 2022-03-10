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

package org.apache.eventmesh.tcp.demo.pub.eventmeshmessage;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.util.Utils;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPublishBroadcast {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishBroadcast.class);

    public static void main(String[] args) throws Exception {
        Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(ExampleConstants.EVENTMESH_IP);
        final int eventMeshTcpPort = Integer.parseInt(properties.getProperty(ExampleConstants.EVENTMESH_TCP_PORT));
        UserAgent userAgent = EventMeshTestUtils.generateClient1();
        EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
                .host(eventMeshIp)
                .port(eventMeshTcpPort)
                .userAgent(userAgent)
                .build();
        try (final EventMeshTCPClient<EventMeshMessage> client =
                     EventMeshTCPClientFactory.createEventMeshTCPClient(eventMeshTcpClientConfig, EventMeshMessage.class)) {
            client.init();

            EventMeshMessage eventMeshMessage = EventMeshTestUtils.generateBroadcastMqMsg();
            logger.info("begin send broadcast msg: {}", eventMeshMessage);
            client.broadcast(eventMeshMessage, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);

            Thread.sleep(2000);

        } catch (Exception e) {
            logger.warn("AsyncPublishBroadcast failed", e);
        }
    }
}
