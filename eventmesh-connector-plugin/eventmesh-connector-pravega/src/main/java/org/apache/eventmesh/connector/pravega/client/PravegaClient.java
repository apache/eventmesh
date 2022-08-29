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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventStreamWriter;
import io.pravega.client.stream.EventWriterConfig;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.stream.impl.ByteArraySerializer;

public class PravegaClient {
    private final PravegaConnectorConfig config;
    private final StreamManager streamManager;
    private final EventStreamClientFactory clientFactory;

    private static PravegaClient instance;

    private PravegaClient() {
        this.config = PravegaConnectorConfig.getInstance();
        this.streamManager = StreamManager.create(config.getControllerURI());
        clientFactory =
            EventStreamClientFactory.withScope(config.getScope(), ClientConfig.builder().controllerURI(config.getControllerURI()).build());
    }

    public static PravegaClient getInstance() {
        if (instance == null) {
            instance = new PravegaClient();
        }
        return instance;
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

    public void checkTopicExist(String topic) {
        boolean exist = streamManager.checkStreamExists(config.getScope(), topic);
        if (!exist) {
            throw new PravegaConnectorException(String.format("topic:%s is not exist", topic));
        }
    }
}
