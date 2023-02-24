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

package org.apache.eventmesh.tcp.demo.pub.cloudevents;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.util.Utils;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncPublish {

    public static void main(String[] args) throws Exception {
        final Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(ExampleConstants.EVENTMESH_IP);
        final int eventMeshTcpPort = Integer.parseInt(properties.getProperty(ExampleConstants.EVENTMESH_TCP_PORT));
        try {
            UserAgent userAgent = EventMeshTestUtils.generateClient1();
            EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
                .host(eventMeshIp)
                .port(eventMeshTcpPort)
                .userAgent(userAgent)
                .build();
            final EventMeshTCPClient<CloudEvent> client =
                EventMeshTCPClientFactory.createEventMeshTCPClient(eventMeshTcpClientConfig, CloudEvent.class);
            client.init();

            for (int i = 0; i < 2; i++) {
                CloudEvent event = EventMeshTestUtils.generateCloudEventV1Async();
                if (log.isInfoEnabled()) {
                    log.info("begin send async msg[{}]: {}", i, event);
                }
                client.publish(event, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);

                ThreadUtils.sleep(1, TimeUnit.SECONDS);
            }
            ThreadUtils.sleep(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("AsyncPublish failed", e);
        }
    }
}
