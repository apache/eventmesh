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

import org.apache.eventmesh.api.EventListener;
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

import io.cloudevents.CloudEvent;
import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.ReaderGroupManager;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventStreamReader;
import io.pravega.client.stream.EventStreamWriter;
import io.pravega.client.stream.EventWriterConfig;
import io.pravega.client.stream.ReaderConfig;
import io.pravega.client.stream.ReaderGroupConfig;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.stream.impl.ByteArraySerializer;
import io.pravega.shared.NameUtils;
import io.pravega.shared.security.auth.DefaultCredentials;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PravegaClient {
    private final PravegaConnectorConfig config;
    private final StreamManager streamManager;
    private final EventStreamClientFactory clientFactory;
    private final ReaderGroupManager readerGroupManager;
    private final Map<String, AtomicLong> readerIdMap = new ConcurrentHashMap<>();
    private final Map<String, SubscribeTask> subscribeTaskMap = new ConcurrentHashMap<>();

    private static PravegaClient instance;

    private PravegaClient(PravegaConnectorConfig config) {
        this.config = config;
        streamManager = StreamManager.create(config.getControllerURI());
        ClientConfig.ClientConfigBuilder clientConfigBuilder = ClientConfig.builder().controllerURI(config.getControllerURI());
        if (config.isAuthEnabled()) {
            clientConfigBuilder.credentials(new DefaultCredentials(config.getPassword(), config.getUsername()));
        }
        if (config.isTlsEnable()) {
            clientConfigBuilder.trustStore(config.getTruststore()).validateHostName(false);
        }
        ClientConfig clientConfig = clientConfigBuilder.build();
        clientFactory = EventStreamClientFactory.withScope(config.getScope(), clientConfig);
        readerGroupManager = ReaderGroupManager.withScope(config.getScope(), clientConfig);
    }

    public static PravegaClient getInstance() {
        if (instance == null) {
            instance = new PravegaClient(PravegaConnectorConfig.getInstance());
        }
        return instance;
    }

    protected static PravegaClient setUpAndGetInstance(PravegaConnectorConfig config) {
        if (instance == null) {
            instance = new PravegaClient(config);
        }
        return instance;
    }

    public void start() {
        if (!PravegaClient.getInstance().createScope()) {
            log.info("Pravega scope[{}] has already been created.", PravegaConnectorConfig.getInstance().getScope());
        }
        log.info("Create Pravega scope[{}] success.", PravegaConnectorConfig.getInstance().getScope());
    }

    public void shutdown() {
        subscribeTaskMap.forEach((topic, task) -> task.stopRead());
        subscribeTaskMap.clear();
        readerIdMap.clear();
        readerGroupManager.close();
        clientFactory.close();
        streamManager.close();
    }

    public SendResult publish(String topic, CloudEvent cloudEvent) {
        if (!createStream(topic)) {
            log.debug("stream[{}] has already been created.", topic);
        }
        try (EventStreamWriter<byte[]> writer = createWrite(topic)) {
            PravegaCloudEventWriter cloudEventWriter = new PravegaCloudEventWriter(topic);
            PravegaEvent pravegaEvent = cloudEventWriter.writeBinary(cloudEvent);
            final CompletableFuture<Void> writerFuture = writer.writeEvent(PravegaEvent.toByteArray(pravegaEvent));
            writerFuture.get(5, TimeUnit.SECONDS);
            SendResult sendResult = new SendResult();
            sendResult.setTopic(topic);
            // set -1 as messageId since writeEvent method doesn't return it.
            sendResult.setMessageId("-1");
            return sendResult;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new PravegaConnectorException(String.format("Write topic[%s] fail", topic));
        }
    }

    public boolean subscribe(String topic, String consumerGroup, EventListener listener) {
        if (subscribeTaskMap.containsKey(topic)) {
            return true;
        }
        String readerGroup = buildReaderGroup(topic, consumerGroup);
        if (!createReaderGroup(topic, readerGroup)) {
            log.debug("readerGroup[{}] has already been created.", readerGroup);
        }
        String readerId = buildReaderId(readerGroup);
        EventStreamReader<byte[]> reader = createReader(readerId, readerGroup);
        SubscribeTask subscribeTask = new SubscribeTask(topic, reader, listener);
        subscribeTask.start();
        subscribeTaskMap.put(topic, subscribeTask);
        return true;
    }

    public boolean unsubscribe(String topic, String consumerGroup) {
        if (!subscribeTaskMap.containsKey(topic)) {
            return true;
        }
        deleteReaderGroup(consumerGroup);
        subscribeTaskMap.remove(topic).stopRead();
        return true;
    }

    public void checkTopicExist(String topic) {
        boolean exist = streamManager.checkStreamExists(config.getScope(), topic);
        if (!exist) {
            throw new PravegaConnectorException(String.format("topic:%s is not exist", topic));
        }
    }

    private boolean createScope() {
        return streamManager.createScope(config.getScope());
    }

    private boolean createStream(String topic) {
        StreamConfiguration streamConfiguration = StreamConfiguration.builder().build();
        return streamManager.createStream(config.getScope(), topic, streamConfiguration);
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
        readerIdMap.putIfAbsent(readerGroup, new AtomicLong(0));
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

    protected StreamManager getStreamManager() {
        return streamManager;
    }

    protected EventStreamClientFactory getClientFactory() {
        return clientFactory;
    }

    protected ReaderGroupManager getReaderGroupManager() {
        return readerGroupManager;
    }

    protected Map<String, AtomicLong> getReaderIdMap() {
        return readerIdMap;
    }

    protected Map<String, SubscribeTask> getSubscribeTaskMap() {
        return subscribeTaskMap;
    }
}
