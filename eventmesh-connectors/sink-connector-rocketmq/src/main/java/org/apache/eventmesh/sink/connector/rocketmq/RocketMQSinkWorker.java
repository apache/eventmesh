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

package org.apache.eventmesh.sink.connector.rocketmq;

import io.cloudevents.CloudEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.connector.api.data.ConnectRecord;
import org.apache.eventmesh.sink.connector.rocketmq.config.RocketMQSinkConfig;
import org.apache.eventmesh.sink.connector.rocketmq.connector.RocketMQSinkConnector;

public class RocketMQSinkWorker {

    public static final String SINK_GROUP = "DEFAULT-SINK-GROUP";

    public static final String SINK_CONNECT_NAMESRVADDR = "127.0.0.1:9877";

    public static final String SINK_TOPIC = "TopicTest";

    public static RocketMQSinkConnector rocketMQSinkConnector;

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

        client.subscribe(SINK_TOPIC, SubscriptionMode.CLUSTERING,
            SubscriptionType.ASYNC);
        client.registerSubBusiHandler(new MessageHandler());

        client.listen();

        rocketMQSinkConnector = new RocketMQSinkConnector();

        RocketMQSinkConfig rocketMQSinkConfig = new RocketMQSinkConfig();

        rocketMQSinkConfig.setSinkNameserver(SINK_CONNECT_NAMESRVADDR);
        rocketMQSinkConfig.setSinkTopic(SINK_TOPIC);
        rocketMQSinkConfig.setSinkGroup(SINK_GROUP);

        rocketMQSinkConnector.init(rocketMQSinkConfig);

        rocketMQSinkConnector.start();

    }

    public static class MessageHandler implements ReceiveMsgHook<CloudEvent> {

        @Override
        public Optional<CloudEvent> handle(CloudEvent event) {
            byte[] body = Objects.requireNonNull(event.getData()).toBytes();
            //todo: recordPartition & recordOffset
            ConnectRecord connectRecord = new ConnectRecord(null, null, System.currentTimeMillis(), body);
            for(String extensionName : event.getExtensionNames()) {
                connectRecord.addExtension(extensionName, Objects.requireNonNull(event.getExtension(extensionName)).toString());
            }
            connectRecord.addExtension("id", event.getId());
            connectRecord.addExtension("topic", event.getSubject());
            connectRecord.addExtension("source", event.getSource().toString());
            connectRecord.addExtension("type", event.getType());
            List<ConnectRecord> connectRecords = new ArrayList<>();
            connectRecords.add(connectRecord);
            rocketMQSinkConnector.put(connectRecords);
            return Optional.empty();
        }
    }

}
