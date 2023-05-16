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

package org.apache.eventmesh.source.connector.rocketmq;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.connector.api.data.ConnectRecord;
import org.apache.eventmesh.source.connector.rocketmq.config.RocketMQSourceConfig;
import org.apache.eventmesh.source.connector.rocketmq.connector.RocketMQSourceConnector;

import java.util.List;

import io.cloudevents.CloudEvent;

public class RocketMQSourceWorker {

    public static final String SOURCE_GROUP = "DEFAULT-CONSUMER-GROUP";
    public static final String SOURCE_CONNECT_NAMESRVADDR = "127.0.0.1:9877";
    public static final String SOURCE_TOPIC = "TopicTest";

    public static final String DESTINATION = "SourceTopic";


    public static void main(String[] args) throws Exception {

        UserAgent userAgent = EventMeshTestUtils.generateClient1();
        EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
            .host("127.0.0.1")
            .port(10002)
            .userAgent(userAgent)
            .build();

        final EventMeshTCPClient<CloudEvent> client =
            EventMeshTCPClientFactory.createEventMeshTCPClient(eventMeshTcpClientConfig, CloudEvent.class);

        client.init();

        final RocketMQSourceConnector rocketMQSourceConnector = new RocketMQSourceConnector();

        rocketMQSourceConfig.setSourceNameserver(SOURCE_CONNECT_NAMESRVADDR);
        rocketMQSourceConfig.setSourceTopic(SOURCE_TOPIC);
        rocketMQSourceConfig.setSourceGroup(SOURCE_GROUP);

        RocketMQSourceConnector rocketMQSourceConnector = new RocketMQSourceConnector();
        rocketMQSourceConnector.init(rocketMQSourceConfig);
        rocketMQSourceConnector.start();

        while (true) {
            List<ConnectRecord> connectorRecordList = rocketMQSourceConnector.poll();
            for (ConnectRecord connectRecord : connectorRecordList) {
                // todo:connectorRecord convert cloudEvents
                CloudEvent event = EventMeshTestUtils.generateCloudEventV1(connectRecord.getExtension("topic"), connectRecord.getData().toString());
                client.publish(event, 3000);
                Thread.sleep(500);
            }
        }

    }

}
