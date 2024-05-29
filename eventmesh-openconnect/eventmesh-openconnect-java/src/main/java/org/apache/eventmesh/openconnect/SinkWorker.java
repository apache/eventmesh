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

package org.apache.eventmesh.openconnect;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.config.connector.SinkConfig;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.SystemUtils;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SinkWorker implements ConnectorWorker {

    private final Sink sink;
    private final SinkConfig config;

    private final EventMeshTCPClient<CloudEvent> eventMeshTCPClient;

    public SinkWorker(Sink sink, SinkConfig config) {
        this.sink = sink;
        this.config = config;
        eventMeshTCPClient = buildEventMeshSubClient(config);
    }

    private EventMeshTCPClient<CloudEvent> buildEventMeshSubClient(SinkConfig config) {
        String meshAddress = config.getPubSubConfig().getMeshAddress();
        String meshIp = meshAddress.split(":")[0];
        int meshPort = Integer.parseInt(meshAddress.split(":")[1]);
        UserAgent agent = UserAgent.builder()
            .env(config.getPubSubConfig().getEnv())
            .host("localhost")
            .password(config.getPubSubConfig().getPassWord())
            .username(config.getPubSubConfig().getUserName())
            .group(config.getPubSubConfig().getGroup())
            .path("/")
            .port(8362)
            .subsystem(config.getPubSubConfig().getAppId())
            .pid(Integer.parseInt(SystemUtils.getProcessId()))
            .version("2.0")
            .idc(config.getPubSubConfig().getIdc())
            .build();
        UserAgent userAgent = MessageUtils.generateSubClient(agent);

        EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
            .host(meshIp)
            .port(meshPort)
            .userAgent(userAgent)
            .build();
        return EventMeshTCPClientFactory.createEventMeshTCPClient(eventMeshTcpClientConfig, CloudEvent.class);
    }

    @Override
    public void init() {
        SinkConnectorContext sinkConnectorContext = new SinkConnectorContext();
        sinkConnectorContext.setSinkConfig(config);
        try {
            sink.init(sinkConnectorContext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        eventMeshTCPClient.init();
    }

    @Override
    public void start() {
        log.info("sink worker starting {}", sink.name());
        log.info("event mesh address is {}", config.getPubSubConfig().getMeshAddress());
        try {
            sink.start();
        } catch (Exception e) {
            log.error("sink worker[{}] start fail", sink.name(), e);
            return;
        }
        eventMeshTCPClient.subscribe(config.getPubSubConfig().getSubject(), SubscriptionMode.CLUSTERING,
            SubscriptionType.ASYNC);
        eventMeshTCPClient.registerSubBusiHandler(new EventHandler(sink));
        eventMeshTCPClient.listen();
    }

    @Override
    public void stop() {
        log.info("sink worker stopping");
        try {
            eventMeshTCPClient.unsubscribe();
            eventMeshTCPClient.close();
        } catch (Exception e) {
            log.error("event mesh client close", e);
        }
        try {
            sink.stop();
        } catch (Exception e) {
            log.error("sink destroy error", e);
        }
        log.info("source worker stopped");
    }

    static class EventHandler implements ReceiveMsgHook<CloudEvent> {

        private final Sink sink;

        public EventHandler(Sink sink) {
            this.sink = sink;
        }

        @Override
        public Optional<CloudEvent> handle(CloudEvent event) {
            ConnectRecord connectRecord = CloudEventUtil.convertEventToRecord(event);
            List<ConnectRecord> connectRecords = new ArrayList<>();
            connectRecords.add(connectRecord);
            sink.put(connectRecords);
            return Optional.empty();
        }
    }
}
