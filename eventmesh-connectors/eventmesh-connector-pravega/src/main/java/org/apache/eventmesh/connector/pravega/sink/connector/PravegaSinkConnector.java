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

package org.apache.eventmesh.connector.pravega.sink.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.pravega.PravegaSinkConfig;
import org.apache.eventmesh.connector.pravega.client.PravegaCloudEventWriter;
import org.apache.eventmesh.connector.pravega.client.PravegaEvent;
import org.apache.eventmesh.connector.pravega.exception.PravegaConnectorException;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventStreamWriter;
import io.pravega.client.stream.EventWriterConfig;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.stream.impl.ByteArraySerializer;
import io.pravega.shared.security.auth.DefaultCredentials;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PravegaSinkConnector implements Sink {

    private PravegaSinkConfig sinkConfig;
    private StreamManager streamManager;
    private EventStreamClientFactory clientFactory;
    private final Map<String, EventStreamWriter<byte[]>> writerMap = new ConcurrentHashMap<>();

    private static final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public Class<? extends Config> configClass() {
        return PravegaSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (PravegaSinkConfig) sinkConnectorContext.getSinkConfig();

        streamManager = StreamManager.create(sinkConfig.getConnectorConfig().getControllerURI());

        if (!streamManager.checkScopeExists(sinkConfig.getConnectorConfig().getScope())) {
            streamManager.createScope(sinkConfig.getConnectorConfig().getScope());
            log.debug("scope[{}] is just created.", sinkConfig.getConnectorConfig().getScope());
        }

        ClientConfig.ClientConfigBuilder clientConfigBuilder =
            ClientConfig.builder().controllerURI(sinkConfig.getConnectorConfig().getControllerURI());
        if (sinkConfig.getConnectorConfig().isAuthEnabled()) {
            clientConfigBuilder.credentials(
                new DefaultCredentials(
                    sinkConfig.getConnectorConfig().getPassword(),
                    sinkConfig.getConnectorConfig().getUsername()));
        }
        if (sinkConfig.getConnectorConfig().isTlsEnable()) {
            clientConfigBuilder.trustStore(sinkConfig.getConnectorConfig().getTruststore()).validateHostName(false);
        }
        ClientConfig clientConfig = clientConfigBuilder.build();
        clientFactory = EventStreamClientFactory.withScope(sinkConfig.getConnectorConfig().getScope(), clientConfig);
    }

    @Override
    public void start() throws Exception {
        started.compareAndSet(false, true);
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        writerMap.forEach((topic, writer) -> writer.close());
        writerMap.clear();
        clientFactory.close();
        streamManager.close();
        started.compareAndSet(true, false);
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
            CloudEvent cloudEvent = CloudEventUtil.convertRecordToEvent(connectRecord);
            try {
                publish(cloudEvent.getSubject(), cloudEvent);
            } catch (InterruptedException e) {
                Thread currentThread = Thread.currentThread();
                log.warn("[PravegaSinkConnector] Interrupting thread {} due to exception {}", currentThread.getName(), e.getMessage());
                currentThread.interrupt();
            } catch (Exception e) {
                log.error("[PravegaSinkConnector] sendResult has error : ", e);
            }
        }
    }

    private void publish(String topic, CloudEvent cloudEvent) throws Exception {
        if (!createStream(topic)) {
            log.debug("stream[{}] has already been created.", topic);
        }

        try (EventStreamWriter<byte[]> writer = writerMap.computeIfAbsent(topic,
            k -> clientFactory.createEventWriter(topic, new ByteArraySerializer(), EventWriterConfig.builder().build()))) {
            PravegaCloudEventWriter cloudEventWriter = new PravegaCloudEventWriter(topic);
            PravegaEvent pravegaEvent = cloudEventWriter.writeBinary(cloudEvent);
            writer.writeEvent(PravegaEvent.toByteArray(pravegaEvent)).get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            log.error(String.format("Write topic[%s] fail.", topic), e);
            throw new PravegaConnectorException(String.format("Write topic[%s] fail.", topic));
        }
    }

    private boolean createStream(String topic) {
        StreamConfiguration streamConfiguration = StreamConfiguration.builder().build();
        return streamManager.createStream(sinkConfig.getConnectorConfig().getScope(), topic, streamConfiguration);
    }

}
