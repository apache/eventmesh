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

import io.cloudevents.CloudEvent;
import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.ReaderGroupManager;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.*;
import io.pravega.client.stream.impl.ByteArraySerializer;
import io.pravega.shared.NameUtils;
import io.pravega.shared.security.auth.DefaultCredentials;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.connector.pravega.SubscribeTask;
import org.apache.eventmesh.connector.pravega.config.PravegaConnectorConfig;
import org.apache.eventmesh.connector.pravega.exception.PravegaConnectorException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class PravegaClient {
    private final PravegaConnectorConfig config;
    private final StreamManager streamManager;
    private final EventStreamClientFactory clientFactory;
    private final ReaderGroupManager readerGroupManager;
    private final Map<String, EventStreamWriter<byte[]>> writerMap = new ConcurrentHashMap<>();
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

    protected static PravegaClient getNewInstance(PravegaConnectorConfig config) {
        return new PravegaClient(config);
    }

    public void start() {
        if (PravegaClient.getInstance().createScope()) {
            log.info("Create Pravega scope[{}] success.", PravegaConnectorConfig.getInstance().getScope());
        } else {
            log.info("Pravega scope[{}] has already been created.", PravegaConnectorConfig.getInstance().getScope());
        }
    }

    public void shutdown() {
        subscribeTaskMap.forEach((topic, task) -> task.stopRead());
        subscribeTaskMap.clear();
        readerGroupManager.close();
        clientFactory.close();
        streamManager.close();
    }

    public SendResult publish(String topic, CloudEvent cloudEvent) {
        try (EventStreamWriter<byte[]> writer = writerMap.computeIfAbsent(topic, k -> createWrite(topic))) {
            if (!createStream(topic)) {
                log.debug("stream[{}] has already been created.", topic);
            }
            PravegaCloudEventWriter cloudEventWriter = new PravegaCloudEventWriter(topic);
            PravegaEvent pravegaEvent = cloudEventWriter.writeBinary(cloudEvent);
            writer.writeEvent(PravegaEvent.toByteArray(pravegaEvent)).get(5, TimeUnit.SECONDS);
            SendResult sendResult = new SendResult();
            sendResult.setTopic(topic);
            // set -1 as messageId since writeEvent method doesn't return it.
            sendResult.setMessageId("-1");
            return sendResult;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new PravegaConnectorException(String.format("Write topic[%s] fail.", topic));
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
        deleteReaderGroup(buildReaderGroup(topic, consumerGroup));
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

    private String buildReaderGroup(String topic, String consumerGroup) {
        return String.format("%s-%s", topic, consumerGroup);
    }

    private String buildReaderId(String readerGroup) {
        return String.format("%s-reader", readerGroup);
    }

    private boolean createReaderGroup(String topic, String readerGroup) {
        if (!checkTopicExist(topic)) {
            createStream(topic);
        }
        ReaderGroupConfig readerGroupConfig =
                ReaderGroupConfig.builder().stream(NameUtils.getScopedStreamName(config.getScope(), topic)).build();
        return readerGroupManager.createReaderGroup(readerGroup, readerGroupConfig);
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

    protected Map<String, SubscribeTask> getSubscribeTaskMap() {
        return subscribeTaskMap;
    }
}
