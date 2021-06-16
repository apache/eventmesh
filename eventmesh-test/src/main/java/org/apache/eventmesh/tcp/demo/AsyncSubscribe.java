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

import io.netty.channel.ChannelHandlerContext;

import org.apache.eventmesh.client.tcp.EventMeshClient;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.impl.DefaultEventMeshClient;
import org.apache.eventmesh.common.protocol.SubcriptionType;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncSubscribe implements ReceiveMsgHook {

    public static Logger logger = LoggerFactory.getLogger(AsyncSubscribe.class);

    private static EventMeshClient client;

    public static AsyncSubscribe handler = new AsyncSubscribe();

    public static void main(String[] agrs) throws Exception {
        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final int eventMeshTcpPort = Integer.parseInt(properties.getProperty("eventmesh.tcp.port"));
        try {
            UserAgent userAgent = EventMeshTestUtils.generateClient2();
            client = new DefaultEventMeshClient(eventMeshIp, eventMeshTcpPort, userAgent);
            client.init();
            client.heartbeat();

            client.subscribe("FT0-e-80010000-01-1", SubscriptionMode.CLUSTERING, SubcriptionType.ASYNC);
            client.registerSubBusiHandler(handler);

            client.listen();

            //client.unsubscribe();

            //退出,销毁资源
//            client.close();
        } catch (Exception e) {
            logger.warn("AsyncSubscribe failed", e);
        }
    }

    @Override
    public void handle(Package msg, ChannelHandlerContext ctx) {
        EventMeshMessage eventMeshMessage = (EventMeshMessage) msg.getBody();
        logger.info("receive async msg====================={}", eventMeshMessage);
    }
}
