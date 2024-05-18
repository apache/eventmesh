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

package org.apache.eventmesh.storage.pulsar.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.api.exception.StorageRuntimeException;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.storage.pulsar.config.ClientConfiguration;
import org.apache.eventmesh.storage.pulsar.constant.PulsarConstant;

import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.api.AuthenticationFactory;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionMode;
import org.apache.pulsar.client.api.SubscriptionType;

import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventDeserializationException;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Config(field = "clientConfiguration")
public class PulsarConsumerImpl implements Consumer {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private Properties properties;
    private PulsarClient pulsarClient;
    private EventListener eventListener;

    private final ConcurrentHashMap<String, org.apache.pulsar.client.api.Consumer<byte[]>> consumerMap = new ConcurrentHashMap<>();

    /**
     * Unified configuration class corresponding to pulsar-client.properties
     */
    private ClientConfiguration clientConfiguration;

    @Override
    public void init(Properties properties) throws Exception {
        this.properties = properties;
        String token = properties.getProperty(Constants.CONSUMER_TOKEN);

        try {
            ClientBuilder clientBuilder = PulsarClient.builder()
                .serviceUrl(clientConfiguration.getServiceAddr());

            if (clientConfiguration.getAuthPlugin() != null) {
                Preconditions.checkNotNull(clientConfiguration.getAuthParams(),
                    "Authentication Enabled in pulsar cluster, Please set authParams in pulsar-client.properties");
                clientBuilder.authentication(
                    clientConfiguration.getAuthPlugin(),
                    clientConfiguration.getAuthParams());
            }
            if (StringUtils.isNotBlank(token)) {
                clientBuilder.authentication(
                    AuthenticationFactory.token(token));
            }

            this.pulsarClient = clientBuilder.build();
        } catch (Exception ex) {
            throw new StorageRuntimeException(
                String.format("Failed to connect pulsar with exception: %s", ex.getMessage()));
        }
    }

    @Override
    public void start() {
        this.started.compareAndSet(false, true);
    }

    @Override
    public void subscribe(String topic) throws Exception {
        String subTopic = clientConfiguration.getTopicPrefix() + topic;
        if (pulsarClient == null) {
            throw new StorageRuntimeException(
                String.format("Cann't find the pulsar client for topic: %s", subTopic));
        }

        EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {

            @Override
            public void commit(EventMeshAction action) {
                log.debug("message action: {} for topic: {}", action.name(), subTopic);
            }
        };

        SubscriptionType type = SubscriptionType.Shared;

        String consumerKey = topic + PulsarConstant.KEY_SEPARATOR + properties.getProperty(Constants.CONSUMER_GROUP)
            + PulsarConstant.KEY_SEPARATOR + properties.getProperty(Constants.CLIENT_ADDRESS);
        
        String dlqTopic = subTopic + "-DLQ";
        
        String retryTopic = subTopic + "-RETRY";
        
        org.apache.pulsar.client.api.Consumer<byte[]> consumer = pulsarClient.newConsumer()
            .topic(subTopic)
            .enableRetry(true)
            .deadLetterPolicy(DeadLetterPolicy.builder()
                    .deadLetterTopic(dlqTopic)
                    .retryLetterTopic(retryTopic)
                    .maxRedeliverCount(3)
                    .build())
            .subscriptionName(properties.getProperty(Constants.CONSUMER_GROUP))
            .subscriptionMode(SubscriptionMode.Durable)
            .subscriptionType(type)
            .messageListener(
                (MessageListener<byte[]>) (ackConsumer, msg) -> {
                    EventFormat eventFormat = Objects.requireNonNull(
                        EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE));
                    CloudEvent cloudEvent = eventFormat.deserialize(msg.getData());
                    eventListener.consume(cloudEvent, consumeContext);
                    try {
                        ackConsumer.acknowledge(msg);
                    } catch (PulsarClientException ex) {
                        throw new StorageRuntimeException(
                            String.format("Failed to unsubscribe the topic:%s with exception: %s", subTopic, ex.getMessage()));
                    } catch (EventDeserializationException ex) {
                        log.warn("The Message isn't json format, with exception:{}", ex.getMessage());
                    } catch (Exception e) {
                        ackConsumer.negativeAcknowledge(msg);
                        try {
                            ackConsumer.reconsumeLater(msg, 5, TimeUnit.SECONDS);
                        } catch (PulsarClientException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                })
            .subscribe();

        consumerMap.putIfAbsent(consumerKey, consumer);

    }

    @Override
    public void unsubscribe(String topic) {
        try {
            String consumerKey = topic + PulsarConstant.KEY_SEPARATOR + properties.getProperty(Constants.CONSUMER_GROUP)
                + PulsarConstant.KEY_SEPARATOR + properties.getProperty(Constants.CLIENT_ADDRESS);
            org.apache.pulsar.client.api.Consumer<byte[]> consumer = consumerMap.get(consumerKey);
            consumer.unsubscribe();
            consumerMap.remove(consumerKey);
        } catch (PulsarClientException ex) {
            throw new StorageRuntimeException(
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

            consumerMap.forEach((key, consumer) -> {
                try {
                    consumer.close();
                } catch (PulsarClientException e) {
                    throw new StorageRuntimeException(
                        String.format("Failed to close the pulsar consumer with exception: %s", e.getMessage()));
                }
            });
            this.pulsarClient.close();
            consumerMap.clear();
        } catch (PulsarClientException ex) {
            throw new StorageRuntimeException(
                String.format("Failed to close the pulsar client with exception: %s", ex.getMessage()));
        }
    }

    public ClientConfiguration getClientConfiguration() {
        return this.clientConfiguration;
    }
}
