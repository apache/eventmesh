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

package org.apache.eventmesh.connector.pulsar.producer;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.connector.pulsar.utils.CloudEventUtils;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.protobuf.ProtobufFormat;

public class ProducerImpl extends AbstractProducer {
    private final AtomicBoolean started = new AtomicBoolean(false);
    private PulsarClient pulsarClient;

    public ProducerImpl(final Properties properties) {
        super(properties);
    }

    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) {

        try {
            Producer<byte[]> producer = this.pulsarClient.newProducer()
                    .topic(cloudEvent.getSubject())
                    .batchingMaxPublishDelay(10, TimeUnit.MILLISECONDS)
                    .sendTimeout(10, TimeUnit.SECONDS)
                    .blockIfQueueFull(true)
                    .create();

            byte[] serializedCloudEvent = EventFormatProvider
                    .getInstance()
                    .resolveFormat(ProtobufFormat.PROTO_CONTENT_TYPE)
                    .serialize(cloudEvent);

            producer.sendAsync(serializedCloudEvent).thenAccept(messageId -> {
                sendCallback.onSuccess(CloudEventUtils.convertSendResult(cloudEvent));
            });
        } catch (Exception e) {
            ConnectorRuntimeException onsEx = this.checkProducerException(cloudEvent, e);
            OnExceptionContext context = new OnExceptionContext();
            context.setTopic(cloudEvent.getSubject());
            context.setException(onsEx);
            sendCallback.onException(context);
        }
    }

    public void init(Properties properties) {
        new ProducerImpl(properties);
    }

    public void start() {
        try {
            this.started.compareAndSet(false, true);
            this.pulsarClient = PulsarClient.builder()
                    .serviceUrl(this.properties().get("url").toString())
                    .build();
        } catch (Exception ignored) {
            // ignored
        }
    }

    public void shutdown() {
        try {
            this.started.compareAndSet(true, false);
            this.pulsarClient.close();
        } catch (Exception ignored) {
            // ignored
        }
    }

    public boolean isStarted() {
        return this.started.get();
    }

    public boolean isClosed() {
        return !this.isStarted();
    }
}