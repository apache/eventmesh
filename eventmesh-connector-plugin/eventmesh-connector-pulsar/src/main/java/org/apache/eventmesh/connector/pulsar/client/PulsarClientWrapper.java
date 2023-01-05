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

package org.apache.eventmesh.connector.pulsar.client;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.connector.pulsar.config.ClientConfiguration;
import org.apache.eventmesh.connector.pulsar.utils.CloudEventUtils;

import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PulsarClientWrapper {

    private ClientConfiguration config;
    private PulsarClient pulsarClient;
    private Map<String, Producer<byte[]>> producerMap = new HashMap<>();

    public PulsarClientWrapper(ClientConfiguration config)  {
        this.config = config;
        try {
            ClientBuilder clientBuilder = PulsarClient.builder()
                .serviceUrl(config.getServiceAddr());

            if (config.getAuthPlugin() != null) {
                Preconditions.checkNotNull(config.getAuthParams(),
                    "Authentication Enabled in pulsar cluster, Please set authParams in pulsar-client.properties");
                clientBuilder.authentication(
                    config.getAuthPlugin(),
                    config.getAuthParams()
                );
            }

            this.pulsarClient = clientBuilder.build();
        } catch (PulsarClientException ex) {
            throw new ConnectorRuntimeException(
              String.format("Failed to connect pulsar cluster %s with exception: %s", config.getServiceAddr(), ex.getMessage()));
        }
    }

    private Producer<byte[]> createProducer(String topic) {
        try {
            return this.pulsarClient.newProducer()
                .topic(topic)
                .batchingMaxPublishDelay(10, TimeUnit.MILLISECONDS)
                .sendTimeout(10, TimeUnit.SECONDS)
                .blockIfQueueFull(true)
                .create();
        } catch (PulsarClientException ex) {
            throw new ConnectorRuntimeException(
              String.format("Failed to create pulsar producer for %s with exception: %s", topic, ex.getMessage()));
        }
    }

    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) {
        String topic = cloudEvent.getSubject();
        Producer<byte[]> producer = producerMap.computeIfAbsent(topic, k -> createProducer(topic));
        try {
            byte[] serializedCloudEvent = EventFormatProvider
                .getInstance()
                .resolveFormat(JsonFormat.CONTENT_TYPE)
                .serialize(cloudEvent);
            producer.sendAsync(serializedCloudEvent).thenAccept(messageId -> {
                sendCallback.onSuccess(CloudEventUtils.convertSendResult(cloudEvent));
            });
        } catch (Exception ex) {
            log.error("Failed to publish cloudEvent for {} with exception: {}",
                cloudEvent.getSubject(), ex.getMessage());
        }
    }

    public void shutdown() throws PulsarClientException {
        pulsarClient.close();
        for (Map.Entry<String, Producer<byte[]>> producerEntry : producerMap.entrySet()) {
            producerEntry.getValue().close();
        }
        producerMap.clear();
    }

}
