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

package org.apache.eventmesh.connector.pravega.client;

import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.connector.pravega.config.PravegaConnectorConfig;
import org.apache.eventmesh.connector.pravega.exception.PravegaConnectorException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.ReaderGroupManager;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventRead;
import io.pravega.client.stream.EventStreamReader;
import io.pravega.client.stream.EventStreamWriter;
import io.pravega.client.stream.EventWriterConfig;
import io.pravega.client.stream.ReaderConfig;
import io.pravega.client.stream.ReaderGroupConfig;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.stream.impl.ByteArraySerializer;
import io.pravega.shared.NameUtils;
import io.pravega.shared.security.auth.DefaultCredentials;

public class PravegaClient {
    private final PravegaConnectorConfig config;
    private final StreamManager streamManager;
    private final ClientConfig clientConfig;
    private final EventStreamClientFactory clientFactory;
    private final ReaderGroupManager readerGroupManager;
    private final Map<String, AtomicLong> readerIdMap = new ConcurrentHashMap<>();

    private static PravegaClient instance;

    private PravegaClient() {
        this.config = PravegaConnectorConfig.getInstance();
        this.streamManager = StreamManager.create(config.getControllerURI());
        ClientConfig.ClientConfigBuilder clientConfigBuilder = ClientConfig.builder().controllerURI(config.getControllerURI());
        if (config.isAuthEnabled()) {
            clientConfigBuilder.credentials(new DefaultCredentials(config.getPassword(), config.getUsername()));
        }
        if (config.isTlsEnable()) {
            clientConfigBuilder.trustStore(config.getTruststore()).validateHostName(false);
        }
        clientConfig = clientConfigBuilder.build();
        clientFactory = EventStreamClientFactory.withScope(config.getScope(), clientConfig);
        readerGroupManager = ReaderGroupManager.withScope(config.getScope(), clientConfig);
    }

    public static PravegaClient getInstance() {
        if (instance == null) {
            instance = new PravegaClient();
        }
        return instance;
    }

    public void shutdown() {
        // TODO
    }

    // TODO wrap event
    public SendResult publish(String topic, byte[] event) {
        createStream(topic);
        try (EventStreamWriter<byte[]> writer = createWrite(topic)) {
            final CompletableFuture<Void> writerFuture = writer.writeEvent(event);
            writerFuture.get(5, TimeUnit.SECONDS);
            SendResult sendResult = new SendResult();
            sendResult.setTopic(topic);
            // TODO need to build messageId since writeEvent doesn't return it.
            sendResult.setMessageId("-1");
            return sendResult;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new PravegaConnectorException(String.format("Write [%s] fail", topic));
        }
    }

    public boolean subscribe(String topic, String consumerGroup) {
        String readerGroup = buildReaderGroup(topic, consumerGroup);
        boolean created = createReaderGroup(topic, readerGroup);
        // TODO start reader thread
        return false;
    }

    public boolean unsubscribe(String topic, String consumerGroup) {
        String readerGroup = buildReaderGroup(topic, consumerGroup);
        deleteReaderGroup(readerGroup);
        // TODO stop reader thread
        return true;
    }

    public void consume(String topic, String readerGroup) {
        String readerId = buildReaderId(readerGroup);
        try (EventStreamReader<byte[]> reader = createReader(readerId, readerGroup)) {
            EventRead<byte[]> event;
            while ((event = reader.readNextEvent(2000)) != null) {
                if (event.getEvent() == null) {
                    continue;
                }
                // TODO consume
            }
        }
    }

    public void checkTopicExist(String topic) {
        boolean exist = streamManager.checkStreamExists(config.getScope(), topic);
        if (!exist) {
            throw new PravegaConnectorException(String.format("topic:%s is not exist", topic));
        }
    }

    public boolean createScope() {
        return streamManager.createScope(config.getScope());
    }

    private void createStream(String topic) {
        StreamConfiguration streamConfiguration = StreamConfiguration.builder().build();
        streamManager.createStream(config.getScope(), topic, streamConfiguration);
    }

    private EventStreamWriter<byte[]> createWrite(String topic) {
        return clientFactory.createEventWriter(topic, new ByteArraySerializer(), EventWriterConfig.builder().build());
    }

    private String buildReaderGroup(String topic, String consumerGroup) {
        return String.format("%s-%s", consumerGroup, topic);
    }

    private String buildReaderId(String readerGroup) {
        if (!readerIdMap.containsKey(readerGroup)) {
            return null;
        }
        return String.format("%s-%d", readerGroup, readerIdMap.get(readerGroup).getAndIncrement());
    }

    private boolean createReaderGroup(String topic, String readerGroup) {
        readerIdMap.putIfAbsent(topic, new AtomicLong(0));
        ReaderGroupConfig readerGroupConfig =
            ReaderGroupConfig.builder().stream(NameUtils.getScopedStreamName(config.getScope(), topic)).build();
        return readerGroupManager.createReaderGroup(readerGroup, readerGroupConfig);
    }

    private void deleteReaderGroup(String readerGroup) {
        readerGroupManager.deleteReaderGroup(readerGroup);
        readerIdMap.remove(readerGroup);
    }

    private EventStreamReader<byte[]> createReader(String readerId, String readerGroup) {
        return clientFactory.createReader(readerId, readerGroup, new ByteArraySerializer(), ReaderConfig.builder().build());
    }
}
