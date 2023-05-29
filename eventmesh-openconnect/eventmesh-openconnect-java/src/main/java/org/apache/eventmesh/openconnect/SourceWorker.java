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
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.SystemUtils;
import org.apache.eventmesh.openconnect.api.config.SourceConfig;
import org.apache.eventmesh.openconnect.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.api.source.Source;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SourceWorker implements ConnectorWorker {

    private final Source source;
    private final SourceConfig config;

    private final EventMeshTCPClient<CloudEvent> eventMeshTCPClient;

    private volatile boolean isRunning = true;

    public SourceWorker(Source source, SourceConfig config) throws Exception {
        this.source = source;
        this.config = config;
        eventMeshTCPClient = buildEventMeshPubClient(config);
        eventMeshTCPClient.init();
    }

    private EventMeshTCPClient<CloudEvent> buildEventMeshPubClient(SourceConfig config) {
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
        UserAgent userAgent = MessageUtils.generatePubClient(agent);

        EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
            .host(meshIp)
            .port(meshPort)
            .userAgent(userAgent)
            .build();
        return EventMeshTCPClientFactory.createEventMeshTCPClient(eventMeshTcpClientConfig, CloudEvent.class);
    }

    @Override
    public void start() {
        log.info("source worker starting {}", source.name());
        log.info("event mesh address is {}", config.getPubSubConfig().getMeshAddress());
        try {
            source.start();
            while (isRunning) {
                List<ConnectRecord> connectorRecordList = source.poll();
                for (ConnectRecord connectRecord : connectorRecordList) {
                    // todo: convert connectRecord to cloudevent
                    CloudEvent event = convertRecordToEvent(connectRecord);
                    eventMeshTCPClient.publish(event, 3000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
        log.info("source worker started");
    }

    private CloudEvent convertRecordToEvent(ConnectRecord connectRecord) {
        final Map<String, String> content = new HashMap<>();
        content.put("content", connectRecord.getData().toString());

        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSubject(config.getPubSubConfig().getSubject())
            .withSource(URI.create("/"))
            .withDataContentType("application/cloudevents+json")
            .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
            .withData(Objects.requireNonNull(JsonUtils.toJSONString(content)).getBytes(StandardCharsets.UTF_8))
            .withExtension("ttl", 10000)
            .build();
    }

    @Override
    public void stop() {
        log.info("source worker stopping");
        isRunning = false;
        try {
            source.stop();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("source destroy error", e);
        }

        try {
            eventMeshTCPClient.close();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("event mesh client close", e);
        }
        log.info("source worker stopped");
    }

}
