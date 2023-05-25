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

package org.apache.eventmesh.storage.pravega.client;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.storage.pravega.config.PravegaStorageConfig;
import org.apache.eventmesh.storage.pravega.exception.PravegaStorageException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    private final PravegaStorageConfig config;
    private final StreamManager streamManager;
    private final EventStreamClientFactory clientFactory;
    private final ReaderGroupManager readerGroupManager;
    private final Map<String, EventStreamWriter<byte[]>> writerMap = new ConcurrentHashMap<>();
    private final Map<String, SubscribeTask> subscribeTaskMap = new ConcurrentHashMap<>();

    private static PravegaClient instance;

    public static PravegaClient getInstance() {
        return instance;
    }

    public static PravegaClient getInstance(PravegaStorageConfig config) {
        if (instance == null) {
            instance = new PravegaClient(config);
        }

        return instance;
    }

    private PravegaClient(PravegaStorageConfig config) {
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

    protected static PravegaClient getNewInstance(PravegaStorageConfig config) {
        return new PravegaClient(config);
    }

    public void start() {
        if (createScope()) {
            log.info("Create Pravega scope[{}] success.", config.getScope());
        } else {
            log.info("Pravega scope[{}] has already been created.", config.getScope());
        }
    }

    public void shutdown() {
        subscribeTaskMap.forEach((topic, task) -> task.stopRead());
        subscribeTaskMap.clear();
        writerMap.forEach((topic, writer) -> writer.close());
        writerMap.clear();
        readerGroupManager.close();
        clientFactory.close();
        streamManager.close();
    }

    /**
     * Publish CloudEvent to Pravega stream named topic. Note that the messageId in SendResult is always -1 since {@link
     * EventStreamWriter#writeEvent(Object)} just return {@link java.util.concurrent.CompletableFuture} with {@link Void} which couldn't get
     * messageId.
     *
     * @param topic      topic
     * @param cloudEvent cloudEvent
     * @return SendResult whose messageId is always -1
     */
    public SendResult publish(String topic, CloudEvent cloudEvent) {
        if (!createStream(topic)) {
            log.debug("stream[{}] has already been created.", topic);
        }
        EventStreamWriter<byte[]> writer = writerMap.computeIfAbsent(topic, k -> createWrite(topic));
        PravegaCloudEventWriter cloudEventWriter = new PravegaCloudEventWriter(topic);
        PravegaEvent pravegaEvent = cloudEventWriter.writeBinary(cloudEvent);
        try {
            writer.writeEvent(PravegaEvent.toByteArray(pravegaEvent)).get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            log.error(String.format("Write topic[%s] fail.", topic), e);
            throw new PravegaStorageException(String.format("Write topic[%s] fail.", topic));
        }
        SendResult sendResult = new SendResult();
        sendResult.setTopic(topic);
        // set -1 as messageId since writeEvent method doesn't return it.
        sendResult.setMessageId("-1");
        return sendResult;

    }

    public boolean subscribe(String topic, boolean isBroadcast, String consumerGroup, String instanceName, EventListener listener) {
        if (subscribeTaskMap.containsKey(topic)) {
            return true;
        }
        String readerGroupName = buildReaderGroupName(isBroadcast, consumerGroup, topic);
        createReaderGroup(topic, readerGroupName);
        String readerId = buildReaderId(instanceName);
        EventStreamReader<byte[]> reader = createReader(readerId, readerGroupName);
        SubscribeTask subscribeTask = new SubscribeTask(topic, reader, listener);
        subscribeTask.start();
        subscribeTaskMap.put(topic, subscribeTask);
        return true;
    }

    public boolean unsubscribe(String topic, boolean isBroadcast, String consumerGroup) {
        if (!subscribeTaskMap.containsKey(topic)) {
            return true;
        }
        if (!isBroadcast) {
            deleteReaderGroup(buildReaderGroupName(false, consumerGroup, topic));
        }
        subscribeTaskMap.remove(topic).stopRead();
        return true;
    }

    public boolean checkTopicExist(String topic) {
        return streamManager.checkStreamExists(config.getScope(), topic);
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

    private String buildReaderGroupName(boolean isBroadcast, String consumerGroup, String topic) {
        if (isBroadcast) {
            return UUID.randomUUID().toString();
        } else {
            return String.format("%s-%s", consumerGroup, topic);
        }
    }

    private String buildReaderId(String instanceName) {
        return String.format("%s-reader", instanceName).replace("\\(", "-").replace("\\)", "-");
    }

    private void createReaderGroup(String topic, String readerGroupName) {
        if (!checkTopicExist(topic)) {
            createStream(topic);
        }
        ReaderGroupConfig readerGroupConfig =
            ReaderGroupConfig.builder()
                .stream(NameUtils.getScopedStreamName(config.getScope(), topic))
                .retentionType(ReaderGroupConfig.StreamDataRetention.AUTOMATIC_RELEASE_AT_LAST_CHECKPOINT)
                .build();
        readerGroupManager.createReaderGroup(readerGroupName, readerGroupConfig);
    }

    private void deleteReaderGroup(String readerGroup) {
        readerGroupManager.deleteReaderGroup(readerGroup);
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

    public Map<String, EventStreamWriter<byte[]>> getWriterMap() {
        return writerMap;
    }

    protected Map<String, SubscribeTask> getSubscribeTaskMap() {
        return subscribeTaskMap;
    }
}
