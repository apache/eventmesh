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

package org.apache.eventmesh.client.http.producer;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.support.Producer;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.exception.EventMeshException;

import io.cloudevents.CloudEvent;
import io.openmessaging.api.Message;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshHttpProducer implements Producer, AutoCloseable {

    private final EventMeshMessageProducer eventMeshMessageProducerDelegate;
    private final CloudEventProducer cloudEventProducerDelegate;
    private final OpenMessageProducer openMessageProducerDelegate;

    public EventMeshHttpProducer(final EventMeshHttpClientConfig eventMeshHttpClientConfig) {
        this.cloudEventProducerDelegate = new CloudEventProducer(eventMeshHttpClientConfig);
        this.eventMeshMessageProducerDelegate = new EventMeshMessageProducer(eventMeshHttpClientConfig);
        this.openMessageProducerDelegate = new OpenMessageProducer(eventMeshHttpClientConfig);
    }

    @Override
    public <T> void publish(T message) {
        if (message instanceof EventMeshMessage) {
            eventMeshMessageProducerDelegate.publish((EventMeshMessage) message);
        } else if (message instanceof CloudEvent) {
            cloudEventProducerDelegate.publish((CloudEvent) message);
        } else if (message instanceof Message) {
            openMessageProducerDelegate.publish((Message) message);
        } else {
            throw new IllegalArgumentException("Not support message " + message.getClass().getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T request(T message, long timeout) {
        if (message instanceof EventMeshMessage) {
            return (T) eventMeshMessageProducerDelegate.request((EventMeshMessage) message, timeout);
        } else if (message instanceof CloudEvent) {
            return (T) cloudEventProducerDelegate.request((CloudEvent) message, timeout);
        } else if (message instanceof Message) {
            return (T) openMessageProducerDelegate.request((Message) message, timeout);
        } else {
            throw new IllegalArgumentException("Not support message " + message.getClass().getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void request(T message, RRCallback<T> callback, long timeout) {
        if (message instanceof EventMeshMessage) {
            eventMeshMessageProducerDelegate.request((EventMeshMessage) message, (RRCallback<EventMeshMessage>) callback, timeout);
        } else if (message instanceof CloudEvent) {
            cloudEventProducerDelegate.request((CloudEvent) message, (RRCallback<CloudEvent>) callback, timeout);
        } else if (message instanceof Message) {
            openMessageProducerDelegate.request((Message) message, (RRCallback<Message>) callback, timeout);
        } else {
            throw new IllegalArgumentException("Not support message " + message.getClass().getName());
        }
    }

    @Override
    public void close() throws EventMeshException {
        try (final EventMeshMessageProducer ignored = eventMeshMessageProducerDelegate;
            final OpenMessageProducer ignored1 = openMessageProducerDelegate;
            final CloudEventProducer ignored2 = cloudEventProducerDelegate) {
            log.info("Close producer");
        }
    }
}
