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

package org.apache.eventmesh.storage.pulsar.producer;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.producer.AbstractProducer;
import org.apache.eventmesh.storage.pulsar.client.PulsarClientWrapper;
import org.apache.eventmesh.storage.pulsar.config.ClientConfiguration;

import java.util.Properties;

import io.cloudevents.CloudEvent;

public class ProducerImpl extends AbstractProducer {

    private ClientConfiguration config;
    private PulsarClientWrapper pulsarClient;

    public ProducerImpl(final Properties properties, ClientConfiguration config) {
        this(properties);
        setConfig(config);
    }

    public ProducerImpl(final Properties properties) {
        super(properties);
    }

    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) {
        this.pulsarClient.publish(cloudEvent, sendCallback);
    }

    public void init(Properties properties) {
        new ProducerImpl(properties);
    }

    public void start() {
        this.started.set(true);
        this.pulsarClient = new PulsarClientWrapper(config, properties);
    }

    public void shutdown() {
        try {
            this.started.set(false);
            this.pulsarClient.shutdown();
        } catch (Exception ignored) {
            // ignored
        }
    }

    public void setConfig(ClientConfiguration config) {
        this.config = config;
    }
}