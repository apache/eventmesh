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

package org.apache.eventmesh.connector.pravega.source.connector;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.pravega.PravegaSourceConfig;
import org.apache.eventmesh.connector.pravega.client.PravegaEvent;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.ReaderGroupManager;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventRead;
import io.pravega.client.stream.EventStreamReader;
import io.pravega.client.stream.ReaderConfig;
import io.pravega.client.stream.ReaderGroupConfig;
import io.pravega.client.stream.impl.ByteArraySerializer;
import io.pravega.shared.NameUtils;
import io.pravega.shared.security.auth.DefaultCredentials;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PravegaSourceConnector implements Source {

    private static final AtomicBoolean started = new AtomicBoolean(false);

    private static final int DEFAULT_BATCH_SIZE = 10;

    private PravegaSourceConfig sourceConfig;

    private StreamManager streamManager;

    private EventStreamClientFactory clientFactory;

    private ReaderGroupManager readerGroupManager;

    private final Map<String, PravegaSourceHandler> sourceHandlerMap = new ConcurrentHashMap<>();

    private BlockingQueue<CloudEvent> queue;

    private final ThreadPoolExecutor executor = ThreadPoolFactory.createThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() * 2,
        Runtime.getRuntime().availableProcessors() * 2,
        "EventMesh-RabbitMQSourceConnector-");

    @Override
    public Class<? extends Config> configClass() {
        return PravegaSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (PravegaSourceConfig) sourceConnectorContext.getSourceConfig();
        this.queue = new LinkedBlockingQueue<>(1000);

        streamManager = StreamManager.create(sourceConfig.getConnectorConfig().getControllerURI());
        ClientConfig.ClientConfigBuilder clientConfigBuilder =
            ClientConfig.builder().controllerURI(sourceConfig.getConnectorConfig().getControllerURI());
        if (sourceConfig.getConnectorConfig().isAuthEnabled()) {
            clientConfigBuilder.credentials(
                new DefaultCredentials(
                    sourceConfig.getConnectorConfig().getPassword(),
                    sourceConfig.getConnectorConfig().getUsername()));
        }
        if (sourceConfig.getConnectorConfig().isTlsEnable()) {
            clientConfigBuilder.trustStore(sourceConfig.getConnectorConfig().getTruststore()).validateHostName(false);
        }
        ClientConfig clientConfig = clientConfigBuilder.build();
        clientFactory = EventStreamClientFactory.withScope(sourceConfig.getConnectorConfig().getScope(), clientConfig);
        readerGroupManager = ReaderGroupManager.withScope(sourceConfig.getConnectorConfig().getScope(), clientConfig);

        initReaders();
    }

    private void initReaders() {
        streamManager.listStreams(sourceConfig.getConnectorConfig().getScope())
            .forEachRemaining(stream -> {
                if (stream.getStreamName().startsWith("_")) {
                    return;
                }
                ReaderGroupConfig readerGroupConfig =
                    ReaderGroupConfig.builder()
                        .stream(NameUtils.getScopedStreamName(sourceConfig.getConnectorConfig().getScope(), stream.getStreamName()))
                        .retentionType(ReaderGroupConfig.StreamDataRetention.AUTOMATIC_RELEASE_AT_LAST_CHECKPOINT)
                        .build();
                readerGroupManager.createReaderGroup(stream.getStreamName(), readerGroupConfig);

                EventStreamReader<byte[]> reader = clientFactory.createReader(
                    "PravegaSourceConnector-reader",
                    stream.getStreamName(),
                    new ByteArraySerializer(),
                    ReaderConfig.builder().build());
                this.sourceHandlerMap.put(stream.getStreamName(), new PravegaSourceHandler(reader));
            });

    }

    @Override
    public void start() throws Exception {
        sourceHandlerMap.forEach((topic, handler) -> executor.execute(handler));
        started.compareAndSet(false, true);
    }

    @Override
    public void commit(ConnectRecord record) {
    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        sourceHandlerMap.forEach((topic, handler) -> {
            readerGroupManager.deleteReaderGroup(topic);
            handler.stop();
        });
        sourceHandlerMap.clear();
        readerGroupManager.close();
        clientFactory.close();
        streamManager.close();
        started.compareAndSet(true, false);
    }

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> connectRecords = new ArrayList<>(DEFAULT_BATCH_SIZE);
        for (int count = 0; count < DEFAULT_BATCH_SIZE; ++count) {
            try {
                CloudEvent event = queue.poll(3, TimeUnit.SECONDS);
                if (event == null) {
                    break;
                }

                connectRecords.add(CloudEventUtil.convertEventToRecord(event));
            } catch (InterruptedException e) {
                break;
            }
        }
        return connectRecords;
    }

    public class PravegaSourceHandler implements Runnable {

        private final EventStreamReader<byte[]> reader;

        private final AtomicBoolean running = new AtomicBoolean(true);

        public PravegaSourceHandler(EventStreamReader<byte[]> reader) {
            this.reader = reader;
        }

        @Override
        public void run() {
            while (running.get()) {
                try {
                    EventRead<byte[]> event = reader.readNextEvent(2000);

                    byte[] eventByteArray = event.getEvent();
                    if (eventByteArray == null) {
                        continue;
                    }
                    PravegaEvent pravegaEvent = PravegaEvent.getFromByteArray(eventByteArray);
                    CloudEvent cloudEvent = pravegaEvent.convertToCloudEvent();
                    queue.add(cloudEvent);
                } catch (Exception ex) {
                    log.error("[PravegaSourceHandler] thread run happen exception.", ex);
                }
            }
        }

        public void stop() {
            running.compareAndSet(true, false);
        }
    }
}
