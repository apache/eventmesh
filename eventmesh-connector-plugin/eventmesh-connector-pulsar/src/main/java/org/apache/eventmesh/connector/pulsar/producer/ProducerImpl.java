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
import org.apache.eventmesh.connector.pulsar.client.PulsarClientWrapper;
import org.apache.eventmesh.connector.pulsar.config.ClientConfiguration;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProducerImpl extends AbstractProducer {

    private final AtomicBoolean started = new AtomicBoolean(false);

    private ClientConfiguration config;
    private PulsarClientWrapper pulsarClient;

    public ProducerImpl(final Properties properties) {
        super(properties);
        this.config = new ClientConfiguration();
        this.config.init();
    }

    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) {
        this.pulsarClient.publish(cloudEvent, sendCallback);
    }

    public void init(Properties properties) {
        new ProducerImpl(properties);
    }

    public void start() {
        this.started.compareAndSet(false, true);
        this.pulsarClient = new PulsarClientWrapper(config);
    }

    public void shutdown() {
        try {
            this.started.compareAndSet(true, false);
            this.pulsarClient.shutdown();
        } catch (Exception ignored) {
            // ignored
        }
    }

    @Override
    public boolean isStarted() {
        return this.started.get();
    }

    @Override
    public boolean isClosed() {
        return !this.isStarted();
    }
}