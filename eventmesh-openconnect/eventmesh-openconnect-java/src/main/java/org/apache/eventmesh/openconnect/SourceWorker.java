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

import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.config.OffsetStorageConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetManagementService;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageReader;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageWriter;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.collections4.CollectionUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SourceWorker implements ConnectorWorker {

    private final Source source;
    private final SourceConfig config;

//    private final OffsetStorageWriter offsetStorageWriter;
//
//    private final OffsetStorageReader offsetStorageReader;

    private final OffsetManagementService offsetManagementService;

    private final ExecutorService pollService = Executors.newSingleThreadExecutor();

    private final ExecutorService startService = Executors.newSingleThreadExecutor();

    private final BlockingQueue<ConnectRecord> queue;
    private final EventMeshTCPClient<CloudEvent> eventMeshTCPClient;

    private volatile boolean isRunning = false;

    public SourceWorker(Source source, SourceConfig config) throws Exception {
        this.source = source;
        this.config = config;
        queue = new LinkedBlockingQueue<>(1000);
        eventMeshTCPClient = buildEventMeshPubClient(config);
        eventMeshTCPClient.init();
        // spi load offsetMgmt
        OffsetStorageConfig offsetStorageConfig = config.getOffsetStorageConfig();
        String offsetMgmtPluginType = offsetStorageConfig.getOffsetStorageType();
        offsetManagementService =
            EventMeshExtensionFactory.getExtension(OffsetManagementService.class, offsetMgmtPluginType);
        offsetManagementService.initialize(offsetStorageConfig);
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
        isRunning = true;
        pollService.execute(this::startPoll);

        startService.execute(
            () -> {
                try {
                    startConnector();
                } catch (Exception e) {
                    log.error("source worker[{}] start fail", source.name(), e);
                    throw new RuntimeException(e);
                }
            }
        );
    }

    public void startPoll() {
        while (isRunning) {
            ConnectRecord connectRecord = null;
            try {
                connectRecord = queue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("poll connect record error", e);
            }
            if (connectRecord == null) {
                continue;
            }
            // todo: convert connectRecord to cloudevent
            CloudEvent event = convertRecordToEvent(connectRecord);
            eventMeshTCPClient.publish(event, 3000);
        }
    }

    private void startConnector() throws Exception {
        source.start();
        while (isRunning) {
            List<ConnectRecord> connectorRecordList = source.poll();
            if (CollectionUtils.isEmpty(connectorRecordList)) {
                continue;
            }
            for (ConnectRecord record : connectorRecordList) {
                queue.put(record);
            }
        }
    }

    private CloudEvent convertRecordToEvent(ConnectRecord connectRecord) {

        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSubject(config.getPubSubConfig().getSubject())
            .withSource(URI.create("/"))
            .withDataContentType("application/cloudevents+json")
            .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
            .withData(Objects.requireNonNull(JsonUtils.toJSONString(connectRecord.getData())).getBytes(StandardCharsets.UTF_8))
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
        pollService.shutdown();
        try {
            pollService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("awaitTermination error", e);
        }

        try {
            eventMeshTCPClient.close();
        } catch (Exception e) {
            log.error("event mesh client close error", e);
        }
        log.info("source worker stopped");
    }

}
