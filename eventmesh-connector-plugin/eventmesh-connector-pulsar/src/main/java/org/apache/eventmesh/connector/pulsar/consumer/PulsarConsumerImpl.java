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

package org.apache.eventmesh.connector.pulsar.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.pulsar.config.ClientConfiguration;

import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventDeserializationException;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PulsarConsumerImpl implements Consumer {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private Properties properties;
    private PulsarClient pulsarClient;
    private org.apache.pulsar.client.api.Consumer<byte[]> consumer;
    private EventListener eventListener;

    @Override
    public void init(Properties properties) throws Exception {
        this.properties = properties;

        final ClientConfiguration clientConfiguration = ClientConfiguration.getInstance();

        try {
            ClientBuilder clientBuilder = PulsarClient.builder()
                .serviceUrl(clientConfiguration.getServiceAddr());

            if (clientConfiguration.getAuthPlugin() != null) {
                Preconditions.checkNotNull(clientConfiguration.getAuthParams(),
                    "Authentication Enabled in pulsar cluster, Please set authParams in pulsar-client.properties");
                clientBuilder.authentication(
                    clientConfiguration.getAuthPlugin(),
                    clientConfiguration.getAuthParams()
                );
            }

            this.pulsarClient = clientBuilder.build();
        } catch (Exception ex) {
            throw new ConnectorRuntimeException(
              String.format("Failed to connect pulsar with exception: %", ex.getMessage()));
        }
    }

    @Override
    public void start() {
        this.started.compareAndSet(false, true);
    }

    @Override
    public void subscribe(String topic) throws Exception {

        if (pulsarClient == null) {
            throw new ConnectorRuntimeException(
                 String.format("Cann't find the pulsar client for topic: %s", topic));
        }

        EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
            @Override
            public void commit(EventMeshAction action) {
                log.debug("message action: {} for topic: {}", action.name(), topic);
            }
        };

        consumer = pulsarClient.newConsumer()
            .topic(topic)
            .subscriptionName(properties.getProperty(Constants.CONSUMER_GROUP))
            .messageListener(
              (MessageListener<byte[]>) (ackConsumer, msg) -> {
                  CloudEvent cloudEvent = EventFormatProvider
                      .getInstance()
                      .resolveFormat(JsonFormat.CONTENT_TYPE)
                      .deserialize(msg.getData());
                  eventListener.consume(cloudEvent, consumeContext);
                  try {
                      ackConsumer.acknowledge(msg);
                  } catch (PulsarClientException ex) {
                      throw new ConnectorRuntimeException(
                        String.format("Failed to unsubscribe the topic:%s with exception: %s", topic, ex.getMessage()));
                  } catch (EventDeserializationException ex) {
                      log.warn("The Message isn't json format, with exception:{}", ex.getMessage());
                  }
              }).subscribe();
    }

    @Override
    public void unsubscribe(String topic) {
        try {
            consumer.unsubscribe();
        } catch (PulsarClientException ex) {
            throw new ConnectorRuntimeException(
              String.format("Failed to unsubscribe the topic:%s with exception: %s", topic, ex.getMessage()));
        }
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
    }

    @Override
    public void registerEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    @Override
    public boolean isStarted() {
        return this.started.get();
    }

    @Override
    public boolean isClosed() {
        return !this.isStarted();
    }

    @Override
    public void shutdown() {
        this.started.compareAndSet(true, false);
        try {
            this.consumer.close();
            this.pulsarClient.close();
        } catch (PulsarClientException ex) {
            throw new ConnectorRuntimeException(
              String.format("Failed to close the pulsar client with exception: %s", ex.getMessage()));
        }
    }
}
